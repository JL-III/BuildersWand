package com.playtheatria.buildersWand.wave;

import com.playtheatria.buildersWand.config.PluginConfig;
import com.playtheatria.buildersWand.form.Dims;
import com.playtheatria.buildersWand.protect.PlacementLogger;
import com.playtheatria.buildersWand.protect.ProtectionBridge;
import com.playtheatria.buildersWand.stats.BuildStatsStore;
import com.playtheatria.buildersWand.stats.BuildStatistic;
import com.playtheatria.buildersWand.stats.WaveStatsDelta;
import com.playtheatria.buildersWand.utils.Err;
import com.playtheatria.buildersWand.wand.PlacementUseCost;
import com.playtheatria.buildersWand.wand.PrintMaterial;
import com.playtheatria.buildersWand.wand.UseCounter;
import com.playtheatria.buildersWand.wand.WandItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Owns every in-flight {@link Wave} and the single 1-tick runner task (design §9–§11). The
 * task is started lazily while any wave exists and cancelled when none remain.
 */
public final class WaveRunner {

    private static final int BODY_PUSH_MAX = 12; // design §10.4

    private final JavaPlugin plugin;
    private final ProtectionBridge protection;
    private final PlacementLogger placementLogger;
    private final PluginConfig config;
    private final WandItems wandItems;
    private final BuildStatsStore buildStats;
    private final Map<UUID, Wave> waves = new java.util.HashMap<>();

    private BukkitTask task;

    public WaveRunner(JavaPlugin plugin, ProtectionBridge protection, PlacementLogger placementLogger,
                      PluginConfig config, WandItems wandItems, BuildStatsStore buildStats) {
        this.plugin = plugin;
        this.protection = protection;
        this.placementLogger = placementLogger;
        this.config = config;
        this.wandItems = wandItems;
        this.buildStats = buildStats;
    }

    // ---------------------------------------------------------------- commit (design §9)

