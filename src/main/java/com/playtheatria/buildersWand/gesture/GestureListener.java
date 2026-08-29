package com.playtheatria.buildersWand.gesture;

import com.playtheatria.buildersWand.config.PluginConfig;
import com.playtheatria.buildersWand.form.Form;
import com.playtheatria.buildersWand.form.Orientation;
import com.playtheatria.buildersWand.prefab.PrefabPlacementController;
import com.playtheatria.buildersWand.wand.BlockOrientation;
import com.playtheatria.buildersWand.wand.WandItems;
import com.playtheatria.buildersWand.wave.Plan;
import com.playtheatria.buildersWand.wave.StopReason;
import com.playtheatria.buildersWand.wave.WaveRunner;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
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
 * events of §7.2. While the wand is in the offhand, both hand interactions are cancelled and the
 * first packet for each click is routed as the authoritative control event. Matching opposite-hand
 * packets are de-duplicated so they cannot execute the same control twice.
 */
public final class GestureListener implements Listener {

    private static final String AIM_HINT = "Aim at a surface; RIGHT-CLICK anchors there.";
    private static final String ENTITY_AIM_HINT = "Aim away from entities to control the Builders Wand.";

    private final WandItems wandItems;
    private final PluginConfig config;
    private final WaveRunner waveRunner;
    private Consumer<Player> ghostClearer = player -> { };
    private PrefabPlacementController prefabPlacement;
    private final Map<UUID, GestureSession> sessions = new HashMap<>();
    private final Map<UUID, HandledControl> lastLeftControls = new HashMap<>();
    private final Map<UUID, HandledControl> lastRightControls = new HashMap<>();

    private enum ControlFamily {
        AIR,
        BLOCK,
        ENTITY
    }

    private record ControlFingerprint(ControlFamily family, UUID identity,
                                      int x, int y, int z, BlockFace face) {

        static ControlFingerprint air(Player player) {
            return new ControlFingerprint(ControlFamily.AIR, player.getWorld().getUID(),
                    0, 0, 0, null);
        }

        static ControlFingerprint block(Player player, Block block, BlockFace face) {
            if (block == null) {
                return air(player);
            }
            return new ControlFingerprint(ControlFamily.BLOCK, block.getWorld().getUID(),
                    block.getX(), block.getY(), block.getZ(), face);
        }

        static ControlFingerprint entity(UUID entityId) {
            return new ControlFingerprint(ControlFamily.ENTITY, entityId, 0, 0, 0, null);
        }
    }

    private record HandledControl(int tick, EquipmentSlot hand, ControlFingerprint fingerprint) {
    }

    public GestureListener(WandItems wandItems, PluginConfig config, WaveRunner waveRunner) {
        this.wandItems = wandItems;
        this.config = config;
        this.waveRunner = waveRunner;
    }

    /** Wire ghost removal on gesture drops (set once the ghost service exists). */
    public void setGhostClearer(Consumer<Player> ghostClearer) {
        this.ghostClearer = ghostClearer;
    }

    public void setPrefabPlacement(PrefabPlacementController prefabPlacement) {
        this.prefabPlacement = prefabPlacement;
    }

    /** The player's live session (or null) — read by the ghost service. */
    public GestureSession sessionOf(Player player) {
        return sessions.get(player.getUniqueId());
    }

    /** Apply live wand preferences without discarding the player's carefully chosen anchor. */
    public void refreshPreferences(Player player, ItemStack wand) {
        GestureSession session = sessions.get(player.getUniqueId());
        if (session == null || session.form != wandItems.getForm(wand)) {
            return;
        }
        var density = session.form.supportsDensity()
                ? wandItems.getDensity(wand) : com.playtheatria.buildersWand.form.Density.SHELL;
        var restriction = wandItems.getSurfaceRestriction(wand);
        if (session.density == density && session.surfaceRestriction == restriction) {
            return;
        }
        boolean restrictionChanged = session.surfaceRestriction != restriction;
        session.density = density;
        session.surfaceRestriction = restriction;
        if (restrictionChanged) {
            session.surfaceSources = null;
        }
    }

