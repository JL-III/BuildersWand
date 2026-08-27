package com.playtheatria.buildersWand.ghost;

import com.playtheatria.buildersWand.config.PluginConfig;
import com.playtheatria.buildersWand.form.Dims;
import com.playtheatria.buildersWand.form.Form;
import com.playtheatria.buildersWand.gesture.GestureListener;
import com.playtheatria.buildersWand.gesture.GestureSession;
import com.playtheatria.buildersWand.gesture.LivePlan;
import com.playtheatria.buildersWand.wand.WandItems;
import com.playtheatria.buildersWand.wand.PlacementUseCost;
import com.playtheatria.buildersWand.wand.UseCounter;
import com.playtheatria.buildersWand.wave.Feedstock;
import com.playtheatria.buildersWand.wave.PlacementRules;
import com.playtheatria.buildersWand.wave.Plan;
import com.playtheatria.buildersWand.wave.WaveRunner;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player display-entity ghost preview (design §8) and the §8.4 action bar. One repeating
 * sync task diffs each wand-holder's plan against their live ghosts, spawning/removing/
 * re-blocking only the difference. Ghosts are owner-only and suppressed during that player's
 * wave. All geometry comes from {@link LivePlan} — no second implementation.
 */
public final class GhostService {

    private static final String NO_MATERIAL = "Hold a material in your off hand.";
    private static final String AIM_HINT = "Aim at a surface; LEFT-CLICK anchors there.";
    private static final Color UNAFFORDABLE = Color.fromRGB(0xFF, 0x2D, 0x2D); // cells you can't afford

    // The idle "how to use" hint shows for PULSE_SHOW of every PULSE_PERIOD ghost ticks, then
    // nothing at all for the rest — so it never holds the action bar hostage: other plugins'
    // messages can take the bar during the (long) gap, and Minecraft fades the hint out on its
    // own. At the default 2-tick update this is ~1.2s shown out of a ~6s cycle.
    private static final int PULSE_PERIOD = 60;
    private static final int PULSE_SHOW = 12;
    private static final TextColor HINT_COLOR = TextColor.color(0x55, 0xFF, 0xFF); // aqua

    private final JavaPlugin plugin;
    private final PluginConfig config;
    private final WandItems wandItems;
    private final WaveRunner waveRunner;
    private final GestureListener gestureListener;
    private final Map<UUID, Map<BlockVector, BlockDisplay>> ghosts = new HashMap<>();
    private int pulseTick;

