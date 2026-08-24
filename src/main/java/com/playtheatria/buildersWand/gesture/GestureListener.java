package com.playtheatria.buildersWand.gesture;

import com.playtheatria.buildersWand.config.PluginConfig;
import com.playtheatria.buildersWand.form.Form;
import com.playtheatria.buildersWand.form.Orientation;
import com.playtheatria.buildersWand.wand.WandItems;
import com.playtheatria.buildersWand.wave.Plan;
import com.playtheatria.buildersWand.wave.StopReason;
import com.playtheatria.buildersWand.wave.WaveRunner;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BlockVector;
import org.bukkit.util.RayTraceResult;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Routes wand clicks into the gesture machine (design §7.2) and drops the gesture on the
 * events of §7.2. Every {@link PlayerInteractEvent} handler is guarded by
 * {@code getHand() == EquipmentSlot.HAND} to defeat the double-fire.
 */
public final class GestureListener implements Listener {

    private static final String AIM_HINT = "Aim at a surface; RIGHT-CLICK anchors there.";

    private final WandItems wandItems;
    private final PluginConfig config;
    private final WaveRunner waveRunner;
    private Consumer<Player> ghostClearer = player -> { };
    private final Map<UUID, GestureSession> sessions = new HashMap<>();

    public GestureListener(WandItems wandItems, PluginConfig config, WaveRunner waveRunner) {
        this.wandItems = wandItems;
        this.config = config;
        this.waveRunner = waveRunner;
    }

    /** Wire ghost removal on gesture drops (set once the ghost service exists). */
    public void setGhostClearer(Consumer<Player> ghostClearer) {
        this.ghostClearer = ghostClearer;
    }

    /** The player's live session (or null) — read by the ghost service. */
    public GestureSession sessionOf(Player player) {
        return sessions.get(player.getUniqueId());
    }

    /** Drop the player's gesture: anchor, locks, and ghost. */
    public void clearSession(Player player) {
        sessions.remove(player.getUniqueId());
        ghostClearer.accept(player);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // mandatory guard against the double-fire
        }
        ItemStack item = event.getItem();
        if (!wandItems.isWand(item)) {
            return;
        }
        Player player = event.getPlayer();
        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            if (sessions.containsKey(player.getUniqueId())) {
                event.setCancelled(true);
                clearSession(player);
            }
            return;
        }
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            handleRightClick(player, item);
        }
    }

    private void handleRightClick(Player player, ItemStack wand) {
        GestureSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            Form form = wandItems.getForm(wand);
            if (form == Form.SINGLE) {
                printSingle(player);
            } else {
                tryAnchor(player, form);
            }
            return;
        }
        if (session.stage() < session.form.lockStages()) {
            LivePlan.lockValue(player, session, config).ifPresent(value -> {
                if (session.lock1 == null) {
                    session.lock1 = value;
                } else {
                    session.lock2 = value;
                }
            });
            return;
        }
        Optional<Plan> plan = LivePlan.derive(player, session, config, wandItems);
        if (plan.isEmpty()) {
            player.sendMessage(red("Hold a placeable block in your off hand to choose the material."));
            return;
        }
        if (waveRunner.commit(player, plan.get())) {
            clearSession(player); // gesture clears only after a successful commit
        }
    }

    private void printSingle(Player player) {
        Optional<Plan> plan = LivePlan.single(player, config, wandItems);
        if (plan.isPresent()) {
            waveRunner.commit(player, plan.get());
            return;
        }
        if (LivePlan.wouldBeAnchor(player, config).isEmpty()) {
            player.sendActionBar(Component.text(AIM_HINT));
        } else {
            player.sendMessage(red("Hold a placeable block in your off hand to choose the material."));
        }
    }

    private void tryAnchor(Player player, Form form) {
        RayTraceResult hit = player.rayTraceBlocks(config.anchorReach, FluidCollisionMode.NEVER);
        if (hit == null || hit.getHitBlock() == null || hit.getHitBlockFace() == null) {
            player.sendActionBar(Component.text(AIM_HINT));
            return;
        }
        if (wandItems.selectedMaterial(player).isEmpty()) {
            player.sendMessage(red("Hold a placeable block in your off hand to choose the material."));
            return;
        }
        Block block = hit.getHitBlock();
        BlockFace face = hit.getHitBlockFace();
        GestureSession session = new GestureSession();
        session.form = form;
        session.worldId = block.getWorld().getUID();
        session.anchor = new BlockVector(
                block.getX() + face.getModX(),
                block.getY() + face.getModY(),
                block.getZ() + face.getModZ());
        session.orientation = Orientation.fromClick(face, player.getLocation().getYaw());
        sessions.put(player.getUniqueId(), session);
    }

    @EventHandler
    public void onHeldChange(PlayerItemHeldEvent event) {
        ItemStack newItem = event.getPlayer().getInventory().getItem(event.getNewSlot());
        if (!wandItems.isWand(newItem)) {
            clearSession(event.getPlayer());
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (wandItems.isWand(event.getItemDrop().getItemStack())) {
            clearSession(event.getPlayer());
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        clearSession(event.getEntity());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        clearSession(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        clearSession(player);
        waveRunner.stopFor(player, StopReason.PLAYER_QUIT);
    }

    private static Component red(String text) {
        return Component.text(text, NamedTextColor.RED);
    }
}