    /** Drop the player's gesture: anchor, locks, and ghost. */
    public void clearSession(Player player) {
        sessions.remove(player.getUniqueId());
        ghostClearer.accept(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        boolean canUse = player.hasPermission(WandItems.PERMISSION_USE);
        ItemStack wand = player.getInventory().getItemInOffHand();
        if (!canUse || !wandItems.isWand(wand)) {
            return; // no permission or no offhand wand: vanilla interaction remains untouched
        }
        if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) {
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true);
        Action action = event.getAction();
        boolean leftClick = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        ControlFingerprint fingerprint = action == Action.LEFT_CLICK_BLOCK
                || action == Action.RIGHT_CLICK_BLOCK
                ? ControlFingerprint.block(player, event.getClickedBlock(), event.getBlockFace())
                : ControlFingerprint.air(player);
        if ((!leftClick && !rightClick)
                || !claimControl(player, event.getHand(), rightClick, fingerprint)) {
            return;
        }
        if (wand.getAmount() != 1) {
            clearSession(player);
            player.sendMessage(red("Builders Wands cannot be used while stacked. Separate them first."));
            return;
        }
        wandItems.ensureFirstWielder(wand, player);
        player.getInventory().setItemInOffHand(wand);
        if (prefabPlacement != null && prefabPlacement.active(player)) {
            if (rightClick) {
                if (player.isSneaking()) {
                    prefabPlacement.exitToForms(player);
                    cycleForm(player, wand);
                } else {
                    prefabPlacement.rightClick(player);
                }
            } else if (leftClick) {
                prefabPlacement.leftClick(player, player.isSneaking());
            }
            return;
        }
        if (rightClick) {
            if (player.isSneaking()) {
                cycleForm(player, wand);
            } else {
                advanceGesture(player, wand);
            }
            return;
        }
        if (leftClick) {
            handleLeftClick(player, wand);
        }
    }

    /**
     * Entity interactions do not emit {@link PlayerInteractEvent}. Cancel both hand variants so a
     * hotbar sample cannot be given to an allay, inserted into a frame, or equipped onto a stand;
     * route only the first packet into the wand's right-click control.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        ItemStack wand = player.getInventory().getItemInOffHand();
        if (!player.hasPermission(WandItems.PERMISSION_USE) || !wandItems.isWand(wand)) {
            return;
        }
        event.setCancelled(true);
        if (!claimControl(player, event.getHand(), true,
                ControlFingerprint.entity(event.getRightClicked().getUniqueId()))) {
            return;
        }
        if (wand.getAmount() != 1) {
            clearSession(player);
            player.sendMessage(red("Builders Wands cannot be used while stacked. Separate them first."));
            return;
        }
        wandItems.ensureFirstWielder(wand, player);
        player.getInventory().setItemInOffHand(wand);
        if (prefabPlacement != null && prefabPlacement.active(player)) {
            if (player.isSneaking()) {
                prefabPlacement.exitToForms(player);
                cycleForm(player, wand);
            } else {
                player.sendActionBar(Component.text(
                        "Aim at a block face, not an entity, to anchor the prefab.",
                        NamedTextColor.YELLOW));
            }
            return;
        }
        if (player.isSneaking()) {
            cycleForm(player, wand);
        } else {
            player.sendActionBar(Component.text(ENTITY_AIM_HINT, NamedTextColor.AQUA));
        }
    }

    /**
     * Minecraft may emit one interaction for each hand, but an empty selected slot can produce
     * only the offhand packet for an air click. Accept whichever arrives first and suppress only
     * a matching opposite-hand counterpart in the same or immediately following server tick.
     * Same-hand clicks are always independent, even when the server receives two in one tick.
     */
    private boolean claimControl(Player player, EquipmentSlot hand, boolean rightClick,
                                 ControlFingerprint fingerprint) {
        Map<UUID, HandledControl> controls = rightClick ? lastRightControls : lastLeftControls;
        int currentTick = Bukkit.getCurrentTick();
        HandledControl previous = controls.get(player.getUniqueId());
        if (previous != null && previous.hand() != hand && previous.fingerprint().equals(fingerprint)) {
            int age = currentTick - previous.tick();
            if (age >= 0 && age <= 1) {
                return false; // keep the original so further duplicates cannot replace it
            }
        }
        controls.put(player.getUniqueId(), new HandledControl(currentTick, hand, fingerprint));
        return true;
    }

