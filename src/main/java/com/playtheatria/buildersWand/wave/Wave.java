package com.playtheatria.buildersWand.wave;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One in-flight print (design §10.1). {@code completed}/{@code reserved} track progress and
 * unspent feedstock; the invariant {@code reserved == printable.size() − completed} holds for
 * non-creative waves. {@code ticksUntilNext} is the per-wave cadence phase for the shared
 * 1-tick runner task.
 */
public final class Wave {

    public final UUID owner;
    public final World world;
    public final Material material;
    public final List<Location> printable;
    public final int ticksPerCell;
    public final Set<Chunk> tickets;
    public final boolean creative;

    public int completed;
    public int reserved;
    public int ticksUntilNext;

    public Wave(UUID owner, World world, Material material, List<Location> printable,
                int ticksPerCell, Set<Chunk> tickets, boolean creative, int reserved) {
        this.owner = owner;
        this.world = world;
        this.material = material;
        this.printable = printable;
        this.ticksPerCell = ticksPerCell;
        this.tickets = tickets;
        this.creative = creative;
        this.reserved = reserved;
        this.completed = 0;
        this.ticksUntilNext = ticksPerCell;
    }

    public int total() {
        return printable.size();
    }
}
