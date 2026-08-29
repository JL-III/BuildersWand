package com.playtheatria.buildersWand.wave;

import com.playtheatria.buildersWand.wand.MaterialSelectionSnapshot;
import com.playtheatria.buildersWand.wand.WandItems;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One in-flight print (design §10.1). The placement queue gives a body-blocked solid cell one
 * deferred retry after the primary pass. Ordinary feedstock is spent one item per completed cell,
 * while reusable catalysts are retained. {@code usesSpent} records economic Uses independently
 * because a cell may cost more than one. {@code ticksUntilNext} is the per-wave cadence phase for
 * the shared 1-tick runner task.
 */
public final class Wave {

    public final UUID owner;
    public final String ownerName;
    public final World world;
    public final MaterialSelectionSnapshot materialSelection;
    public final PlanOptions options;
    /** Exact cells admitted by the full or explicitly confirmed partial resource quote. */
    public final List<PlannedCell> printable;
    final DeferredPlacementQueue<PlannedCell> placements;
    public final int ticksPerCell;
    public final Set<Chunk> tickets;
    public final boolean creative;
    public final boolean usesBypass;
    public final String wandToken;
    /** Up-front prefab invocation debit, restored only for a zero-cell internal start failure. */
    public final WandItems.UseReceipt activationReceipt;
    public final int activationUses;
    /** Printable cells in the frozen plan before the resource subset was selected. */
    public final int requestedPrintableCells;
    /** True when this wave intentionally leaves resource-short cells for a continuation. */
    public final boolean partialAdmission;

    public long usesSpent;
    public int actualBlocksPlaced;
    public int actualWaterCells;
    public boolean statisticsRecorded;
    public int ticksUntilNext;

    public Wave(UUID owner, String ownerName, World world,
                MaterialSelectionSnapshot materialSelection, PlanOptions options,
                List<PlannedCell> printable,
                int ticksPerCell, Set<Chunk> tickets, boolean creative,
                boolean usesBypass, String wandToken, int requestedPrintableCells,
                WandItems.UseReceipt activationReceipt, int activationUses) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.world = world;
        this.materialSelection = materialSelection;
        this.options = options;
        this.printable = List.copyOf(printable);
        this.placements = new DeferredPlacementQueue<>(this.printable);
        this.ticksPerCell = ticksPerCell;
        this.tickets = tickets;
        this.creative = creative;
        this.usesBypass = usesBypass;
        this.wandToken = wandToken;
        this.activationReceipt = activationReceipt;
        if (activationUses < 0) {
            throw new IllegalArgumentException("activation Uses cannot be negative");
        }
        this.activationUses = activationUses;
        if (requestedPrintableCells < printable.size()) {
            throw new IllegalArgumentException("requested cells cannot be fewer than admitted cells");
        }
        this.requestedPrintableCells = requestedPrintableCells;
        this.partialAdmission = printable.size() < requestedPrintableCells;
        this.usesSpent = activationUses;
        this.actualBlocksPlaced = 0;
        this.actualWaterCells = 0;
        this.statisticsRecorded = false;
        this.ticksUntilNext = ticksPerCell;
    }

    public int total() {
        return placements.total();
    }

    public int resolved() {
        return placements.resolved();
    }

    public int skippedOccupied() {
        return placements.skipped();
    }

    public int successfulCells() {
        return placements.successful();
    }
}
