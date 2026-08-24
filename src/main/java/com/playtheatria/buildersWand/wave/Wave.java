package com.playtheatria.buildersWand.wave;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One in-flight print (design §10.1). {@code completed} counts placed cells; feedstock is spent
 * one item per cell as the wave runs (no up-front reserve, so nothing to refund on a stop).
 * {@code ticksUntilNext} is the per-wave cadence phase for the shared 1-tick runner task.
 */
public final class Wave {

    public final UUID owner;
    public final World world;
    public final Material material;
    public final BlockData blockData;
    public final List<Location> printable;
    public final int ticksPerCell;
    public final Set<Chunk> tickets;
    public final boolean creative;

    public int completed;
    public int ticksUntilNext;

    public Wave(UUID owner, World world, Material material, BlockData blockData, List<Location> printable,
                int ticksPerCell, Set<Chunk> tickets, boolean creative) {
        this.owner = owner;
        this.world = world;
        this.material = material;
        this.blockData = blockData;
        this.printable = printable;
        this.ticksPerCell = ticksPerCell;
        this.tickets = tickets;
        this.creative = creative;
        this.completed = 0;
        this.ticksUntilNext = ticksPerCell;
    }

    public int total() {
        return printable.size();
    }
}