    public GhostService(JavaPlugin plugin, PluginConfig config, WandItems wandItems,
                        WaveRunner waveRunner, GestureListener gestureListener) {
        this.plugin = plugin;
        this.config = config;
        this.wandItems = wandItems;
        this.waveRunner = waveRunner;
        this.gestureListener = gestureListener;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, config.ghostUpdateTicks, config.ghostUpdateTicks);
    }

    private void tick() {
        pulseTick++;
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    private void update(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (!wandItems.isWand(mainHand) || !player.hasPermission(WandItems.PERMISSION_USE)
                || waveRunner.hasActiveWave(player)) {
            clearFor(player); // no wand, no permission, or suppress during the player's own wave
            return;
        }
        if (mainHand.getAmount() != 1) {
            clearFor(player);
            sendPulsedHint(player, "Builders Wands cannot be used while stacked.");
            return;
        }
        boolean usesBypass = player.hasPermission(WandItems.PERMISSION_USES_BYPASS);
        UseCounter.State wandUses = wandItems.uses(mainHand);
        if (!usesBypass && wandUses.depleted()) {
            clearFor(player);
            sendPulsedHint(player, "Builders Wand has no Uses remaining · /wand restore");
            return;
        }
        Form form = wandItems.getForm(mainHand);
        GestureSession session = gestureListener.sessionOf(player);
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        if (session == null) {
            // Un-anchored: breathe the "how to use" hint (and leave silent gaps for other
            // plugins' action-bar messages); ghost the would-be anchor cell every tick.
            GestureSession preview = new GestureSession();
            preview.form = form;
            Optional<Plan> plan = LivePlan.derive(player, preview, config, wandItems);
            if (waterEvaporates(plan)) {
                sendPulsedHint(player, form.label() + " · Water evaporates in this world.");
                clearFor(player);
                return;
            }
            String hint = wandItems.selectedMaterial(player).isEmpty() ? NO_MATERIAL : AIM_HINT;
            String uses = usesBypass ? "uses ∞" : "uses " + wandUses.remaining()
                    + "/" + wandUses.maximum();
            sendPulsedHint(player, form.label() + " · " + uses + " · " + hint);
            Affordability affordable = affordability(player, plan, creative, usesBypass, wandUses);
            syncGhosts(player, plan, affordable.cells());
            return;
        }

        Optional<Plan> plan = LivePlan.derive(player, session, config, wandItems);
        if (plan.isEmpty()) {
            player.sendActionBar(Component.text(NO_MATERIAL));
            clearFor(player);
            return;
        }
        if (waterEvaporates(plan)) {
            player.sendActionBar(Component.text("Water evaporates in this world.", NamedTextColor.RED));
            clearFor(player);
            return;
        }
        Affordability affordable = affordability(player, plan, creative, usesBypass, wandUses);
        syncGhosts(player, plan, affordable.cells());
        player.sendActionBar(actionBar(session, plan.get(), affordable, creative));
    }

    private static boolean waterEvaporates(Optional<Plan> plan) {
        return plan.isPresent() && plan.get().material().isWater() && plan.get().world().isUltraWarm();
    }

    private record Affordability(int cells, int material, int uses, int usesPerCell,
                                 boolean usesBypass) {
    }

    /** Material and uses affordability stay separate so the action bar names the real limit. */
    private Affordability affordability(Player player, Optional<Plan> plan, boolean creative,
                                        boolean usesBypass, UseCounter.State wandUses) {
        if (plan.isEmpty()) {
            return new Affordability(0, 0, usesBypass ? Integer.MAX_VALUE : wandUses.remaining(),
                    1, usesBypass);
        }
        int material = creative || plan.get().material().reusable()
                ? Integer.MAX_VALUE
                : Feedstock.count(player.getInventory(), plan.get().material().sourceItem(), wandItems);
        int uses = usesBypass ? Integer.MAX_VALUE : wandUses.remaining();
        int usesPerCell = PlacementUseCost.perCell(plan.get().material(), config.waterUsesPerSource);
        int cellsByUses = usesBypass
                ? Integer.MAX_VALUE
                : PlacementUseCost.affordableCells(uses, usesPerCell);
        return new Affordability(Math.min(material, cellsByUses), material, uses, usesPerCell, usesBypass);
    }

    /**
     * Show the idle hint during the show window, and send nothing during the gap — so the hint
     * pulses (Minecraft fades it out on its own) without ever holding the action bar hostage,
     * leaving the gap free for other plugins' messages.
     */
    private void sendPulsedHint(Player player, String text) {
        if (pulseTick % PULSE_PERIOD < PULSE_SHOW) {
            player.sendActionBar(Component.text(text, HINT_COLOR));
        }
    }

    // ---------------------------------------------------------------- ghost diff (design §8.3)

    private void syncGhosts(Player player, Optional<Plan> planOpt, int have) {
        if (planOpt.isEmpty()) {
            clearFor(player);
            return;
        }
        Plan plan = planOpt.get();
        BlockData targetData = plan.blockData();
        BlockData previewData = plan.material().isWater()
                ? plan.material().previewBlock().createBlockData()
                : targetData;
        Map<BlockVector, BlockDisplay> current = ghosts.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        Set<BlockVector> desired = new HashSet<>();

        int printableIndex = 0; // printable cells in emission order — the first `have` are affordable
        for (Location loc : plan.cells()) {
            if (!PlacementRules.isPrintable(loc.getBlock(), targetData)) {
                continue; // occupied cell is kept at commit — show no ghost (owner feedback)
            }
            BlockVector key = new BlockVector(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            if (!desired.add(key)) {
                continue;
            }
            Color glow = printableIndex < have ? config.ghostGlow : UNAFFORDABLE;
            printableIndex++;
            BlockDisplay existing = current.get(key);
            if (existing == null || !existing.isValid()) {
                current.put(key, spawn(player, loc, previewData, glow));
            } else {
                if (!existing.getBlock().getAsString().equals(previewData.getAsString())) {
                    existing.setBlock(previewData); // material or rotation changed
                }
                if (!glow.equals(existing.getGlowColorOverride())) {
                    existing.setGlowColorOverride(glow); // affordability changed
                }
            }
        }
        current.entrySet().removeIf(entry -> {
            if (!desired.contains(entry.getKey())) {
                entry.getValue().remove();
                return true;
            }
            return false;
        });
    }

    private BlockDisplay spawn(Player player, Location cell, BlockData data, Color glow) {
        World world = cell.getWorld();
        Location origin = new Location(world, cell.getBlockX(), cell.getBlockY(), cell.getBlockZ());
        float s = config.ghostScale;
        float t = (1f - s) / 2f;
        BlockDisplay display = world.spawn(origin, BlockDisplay.class, entity -> {
            entity.setBlock(data);
            entity.setTransformation(new Transformation(
                    new Vector3f(t, t, t), new Quaternionf(),
                    new Vector3f(s, s, s), new Quaternionf()));
            entity.setGlowing(true);
            entity.setGlowColorOverride(glow);
            entity.setBrightness(new Display.Brightness(15, 15));
            entity.setTeleportDuration(2);
            entity.setPersistent(false);
            entity.setVisibleByDefault(false);
        });
        player.showEntity(plugin, display);
        return display;
    }

    /** Remove one player's ghosts (the gesture-drop clearer). */
    public void clearFor(Player player) {
        Map<BlockVector, BlockDisplay> current = ghosts.remove(player.getUniqueId());
        if (current != null) {
            current.values().forEach(BlockDisplay::remove);
        }
    }

    /** Remove every tracked ghost (plugin disable). */
    public void clearAll() {
        ghosts.values().forEach(map -> map.values().forEach(BlockDisplay::remove));
        ghosts.clear();
    }

    // ---------------------------------------------------------------- action bar (design §8.4)

    private Component actionBar(GestureSession session, Plan plan, Affordability affordable, boolean creative) {
        Dims dims = plan.dims();
        int cells = plan.cells().size();
        int kept = 0;
        for (Location loc : plan.cells()) {
            if (!PlacementRules.isPrintable(loc.getBlock(), plan.blockData())) {
                kept++;
            }
        }
        int printable = cells - kept;
        String material = WandItems.materialDisplayName(plan.material().placedBlock());

        Component cost;
        if (plan.material().reusable()) {
            cost = Component.text(material + " ×" + printable + " (bucket retained)", NamedTextColor.GREEN);
        } else if (creative) {
            cost = Component.text(material + " ×" + printable + " (creative)", NamedTextColor.GRAY);
        } else if (affordable.material() >= printable) {
            cost = Component.text(material + " ×" + printable + " (have " + affordable.material() + ")", NamedTextColor.GREEN);
        } else {
            // Short: name the shortfall and paint it red to match the red ghost cells.
            cost = Component.text(material + " ×" + printable + " (have " + affordable.material()
                            + ", short " + (printable - affordable.material()) + ")",
                    NamedTextColor.RED);
        }
        Component uses;
        long requiredUses = PlacementUseCost.totalUses(printable, affordable.usesPerCell());
        if (affordable.usesBypass()) {
            uses = Component.text("uses ∞", NamedTextColor.GRAY);
        } else if (affordable.uses() >= requiredUses) {
            String detail = affordable.usesPerCell() == 1
                    ? ""
                    : " (need " + requiredUses + " · " + affordable.usesPerCell() + "/source)";
            uses = Component.text("uses " + affordable.uses() + detail, NamedTextColor.GREEN);
        } else {
            long shortfall = requiredUses - affordable.uses();
            String rate = affordable.usesPerCell() == 1
                    ? ""
                    : " · " + affordable.usesPerCell() + "/source";
            uses = Component.text("uses " + affordable.uses()
                    + " (need " + requiredUses + ", short " + shortfall + rate + ")", NamedTextColor.RED);
        }
        return Component.text(session.form.label(), NamedTextColor.GOLD)
                .append(Component.text(" " + dims.primary() + "×" + dims.secondary() + "×" + dims.tertiary()
                        + " · " + cells + " cells, " + kept + " kept · ", NamedTextColor.GRAY))
                .append(cost)
                .append(Component.text(" · ", NamedTextColor.GRAY))
                .append(uses)
                .append(Component.text(" · ", NamedTextColor.GRAY))
                .append(Component.text(hint(session), NamedTextColor.AQUA));
    }

    private static String hint(GestureSession session) {
        if (session.stage() >= session.form.lockStages()) {
            return "LEFT prints · RIGHT cancels";
        }
        String locks = switch (session.form) {
            case CYLINDER, SPHERE -> "LEFT locks radius";
            case DIAGONAL -> "LEFT locks width";
            case BOX -> session.orientation.wall()
                    ? (session.stage() == 0 ? "LEFT locks width" : "LEFT locks height")
                    : (session.stage() == 0 ? "LEFT locks length" : "LEFT locks width");
        };
        return locks + " · RIGHT cancels";
    }
}