    /** Runs the §9 pipeline; returns true iff a wave started (so the gesture may clear). */
    public boolean commit(Player player, Plan plan) {
        UUID id = player.getUniqueId();

        // 1. Busy
        Wave existing = waves.get(id);
        if (existing != null) {
            player.sendMessage(red("A print is already running (" + existing.completed + "/" + existing.total() + ")."));
            return false;
        }

        ItemStack wand = player.getInventory().getItemInMainHand();
        if (!wandItems.isWand(wand) || wand.getAmount() != 1) {
            player.sendMessage(red("Hold one unstacked Builders Wand in your main hand."));
            return false;
        }
        boolean usesBypass = player.hasPermission(WandItems.PERMISSION_USES_BYPASS);
        UseCounter.State wandUses = wandItems.uses(wand);
        if (!usesBypass && wandUses.depleted()) {
            player.sendMessage(red("The Builders Wand has no Uses remaining. Restore them with /wand restore."));
            return false;
        }

        // 2. Material (offhand is an allowed block or the reusable water-bucket catalyst)
        ItemStack offhand = player.getInventory().getItemInOffHand();
        Optional<PrintMaterial> selection = wandItems.selectedMaterial(player);
        if (selection.isEmpty()) {
            Material held = offhand.getType();
            if (WandItems.isDenylisted(held)) {
                player.sendMessage(red(WandItems.materialDisplayName(held)
                        + " can't be printed (multi-block or content-carrying)."));
            } else {
                player.sendMessage(red(WandItems.MATERIAL_HINT));
            }
            return false;
        }
        PrintMaterial material = selection.get();
        if (!material.equals(plan.material())) {
            player.sendMessage(red("Your off-hand material changed. Preview the print again."));
            return false;
        }
        if (material.isWater() && plan.world().isUltraWarm()) {
            player.sendMessage(red("Water evaporates in this world. Nothing changed or spent."));
            return false;
        }

        // 3. Dims legal (defense in depth — the gesture already guarantees it)
        if (Dims.validated(plan.form(), plan.dims().primary(), plan.dims().secondary(), plan.dims().tertiary()) instanceof Err) {
            plugin.getLogger().severe("Commit received illegal dims " + plan.dims() + " for " + plan.form());
            return false;
        }

        // 4. Cells (already expanded in emission order by LivePlan)
        List<Location> cells = plan.cells();

        // 5. Protection, per cell
        for (Location loc : cells) {
            Optional<String> denier = protection.deniedBy(player, loc);
            if (denier.isPresent()) {
                player.sendMessage(red("Blocked by " + denier.get() + " at " + coords(loc) + ". Nothing changed or spent."));
                return false;
            }
        }

        // 6. Classify printable vs kept
        List<Location> printable = new ArrayList<>();
        int kept = 0;
        for (Location loc : cells) {
            if (PlacementRules.isPrintable(loc.getBlock(), plan.blockData())) {
                printable.add(loc);
            } else {
                kept++;
            }
        }

        // 7. All kept
        if (printable.isEmpty()) {
            player.sendMessage(red("Every cell is already built. Nothing to print."));
            return false;
        }

        int usesPerCell = PlacementUseCost.perCell(material, config.waterUsesPerSource);
        if (!usesBypass && wandUses.remaining() < usesPerCell) {
            player.sendMessage(red("Water placement costs " + usesPerCell
                    + " Uses per source, but this wand has only " + wandUses.remaining() + "."));
            return false;
        }

        // 8. (Single removed) — all forms push bodies clear mid-wave (§10.4) rather than refuse.

        // 9. Ordinary feedstock is spent one item per cell while the wave runs (never reserved
        //    up front); a reusable water bucket is retained and has no per-cell cost.
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        int need = printable.size();

        // Bind this wave to the exact held item only after every refusal check has passed.
        // The token is rotated here so cloned kit templates cannot share a permanent identity.
        wandItems.ensureFirstWielder(wand, player);
        String wandToken = wandItems.rotateActiveToken(wand);
        player.getInventory().setItemInMainHand(wand);
        wandUses = wandItems.uses(wand);

        // 10. Start the wave: chunk tickets over the plan, register, schedule
        Set<Chunk> tickets = new HashSet<>();
        for (Location loc : cells) {
            Chunk chunk = loc.getChunk();
            if (tickets.add(chunk)) {
                chunk.addPluginChunkTicket(plugin);
            }
        }
        int ticksPerCell = need <= config.smallPrintMaxCells
                ? config.smallPrintTicksPerCell
                : config.largePrintTicksPerCell;
        waves.put(id, new Wave(id, player.getName(), plan.world(), material, plan.blockData(), printable,
                ticksPerCell, tickets, creative, usesBypass, usesPerCell, wandToken));
        ensureTask();

        String usesText = usesBypass ? "uses bypassed" : wandUses.remaining() + " uses available";
        Component startMessage = Component.text("Printing " + plan.form().key() + ": " + need
                + " cells (" + kept + " kept); " + usesText + ".", NamedTextColor.GREEN);
        if (material.isWater()) {
            long plannedUses = PlacementUseCost.totalUses(need, usesPerCell);
            String notice = usesBypass
                    ? " Water placement normally costs " + usesPerCell + " Uses per source ("
                            + "up to " + plannedUses + " Uses for this print); your Uses are bypassed."
                    : " Water placement costs " + usesPerCell + " Uses per source ("
                            + "up to " + plannedUses + " Uses for this print).";
            startMessage = startMessage.append(Component.text(notice, NamedTextColor.GOLD));
        }
        player.sendMessage(startMessage);
        return true;
    }

    /** Whether this player has a wave printing right now (ghosts are suppressed while so). */
    public boolean hasActiveWave(Player player) {
        return waves.containsKey(player.getUniqueId());
    }

    // ---------------------------------------------------------------- tick (design §10.3)

