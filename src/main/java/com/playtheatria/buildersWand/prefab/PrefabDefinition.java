package com.playtheatria.buildersWand.prefab;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable, validated, offset-independent runtime prefab definition. */
public final class PrefabDefinition {

    private static final Comparator<PrefabCell> BUILD_PRIORITY = Comparator
            .comparingInt((PrefabCell cell) -> cell.position().y())
            .thenComparingInt(cell -> cell.position().x() + cell.position().z())
            .thenComparingInt(cell -> cell.position().z())
            .thenComparingInt(cell -> cell.position().x());

    private final PrefabMetadata metadata;
    private final PrefabDimensions dimensions;
    private final PrefabPosition inferredSourceAnchor;
    private final List<PrefabCell> cells;
    private final List<PrefabCell> placementCells;
    private final List<PrefabCell> clearanceCells;
    private final Map<PrefabPosition, PrefabCell> cellsByPosition;
    private final Map<String, Long> materialTotals;
    private final String contentHash;

    PrefabDefinition(
            PrefabMetadata metadata,
            PrefabDimensions dimensions,
            PrefabPosition inferredSourceAnchor,
            List<PrefabCell> cells,
            String contentHash
    ) {
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.dimensions = Objects.requireNonNull(dimensions, "dimensions");
        this.inferredSourceAnchor = Objects.requireNonNull(
                inferredSourceAnchor,
                "inferredSourceAnchor"
        );
        this.contentHash = requireHash(contentHash);

        List<PrefabCell> sorted = new ArrayList<>(cells);
        sorted.sort(Comparator.comparing(PrefabCell::position));
        if (sorted.size() != dimensions.volume()) {
            throw new IllegalArgumentException("Prefab must classify every cell in its trimmed volume");
        }
        Map<PrefabPosition, PrefabCell> indexed = new LinkedHashMap<>();
        for (PrefabCell cell : sorted) {
            if (!dimensions.contains(cell.position())) {
                throw new IllegalArgumentException("Prefab cell is outside normalized dimensions");
            }
            if (indexed.putIfAbsent(cell.position(), cell) != null) {
                throw new IllegalArgumentException("Prefab contains a duplicate cell position");
            }
        }
        this.cells = List.copyOf(sorted);
        this.cellsByPosition = Collections.unmodifiableMap(indexed);

        List<PrefabCell> blocks = sorted.stream()
                .filter(cell -> cell.kind() == PrefabCellKind.BLOCK)
                .sorted(BUILD_PRIORITY)
                .toList();
        if (blocks.isEmpty()) {
            throw new IllegalArgumentException("Prefab must contain at least one block");
        }
        this.placementCells = blocks;
        this.clearanceCells = sorted.stream()
                .filter(cell -> cell.kind() == PrefabCellKind.CLEARANCE)
                .toList();

        Map<String, Long> totals = new LinkedHashMap<>();
        for (PrefabCell block : blocks) {
            totals.merge(block.blockState(), 1L, Long::sum);
        }
        this.materialTotals = Collections.unmodifiableMap(totals);
    }

    public PrefabMetadata metadata() {
        return metadata;
    }

    public String id() {
        return metadata.id();
    }

    public PrefabDimensions dimensions() {
        return dimensions;
    }

    /** Anchor location in source-rotated schematic coordinates before normalization. */
    public PrefabPosition inferredSourceAnchor() {
        return inferredSourceAnchor;
    }

    public List<PrefabCell> cells() {
        return cells;
    }

    /** Bottom-to-top, then anchor-out ordering used by quote, preview, and commit. */
    public List<PrefabCell> placementCells() {
        return placementCells;
    }

    public List<PrefabCell> clearanceCells() {
        return clearanceCells;
    }

    public Optional<PrefabCell> cellAt(PrefabPosition position) {
        return Optional.ofNullable(cellsByPosition.get(position));
    }

    public Map<String, Long> materialTotals() {
        return materialTotals;
    }

    public String contentHash() {
        return contentHash;
    }

    /**
     * Applies a placement-time clockwise rotation around the inferred anchor.
     *
     * <p>At rotation zero, local +X grows right/east and local +Z grows inward/north, so the
     * canonical authored front faces south.</p>
     */
    public List<TransformedPrefabCell> transformedCells(int clockwiseQuarterTurns) {
        return transform(cells, clockwiseQuarterTurns);
    }

    /** Placement cells in the same deterministic bottom-to-top, anchor-out priority. */
    public List<TransformedPrefabCell> transformedPlacementCells(int clockwiseQuarterTurns) {
        return transform(placementCells, clockwiseQuarterTurns);
    }

    private List<TransformedPrefabCell> transform(
            List<PrefabCell> sourceCells,
            int clockwiseQuarterTurns
    ) {
        int turns = Math.floorMod(clockwiseQuarterTurns, 4);
        if (turns != 0 && !metadata.allowRotation()) {
            throw new IllegalArgumentException("Prefab metadata does not allow placement rotation");
        }
        List<TransformedPrefabCell> transformed = new ArrayList<>(sourceCells.size());
        for (PrefabCell cell : sourceCells) {
            int worldX = cell.position().x();
            int worldZ = -cell.position().z();
            for (int turn = 0; turn < turns; turn++) {
                int oldX = worldX;
                worldX = -worldZ;
                worldZ = oldX;
            }
            String blockState = cell.blockState();
            if (blockState != null) {
                blockState = BlockStateString.parse(blockState)
                        .rotateClockwise(turns)
                        .canonical();
            }
            transformed.add(new TransformedPrefabCell(
                    new PrefabPosition(worldX, cell.position().y(), worldZ),
                    cell.kind(),
                    blockState
            ));
        }
        return List.copyOf(transformed);
    }

    private static String requireHash(String hash) {
        Objects.requireNonNull(hash, "contentHash");
        if (!hash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Content hash must be lowercase SHA-256");
        }
        return hash;
    }
}
