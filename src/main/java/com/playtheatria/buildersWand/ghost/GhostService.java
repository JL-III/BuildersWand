package com.playtheatria.buildersWand.ghost;

import com.playtheatria.buildersWand.config.PluginConfig;
import com.playtheatria.buildersWand.form.Dims;
import com.playtheatria.buildersWand.form.Form;
import com.playtheatria.buildersWand.gesture.GestureListener;
import com.playtheatria.buildersWand.gesture.GestureSession;
import com.playtheatria.buildersWand.gesture.LivePlan;
import com.playtheatria.buildersWand.wand.WandItems;
import com.playtheatria.buildersWand.wave.Feedstock;
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

    private static final String NO_MATERIAL = "Hold a placeable block in your off hand to choose the material.";
    private static final String AIM_HINT = "Aim at a surface; RIGHT-CLICK anchors there.";
    private static final Color UNAFFORDABLE = Color.fromRGB(0xFF, 0x2D, 0x2D); // cells you can't afford

    // The idle "how to use" hint breathes over PULSE_PERIOD ticks, is drawn for PULSE_SHOW of
    // them, then stays silent for the rest so other plugins' action-bar messages can show.
    private static final int PULSE_PERIOD = 40;
    private static final int PULSE_SHOW = 22;
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
        Form form = wandItems.getForm(mainHand);
        GestureSession session = gestureListener.sessionOf(player);
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        if (session == null) {
            // Un-anchored: breathe the "how to use" hint (and leave silent gaps for other
            // plugins' action-bar messages); ghost the would-be anchor cell every tick.
            String hint = wandItems.selectedMaterial(player).isEmpty() ? NO_MATERIAL : AIM_HINT;
            sendPulsedHint(player, form.label() + " · " + hint);
            GestureSession preview = new GestureSession();
            preview.form = form;
            Optional<Plan> plan = LivePlan.derive(player, preview, config, wandItems);
            syncGhosts(player, plan, affordable(player, plan, creative));
            return;
        }

        Optional<Plan> plan = LivePlan.derive(player, session, config, wandItems);
        if (plan.isEmpty()) {
            player.sendActionBar(Component.text(NO_MATERIAL));
            clearFor(player);
            return;
        }
        int have = affordable(player, plan, creative);
        syncGhosts(player, plan, have);
        player.sendActionBar(actionBar(session, plan.get(), have, creative));
    }

    /** Cells the player can afford of this plan's material: MAX in creative, 0 for no plan. */
    private int affordable(Player player, Optional<Plan> plan, boolean creative) {
        if (plan.isEmpty()) {
            return 0;
        }
        if (creative) {
            return Integer.MAX_VALUE;
        }
        return Feedstock.count(player.getInventory(), plan.get().material(), wandItems);
    }

    /**
     * Send the idle hint with a breathing fade during the show window, and nothing during the
     * silent gap — so the hint pulses and other action-bar messages get a chance to appear.
     */
    private void sendPulsedHint(Player player, String text) {
        int phase = pulseTick % PULSE_PERIOD;
        if (phase >= PULSE_SHOW) {
            return; // silent gap
        }
        float brightness = (float) Math.sin(Math.PI * phase / PULSE_SHOW); // 0 → 1 → 0
        TextColor color = TextColor.color(
                (int) (HINT_COLOR.red() * brightness),
                (int) (HINT_COLOR.green() * brightness),
                (int) (HINT_COLOR.blue() * brightness));
        player.sendActionBar(Component.text(text, color));
    }

    // ---------------------------------------------------------------- ghost diff (design §8.3)

    private void syncGhosts(Player player, Optional<Plan> planOpt, int have) {
        if (planOpt.isEmpty()) {
            clearFor(player);
            return;
        }
        Plan plan = planOpt.get();
        BlockData data = plan.blockData();
        Map<BlockVector, BlockDisplay> current = ghosts.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        Set<BlockVector> desired = new HashSet<>();

        int printableIndex = 0; // printable cells in emission order — the first `have` are affordable
        for (Location loc : plan.cells()) {
            if (!loc.getBlock().isReplaceable()) {
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
                current.put(key, spawn(player, loc, data, glow));
            } else {
                if (!existing.getBlock().getAsString().equals(data.getAsString())) {
                    existing.setBlock(data); // material or rotation changed
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

    private Component actionBar(GestureSession session, Plan plan, int have, boolean creative) {
        Dims dims = plan.dims();
        int cells = plan.cells().size();
        int kept = 0;
        for (Location loc : plan.cells()) {
            if (!loc.getBlock().isReplaceable()) {
                kept++;
            }
        }
        int printable = cells - kept;
        String material = WandItems.materialDisplayName(plan.material());

        Component cost;
        if (creative) {
            cost = Component.text(material + " ×" + printable + " (creative)", NamedTextColor.GRAY);
        } else if (have >= printable) {
            cost = Component.text(material + " ×" + printable + " (have " + have + ")", NamedTextColor.GREEN);
        } else {
            // Short: name the shortfall and paint it red to match the red ghost cells.
            cost = Component.text(material + " ×" + printable + " (have " + have + ", short " + (printable - have) + ")",
                    NamedTextColor.RED);
        }
        return Component.text(session.form.label(), NamedTextColor.GOLD)
                .append(Component.text(" " + dims.primary() + "×" + dims.secondary() + "×" + dims.tertiary()
                        + " · " + cells + " cells, " + kept + " kept · ", NamedTextColor.GRAY))
                .append(cost)
                .append(Component.text(" · ", NamedTextColor.GRAY))
                .append(Component.text(hint(session), NamedTextColor.AQUA));
    }

    private static String hint(GestureSession session) {
        if (session.stage() >= session.form.lockStages()) {
            return "RIGHT prints · LEFT cancels";
        }
        String locks = switch (session.form) {
            case CYLINDER, SPHERE -> "RIGHT locks radius";
            case DIAGONAL -> "RIGHT locks width";
            case BOX -> session.orientation.wall()
                    ? (session.stage() == 0 ? "RIGHT locks width" : "RIGHT locks height")
                    : (session.stage() == 0 ? "RIGHT locks length" : "RIGHT locks width");
        };
        return locks + " · LEFT cancels";
    }
}