    private void tick() {
        for (Wave wave : new ArrayList<>(waves.values())) {
            if (waves.get(wave.owner) != wave) {
                continue; // already settled/stopped this tick
            }
            if (--wave.ticksUntilNext > 0) {
                continue;
            }
            wave.ticksUntilNext = wave.ticksPerCell;
            try {
                step(wave);
            } catch (RuntimeException error) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Unhandled Builders Wand wave failure for " + wave.owner, error);
                if (waves.get(wave.owner) == wave) {
                    stop(wave, Bukkit.getPlayer(wave.owner), StopReason.INTERNAL_ERROR, null);
                }
            }
        }
        stopTaskIfIdle();
    }

    private void step(Wave wave) {
        Player player = Bukkit.getPlayer(wave.owner);
        if (player == null) {
            // Quit should have stopped the wave already; be defensive.
            stopFor(wave.owner, StopReason.PLAYER_QUIT, null);
            return;
        }

        Location loc = wave.printable.get(wave.completed);
        Block block = loc.getBlock();

        // 1. Protection re-check (stops before spending — nothing is lost)
        if (!protection.canBuild(player, loc)) {
            stop(wave, player, StopReason.PERMISSION_LOST, loc);
            return;
        }
        ItemStack activeWand = player.getInventory().getItemInMainHand();
        if (!wandItems.hasActiveToken(activeWand, wave.wandToken)) {
            stop(wave, player, StopReason.WAND_REMOVED, null);
            return;
        }
        if (!wave.usesBypass && wandItems.uses(activeWand).remaining() < wave.usesPerCell) {
            stop(wave, player, StopReason.OUT_OF_USES, null);
            return;
        }
        if (wave.material.reusable()
                && wandItems.selectedMaterial(player).filter(wave.material::equals).isEmpty()) {
            stop(wave, player, StopReason.WATER_BUCKET_REMOVED, null);
            return;
        }
        // A prior placement can turn a later planned water cell into a source through vanilla
        // infinite-source physics. It is complete now; avoid logging and sounding a no-op.
        if (wave.material.isWater() && PlacementRules.isAlreadyBuilt(block, wave.blockData)) {
            Optional<WandItems.UseReceipt> use = spendUses(wave, player, activeWand);
            if (use.isEmpty()) {
                stop(wave, player, StopReason.OUT_OF_USES, null);
                return;
            }
            completeCell(wave, player, false);
            return;
        }
        // 2. No longer replaceable
        if (!block.isReplaceable()) {
            stop(wave, player, StopReason.BLOCK_IN_WAY, loc);
            return;
        }
        // 3. Push living entities clear
        if (!wave.material.isWater() && !pushBodies(wave, loc)) {
            stop(wave, player, StopReason.BODY_STUCK, loc);
            return;
        }
        // Capture the world rollback point before either resource is spent.
        BlockState before = block.getState();

        // 4. Precheck both resources, then mutate them synchronously. Uses are written first
        //    because it has an exact-token rollback; an ordinary material is only removed after
        //    the precheck makes that removal deterministic on the server thread.
        boolean spendsFeedstock = !wave.creative && !wave.material.reusable();
        if (spendsFeedstock
                && Feedstock.count(player.getInventory(), wave.material.sourceItem(), wandItems) == 0) {
            stop(wave, player, StopReason.OUT_OF_MATERIAL, null);
            return;
        }
        Optional<WandItems.UseReceipt> spentUse = spendUses(wave, player, activeWand);
        if (spentUse.isEmpty()) {
            stop(wave, player, StopReason.OUT_OF_USES, null);
            return;
        }
        Feedstock.Receipt feedstockReceipt = null;
        if (spendsFeedstock) {
            Optional<Feedstock.Receipt> spent = Feedstock.spendOne(
                    player.getInventory(), wave.material.sourceItem(), wandItems);
            if (spent.isEmpty()) {
                restoreUse(wave, player, spentUse.get());
                stop(wave, player, StopReason.OUT_OF_MATERIAL, null);
                return;
            }
            feedstockReceipt = spent.get();
        }

        // 5. Place with physics. If the server throws, inspect the postcondition: a target block
        //    keeps its resource spend; otherwise restore the prior state and both exact debits.
        try {
            block.setBlockData(wave.blockData, true);
        } catch (RuntimeException error) {
            handlePlacementFailure(wave, player, block, before,
                    spentUse.get(), feedstockReceipt, error);
            return;
        }
        try {
            if (wave.material.isWater()) {
                wave.world.playSound(loc, Sound.ITEM_BUCKET_EMPTY, SoundCategory.BLOCKS, 1.0f, 1.0f);
            } else {
                wave.world.playSound(loc, block.getBlockSoundGroup().getPlaceSound(), 1.0f, 1.0f);
            }
        } catch (RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Builders Wand placed a block but could not play its sound at " + coords(loc), error);
        }
        try {
            placementLogger.logPlacement(player, before, block.getState());
        } catch (RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Builders Wand placed a block but could not log it at " + coords(loc), error);
            completeCell(wave, player, true);
            if (waves.get(wave.owner) == wave) {
                stop(wave, player, StopReason.INTERNAL_ERROR, null);
            }
            return;
        }
        // 6. Account
        completeCell(wave, player, true);
    }

    private void handlePlacementFailure(Wave wave, Player player, Block block, BlockState before,
                                        WandItems.UseReceipt useReceipt,
                                        Feedstock.Receipt feedstockReceipt,
                                        RuntimeException error) {
        plugin.getLogger().log(java.util.logging.Level.SEVERE,
                "Builders Wand placement threw at " + coords(block.getLocation()), error);
        if (PlacementRules.isAlreadyBuilt(block, wave.blockData)) {
            try {
                placementLogger.logPlacement(player, before, block.getState());
            } catch (RuntimeException logError) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Builders Wand could not log a placement that completed while throwing at "
                                + coords(block.getLocation()), logError);
            }
            completeCell(wave, player, true);
            if (waves.get(wave.owner) == wave) {
                stop(wave, player, StopReason.INTERNAL_ERROR, null);
            }
            return;
        }
        try {
            if (!before.update(true, false)) {
                plugin.getLogger().severe("Could not restore the prior block state at "
                        + coords(block.getLocation()));
            }
        } catch (RuntimeException restoreError) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Exception restoring the prior block state at " + coords(block.getLocation()), restoreError);
        }
        restoreUse(wave, player, useReceipt);
        if (feedstockReceipt != null) {
            ItemStack leftover = Feedstock.restoreOne(player.getInventory(), feedstockReceipt);
            if (leftover != null) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                plugin.getLogger().warning("Restored a failed Builders Wand placement by dropping feedstock for "
                        + player.getUniqueId());
            }
        }
        stop(wave, player, StopReason.INTERNAL_ERROR, null);
    }

    private Optional<WandItems.UseReceipt> spendUses(Wave wave, Player player, ItemStack activeWand) {
        Optional<WandItems.UseReceipt> receipt = wandItems.spendUses(
                activeWand, wave.wandToken, wave.usesPerCell, !wave.usesBypass);
        if (receipt.isEmpty()) {
            return Optional.empty();
        }
        player.getInventory().setItemInMainHand(activeWand);
        return receipt;
    }

    private void restoreUse(Wave wave, Player player, WandItems.UseReceipt receipt) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (wandItems.restoreUse(mainHand, wave.wandToken, receipt)) {
            player.getInventory().setItemInMainHand(mainHand);
            return;
        }
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            if (wandItems.restoreUse(storage[slot], wave.wandToken, receipt)) {
                player.getInventory().setStorageContents(storage);
                return;
            }
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (wandItems.restoreUse(offhand, wave.wandToken, receipt)) {
            player.getInventory().setItemInOffHand(offhand);
            return;
        }
        plugin.getLogger().severe("Could not restore wand uses after a failed resource or placement update for "
                + player.getUniqueId());
    }

    private void completeCell(Wave wave, Player player, boolean actuallyPlaced) {
        wave.usesSpent += wave.usesPerCell;
        if (actuallyPlaced) {
            if (wave.material.isWater()) {
                wave.actualWaterCells++;
            } else {
                wave.actualBlocksPlaced++;
            }
        }
        wave.completed++;
        if (wave.completed >= wave.total()) {
            settle(wave, player);
        }
    }

    // ---------------------------------------------------------------- body push (design §10.4)

    private boolean pushBodies(Wave wave, Location cell) {
        BoundingBox cellBox = new BoundingBox(
                cell.getBlockX(), cell.getBlockY(), cell.getBlockZ(),
                cell.getBlockX() + 1, cell.getBlockY() + 1, cell.getBlockZ() + 1);
        List<Entity> bodies = new ArrayList<>(
                wave.world.getNearbyEntities(cellBox, e -> e instanceof LivingEntity));
        if (bodies.isEmpty()) {
            return true;
        }
        Set<Long> unplaced = unplacedKeys(wave);
        for (Entity entity : bodies) {
            if (!pushOne(wave, (LivingEntity) entity, unplaced)) {
                return false;
            }
        }
        return true;
    }

    private boolean pushOne(Wave wave, LivingEntity entity, Set<Long> unplaced) {
        Location feet = entity.getLocation();
        int bx = feet.getBlockX();
        int bz = feet.getBlockZ();
        int startY = feet.getBlockY();
        for (int y = startY; y <= startY + BODY_PUSH_MAX; y++) {
            if (passableForPush(wave, bx, y, bz, unplaced) && passableForPush(wave, bx, y + 1, bz, unplaced)) {
                entity.teleport(new Location(wave.world, feet.getX(), y, feet.getZ(), feet.getYaw(), feet.getPitch()));
                return true;
            }
        }
        return false;
    }

    private boolean passableForPush(Wave wave, int x, int y, int z, Set<Long> unplaced) {
        if (unplaced.contains(key(x, y, z))) {
            return false;
        }
        return wave.world.getBlockAt(x, y, z).isPassable();
    }

    private Set<Long> unplacedKeys(Wave wave) {
        Set<Long> keys = new HashSet<>();
        for (int i = wave.completed; i < wave.printable.size(); i++) {
            Location loc = wave.printable.get(i);
            keys.add(key(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
        }
        return keys;
    }

    // ---------------------------------------------------------------- stop / settle

    /** Stop this player's wave (design §10.3); unspent feedstock stays in the inventory. */
    public void stopFor(Player player, StopReason reason) {
        Wave wave = waves.get(player.getUniqueId());
        if (wave != null) {
            stop(wave, player, reason, null);
        }
    }

    /** Stop every wave (plugin disable / server stop, design §10.5). */
    public void stopAll(StopReason reason) {
        for (Wave wave : new ArrayList<>(waves.values())) {
            stop(wave, Bukkit.getPlayer(wave.owner), reason, null);
        }
    }

    private void stopFor(UUID owner, StopReason reason, Location at) {
        Wave wave = waves.get(owner);
        if (wave != null) {
            stop(wave, Bukkit.getPlayer(owner), reason, at);
        }
    }

    private void stop(Wave wave, Player player, StopReason reason, Location at) {
        // No refund: feedstock is spent per cell, so unspent items are still in the inventory.
        recordStatistics(wave, false, player != null
                && reason != StopReason.SERVER_STOPPING && reason != StopReason.PLAYER_QUIT);
        releaseTickets(wave);
        waves.remove(wave.owner);
        if (player != null) {
            player.sendMessage(Component.text(wave.completed + "/" + wave.total()
                    + " cells placed; " + reason.phrase(at) + ".", NamedTextColor.GOLD));
        }
        stopTaskIfIdle();
    }

    private void settle(Wave wave, Player player) {
        recordStatistics(wave, true, player != null);
        releaseTickets(wave);
        waves.remove(wave.owner);
        if (player != null) {
            player.sendActionBar(Component.text("Printed " + wave.total() + " "
                    + WandItems.materialDisplayName(wave.material.placedBlock()) + "."));
        }
        stopTaskIfIdle();
    }

    private void recordStatistics(Wave wave, boolean completedPrint, boolean mayAnnounce) {
        if (wave.statisticsRecorded) {
            return;
        }
        wave.statisticsRecorded = true;
        boolean persisted;
        try {
            persisted = buildStats.recordWave(wave.owner, wave.ownerName, new WaveStatsDelta(
                    wave.usesSpent,
                    wave.actualBlocksPlaced,
                    wave.actualWaterCells,
                    completedPrint,
                    wave.total()));
        } catch (RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Unexpected failure recording Builders Wand build statistics for " + wave.owner, error);
            return;
        }
        if (!persisted || !mayAnnounce || !config.recognitionAnnouncements) {
            return;
        }
        for (long milestone : config.totalBlockMilestones) {
            boolean newlyReached;
            try {
                newlyReached = buildStats.claimRecognitionIfReached(
                        wave.owner, BuildStatistic.TOTAL_BLOCKS_PLACED, milestone);
            } catch (RuntimeException error) {
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "Unexpected failure checking Builders Wand recognition for " + wave.owner, error);
                break;
            }
            if (newlyReached) {
                Bukkit.broadcast(Component.text("✦ Master Builder ✦ ", NamedTextColor.GOLD)
                        .append(Component.text(wave.ownerName, NamedTextColor.LIGHT_PURPLE))
                        .append(Component.text(" has materialized "
                                + String.format(Locale.US, "%,d", milestone)
                                + " blocks with the Builders Wand!", NamedTextColor.GOLD)));
            }
        }
    }

    private void releaseTickets(Wave wave) {
        for (Chunk chunk : wave.tickets) {
            chunk.removePluginChunkTicket(plugin);
        }
    }

    // ---------------------------------------------------------------- task lifecycle

    private void ensureTask() {
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        }
    }

    private void stopTaskIfIdle() {
        if (waves.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    // ---------------------------------------------------------------- helpers

    private static String coords(Location loc) {
        return loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }

    private static Component red(String text) {
        return Component.text(text, NamedTextColor.RED);
    }

    private static long key(int x, int y, int z) {
        return (((long) x) & 0x3FFFFFFL) << 38 | (((long) z) & 0x3FFFFFFL) << 12 | (((long) y) & 0xFFFL);
    }
}
