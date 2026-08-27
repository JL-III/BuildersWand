package com.playtheatria.buildersWand.wave;

import com.playtheatria.buildersWand.wand.PrintMaterial;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One in-flight print (design §10.1). {@code completed} counts completed cells; ordinary
 * feedstock is spent one item per cell as the wave runs, while reusable catalysts are retained.
 * {@code usesSpent} records economic Uses independently because a cell may cost more than one.
 * {@code ticksUntilNext} is the per-wave cadence phase for the shared 1-tick runner task.
 */
public final class Wave {

    public final UUID owner;
    public final String ownerName;
    public final World world;
    public final PrintMaterial material;
    public final BlockData blockData;
    public final List<Location> printable;
    public final int ticksPerCell;
    public final Set<Chunk> tickets;
    public final boolean creative;
    public final boolean usesBypass;
    public final int usesPerCell;
    public final String wandToken;

    public int completed;
    public long usesSpent;
    public int actualBlocksPlaced;
    public int actualWaterCells;
    public boolean statisticsRecorded;
    public int ticksUntilNext;

    public Wave(UUID owner, String ownerName, World world, PrintMaterial material,
                BlockData blockData, List<Location> printable,
                int ticksPerCell, Set<Chunk> tickets, boolean creative,
                boolean usesBypass, int usesPerCell, String wandToken) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.world = world;
        this.material = material;
        this.blockData = blockData;
        this.printable = printable;
        this.ticksPerCell = ticksPerCell;
        this.tickets = tickets;
        this.creative = creative;
        this.usesBypass = usesBypass;
        if (usesPerCell < 1) {
            throw new IllegalArgumentException("usesPerCell must be positive");
        }
        this.usesPerCell = usesPerCell;
        this.wandToken = wandToken;
        this.completed = 0;
        this.usesSpent = 0L;
        this.actualBlocksPlaced = 0;
        this.actualWaterCells = 0;
        this.statisticsRecorded = false;
        this.ticksUntilNext = ticksPerCell;
    }

    public int total() {
        return printable.size();
    }
}