    private void clearControlHistory(Player player) {
        lastLeftControls.remove(player.getUniqueId());
        lastRightControls.remove(player.getUniqueId());
    }

    private void handleLeftClick(Player player, ItemStack wand) {
        if (player.isSneaking()) {
            int rotation = wandItems.cycleRotation(wand); // keep the live gesture
            player.getInventory().setItemInOffHand(wand);
            player.sendActionBar(Component.text("Rotation: " + (rotation + 1)
                    + "/" + BlockOrientation.STATES,
                    NamedTextColor.GOLD));
        } else if (sessions.containsKey(player.getUniqueId())) {
            clearSession(player);
        }
    }

    private void cycleForm(Player player, ItemStack wand) {
        Form[] forms = Form.values();
        Form next = forms[(wandItems.getForm(wand).ordinal() + 1) % forms.length];
        wandItems.setForm(wand, next);
        player.getInventory().setItemInOffHand(wand);
        clearSession(player); // form change drops the anchor (design §5.1)
        player.sendActionBar(Component.text("Mode: " + next.label(), NamedTextColor.GOLD));
    }

    private void advanceGesture(Player player, ItemStack wand) {
        GestureSession session = sessions.get(player.getUniqueId());
        String heldWandId = wandItems.identity(wand).wandId();
        if (session != null && !java.util.Objects.equals(session.wandId, heldWandId)) {
            clearSession(player);
            session = null;
        }
        if (session == null) {
            WandItems.MaterialSelection selection = wandItems.materialSelection(player);
            if (selection.snapshot().isEmpty()) {
                player.sendMessage(red(selection.problem()));
                return;
            }
            tryAnchor(player, wand, wandItems.getForm(wand));
            return;
        }
        refreshPreferences(player, wand);
        if (session.stage() < session.form.lockStages()) {
            LivePlan.applyLock(player, session, config);
            return;
        }
        Optional<Plan> plan = LivePlan.derive(player, session, config, wandItems);
        if (plan.isEmpty()) {
            WandItems.MaterialSelection selection = wandItems.materialSelection(player);
            player.sendMessage(red(selection.problem().isEmpty()
                    ? "The current print could not be planned. Aim again."
                    : selection.problem()));
            return;
        }
        WaveRunner.CommitResult result = waveRunner.commit(player, plan.get(), null);
        switch (result.status()) {
            case QUOTED -> {
                // Ordinary forms never require confirmation. Fail closed if a future plan is
                // accidentally configured as one instead of leaving hidden session state behind.
                clearSession(player);
                player.sendMessage(red("BLOCKED — This print requested an unsupported confirmation."
                        + " Nothing changed or spent."));
            }
            case STARTED -> clearSession(player);
            case BLOCKED -> { /* Keep only the normal live anchor and locks; never freeze a job. */ }
        }
    }

