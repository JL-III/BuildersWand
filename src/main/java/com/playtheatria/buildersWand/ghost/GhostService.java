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
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
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

    private final JavaPlugin plugin;
    private final PluginConfig config;
    private final WandItems wandItems;
    private final WaveRunner waveRunner;
    private final GestureListener gestureListener;
    private final Map<UUID, Map<BlockVector, BlockDisplay>> ghosts = new HashMap<>();

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
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    private void update(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (!wandItems.isWand(mainHand) || waveRunner.hasActiveWave(player)) {
            clearFor(player); // no wand, or suppress during the player's own wave
            return;
        }
        Form form = wandItems.getForm(mainHand);
        GestureSession session = gestureListener.sessionOf(player);
        if (session == null) {
            // Un-anchored: form + hint on the action bar, ghost the would-be anchor cell.
            String hint = wandItems.selectedMaterial(player).isEmpty() ? NO_MATERIAL : AIM_HINT;
            player.sendActionBar(Component.text(form.label() + " · " + hint));
            GestureSession preview = new GestureSession();
            preview.form = form;
            syncGhosts(player, LivePlan.derive(player, preview, config, wandItems));
            return;
        }

        Optional<Plan> plan = LivePlan.derive(player, session, config, wandItems);
        if (plan.isEmpty()) {
            player.sendActionBar(Component.text(NO_MATERIAL));
            clearFor(player);
            return;
        }
        syncGhosts(player, plan);
        player.sendActionBar(Component.text(actionBar(player, session, plan.get())));
    }

    // ---------------------------------------------------------------- ghost diff (design §8.3)

    private void syncGhosts(Player player, Optional<Plan> planOpt) {
        if (planOpt.isEmpty()) {
            clearFor(player);
            return;
        }
        Plan plan = planOpt.get();
        Material material = plan.material();
        BlockData data = material.createBlockData();
        Map<BlockVector, BlockDisplay> current = ghosts.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        Set<BlockVector> desired = new HashSet<>();

        for (Location loc : plan.cells()) {
            if (!loc.getBlock().isReplaceable()) {
                continue; // occupied cell is kept at commit — show no ghost (owner feedback)
            }
            BlockVector key = new BlockVector(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            if (!desired.add(key)) {
                continue;
            }
            BlockDisplay existing = current.get(key);
            if (existing == null || !existing.isValid()) {
                current.put(key, spawn(player, loc, data));
            } else if (existing.getBlock().getMaterial() != material) {
                existing.setBlock(data);
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

    private BlockDisplay spawn(Player player, Location cell, BlockData data) {
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
            entity.setGlowColorOverride(config.ghostGlow);
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

    private String actionBar(Player player, GestureSession session, Plan plan) {
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
        String have = player.getGameMode() == GameMode.CREATIVE
                ? "(creative)"
                : "(have " + Feedstock.count(player.getInventory(), plan.material(), wandItems) + ")";
        return session.form.label()
                + " " + dims.primary() + "×" + dims.secondary() + "×" + dims.tertiary()
                + " · " + cells + " cells, " + kept + " kept"
                + " · " + material + " ×" + printable + " " + have
                + " · " + hint(session);
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
