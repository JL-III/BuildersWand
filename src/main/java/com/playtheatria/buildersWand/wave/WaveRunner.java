package com.playtheatria.buildersWand.wave;

import com.playtheatria.buildersWand.config.PluginConfig;
import com.playtheatria.buildersWand.form.Dims;
import com.playtheatria.buildersWand.protect.ProtectionBridge;
import com.playtheatria.buildersWand.utils.Err;
import com.playtheatria.buildersWand.wand.WandItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
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
    private final PluginConfig config;
    private final WandItems wandItems;
    private final Map<UUID, Wave> waves = new java.util.HashMap<>();

    private BukkitTask task;

    public WaveRunner(JavaPlugin plugin, ProtectionBridge protection, PluginConfig config, WandItems wandItems) {
        this.plugin = plugin;
        this.protection = protection;
        this.config = config;
        this.wandItems = wandItems;
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

        // 2. Material (offhand is an allowed material)
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand.getType().isAir() || wandItems.isWand(offhand)) {
            player.sendMessage(red("Hold a placeable block in your off hand to choose the material."));
            return false;
        }
        Material material = offhand.getType();
        if (WandItems.isDenylisted(material)) {
            player.sendMessage(red(WandItems.materialDisplayName(material) + " can't be printed (multi-block or content-carrying)."));
            return false;
        }
        if (!WandItems.isAllowedMaterial(material)) {
            player.sendMessage(red("Hold a placeable block in your off hand to choose the material."));
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
            if (loc.getBlock().isReplaceable()) {
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

        // 8. (Single removed) — all forms push bodies clear mid-wave (§10.4) rather than refuse.

        // 9. Feedstock — build as many cells as the player can afford (partial build).
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        int need = printable.size();
        int toBuild = need;
        int reserved = 0;
        if (!creative) {
            int have = Feedstock.count(player.getInventory(), material, wandItems);
            toBuild = Math.min(have, need);
            if (toBuild == 0) {
                player.sendMessage(red("You need at least one " + WandItems.materialDisplayName(material) + " to print. Nothing changed or spent."));
                return false;
            }
            Feedstock.reserve(player.getInventory(), material, toBuild, wandItems);
            reserved = toBuild;
        }
        // Only the first `toBuild` printable cells (emission order) are placed; the rest are dropped.
        List<Location> toPrint = toBuild < printable.size()
                ? new ArrayList<>(printable.subList(0, toBuild))
                : printable;

        // 10. Start the wave: chunk tickets over the plan, register, schedule
        Set<Chunk> tickets = new HashSet<>();
        for (Location loc : cells) {
            Chunk chunk = loc.getChunk();
            if (tickets.add(chunk)) {
                chunk.addPluginChunkTicket(plugin);
            }
        }
        int ticksPerCell = toBuild <= config.smallPrintMaxCells
                ? config.smallPrintTicksPerCell
                : config.largePrintTicksPerCell;
        waves.put(id, new Wave(id, plan.world(), material, plan.blockData(), toPrint, ticksPerCell, tickets, creative, reserved));
        ensureTask();

        if (toBuild < need) {
            player.sendMessage(Component.text("Printing " + plan.form().key() + ": " + toBuild + " of " + need
                    + " cells (" + kept + " kept, short " + (need - toBuild) + ").", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Printing " + plan.form().key() + ": " + need
                    + " cells (" + kept + " kept).", NamedTextColor.GREEN));
        }
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
            step(wave);
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

        if (!wave.creative && wave.reserved != wave.total() - wave.completed) {
            plugin.getLogger().severe("Wave invariant violated for " + wave.owner + ": reserved="
                    + wave.reserved + ", printable=" + wave.total() + ", completed=" + wave.completed
                    + "; stopping and refunding.");
            if (wave.reserved > 0) {
                Feedstock.refund(player, wave.material, wave.reserved);
            }
            releaseTickets(wave);
            waves.remove(wave.owner);
            player.sendMessage(Component.text(wave.completed + "/" + wave.total()
                    + " cells placed; an internal error occurred. The unplaced blocks were refunded.",
                    NamedTextColor.GOLD));
            return;
        }

        Location loc = wave.printable.get(wave.completed);
        Block block = loc.getBlock();

        // 1. Protection re-check
        if (!protection.canBuild(player, loc)) {
            stop(wave, player, StopReason.PERMISSION_LOST, loc);
            return;
        }
        // 2. No longer replaceable
        if (!block.isReplaceable()) {
            stop(wave, player, StopReason.BLOCK_IN_WAY, loc);
            return;
        }
        // 3. Push living entities clear
        if (!pushBodies(wave, loc)) {
            stop(wave, player, StopReason.BODY_STUCK, loc);
            return;
        }
        // 4. Place the oriented state with physics, then play the placed block's sound
        block.setBlockData(wave.blockData, true);
        wave.world.playSound(loc, block.getBlockSoundGroup().getPlaceSound(), 1.0f, 1.0f);
        // 5. Account
        wave.completed++;
        if (!wave.creative) {
            wave.reserved--;
        }
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

    /** Stop this player's wave (design §10.3), refunding the unspent remainder. */
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
        if (!wave.creative && wave.reserved > 0 && player != null) {
            Feedstock.refund(player, wave.material, wave.reserved);
        }
        releaseTickets(wave);
        waves.remove(wave.owner);
        if (player != null) {
            player.sendMessage(Component.text(wave.completed + "/" + wave.total() + " cells placed; "
                    + reason.phrase(at) + ". The unplaced blocks were refunded.", NamedTextColor.GOLD));
        }
        stopTaskIfIdle();
    }

    private void settle(Wave wave, Player player) {
        releaseTickets(wave);
        waves.remove(wave.owner);
        if (player != null) {
            player.sendActionBar(Component.text("Printed " + wave.total() + " "
                    + WandItems.materialDisplayName(wave.material) + "."));
        }
        stopTaskIfIdle();
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