    private void tryAnchor(Player player, ItemStack wand, Form form) {
        RayTraceResult hit = LivePlan.rayTrace(player, config);
        if (hit == null || hit.getHitBlock() == null || hit.getHitBlockFace() == null) {
            player.sendActionBar(Component.text(AIM_HINT));
            return;
        }
        WandItems.MaterialSelection selection = wandItems.materialSelection(player);
        if (selection.snapshot().isEmpty()) {
            player.sendMessage(red(selection.problem()));
            return;
        }
        Block block = hit.getHitBlock();
        BlockFace face = hit.getHitBlockFace();
        GestureSession session = new GestureSession();
        session.form = form;
        session.density = form.supportsDensity()
                ? wandItems.getDensity(wand) : com.playtheatria.buildersWand.form.Density.SHELL;
        session.surfaceRestriction = wandItems.getSurfaceRestriction(wand);
        session.wandId = wandItems.identity(wand).wandId();
        session.worldId = block.getWorld().getUID();
        session.sourceBlock = new BlockVector(block.getX(), block.getY(), block.getZ());
        session.sourceFace = face;
        session.sourceBlockData = block.getBlockData().getAsString();
        session.anchor = new BlockVector(
                block.getX() + face.getModX(),
                block.getY() + face.getModY(),
                block.getZ() + face.getModZ());
        session.orientation = orientationFor(form, face, player.getLocation().getYaw());
        sessions.put(player.getUniqueId(), session);
    }

    private static Orientation orientationFor(Form form, BlockFace clickedFace, float yaw) {
        // A box always builds as a horizontal footprint + vertical height, so its stage order is
        // the same everywhere — x or z, then the other, then y. On a wall that means using a
        // floor-style (up) basis instead of the wall's up-is-an-in-plane-axis basis; floor and
        // ceiling clicks already do this.
        if ((form == Form.BOX || form == Form.FLOOR)
                && clickedFace != BlockFace.UP && clickedFace != BlockFace.DOWN) {
            return Orientation.fromClick(BlockFace.UP, yaw);
        }
        if (form == Form.FLOOR) {
            return Orientation.fromClick(BlockFace.UP, yaw);
        }
        if (form == Form.WALL) {
            if (clickedFace == BlockFace.UP || clickedFace == BlockFace.DOWN) {
                Orientation floorBasis = Orientation.fromClick(BlockFace.UP, yaw);
                return new Orientation(floorBasis.l(), floorBasis.s(),
                        new BlockVector(0, 1, 0), false, floorBasis.heading());
            }
            BlockVector planeNormal = new BlockVector(clickedFace.getModX(), 0,
                    clickedFace.getModZ());
            BlockVector lateral = new BlockVector(planeNormal.getBlockZ(), 0,
                    -planeNormal.getBlockX());
            return new Orientation(lateral, planeNormal, new BlockVector(0, 1, 0),
                    true, clickedFace);
        }
        return Orientation.fromClick(clickedFace, yaw);
    }

    /** Left-click cancels or rotates the wand and never attacks an entity. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player
                && player.hasPermission(WandItems.PERMISSION_USE)
                && wandItems.isWand(player.getInventory().getItemInOffHand())) {
            event.setCancelled(true);
            ItemStack wand = player.getInventory().getItemInOffHand();
            if (!claimControl(player, EquipmentSlot.HAND, false,
                    ControlFingerprint.entity(event.getEntity().getUniqueId()))) {
                return;
            }
            if (wand.getAmount() != 1) {
                clearSession(player);
                player.sendMessage(red("Builders Wands cannot be used while stacked. Separate them first."));
                return;
            }
            wandItems.ensureFirstWielder(wand, player);
            player.getInventory().setItemInOffHand(wand);
            if (prefabPlacement != null && prefabPlacement.active(player)) {
                prefabPlacement.leftClick(player, player.isSneaking());
            } else {
                handleLeftClick(player, wand);
            }
        }
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (wandItems.isWand(event.getOffHandItem()) || wandItems.isWand(event.getMainHandItem())) {
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
        clearControlHistory(event.getEntity());
        clearSession(event.getEntity());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        clearControlHistory(event.getPlayer());
        clearSession(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        clearControlHistory(player);
        clearSession(player);
        waveRunner.stopFor(player, StopReason.PLAYER_QUIT);
    }

    private static Component red(String text) {
        return Component.text(text, NamedTextColor.RED);
    }
}
