package com.playtheatria.buildersWand.prefab;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Validates and normalizes parsed schematic data into an immutable runtime definition. */
public final class PrefabImporter {

    private final PrefabBlockPolicy blockPolicy;
    private final int maxPlacementCells;
    private final int maxTrimmedVolume;
    private final int maxAxisSpan;
    private final int maxClearanceCells;
    private final long maxActivationUses;

    public PrefabImporter(
            PrefabBlockPolicy blockPolicy,
            int maxPlacementCells,
            int maxTrimmedVolume
    ) {
        this(blockPolicy, maxPlacementCells, maxTrimmedVolume,
                Integer.MAX_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE);
    }

    public PrefabImporter(
            PrefabBlockPolicy blockPolicy,
            int maxPlacementCells,
            int maxTrimmedVolume,
            int maxAxisSpan,
            int maxClearanceCells
    ) {
        this(blockPolicy, maxPlacementCells, maxTrimmedVolume, maxAxisSpan,
                maxClearanceCells, Long.MAX_VALUE);
    }

    public PrefabImporter(
            PrefabBlockPolicy blockPolicy,
            int maxPlacementCells,
            int maxTrimmedVolume,
            int maxAxisSpan,
            int maxClearanceCells,
            long maxActivationUses
    ) {
        this.blockPolicy = Objects.requireNonNull(blockPolicy, "blockPolicy");
        if (maxPlacementCells <= 0 || maxTrimmedVolume <= 0
                || maxAxisSpan <= 0 || maxClearanceCells <= 0) {
            throw new IllegalArgumentException("Prefab import limits must be positive");
        }
        if (maxActivationUses < 0L) {
            throw new IllegalArgumentException("Prefab activation Uses limit cannot be negative");
        }
        this.maxPlacementCells = maxPlacementCells;
        this.maxTrimmedVolume = maxTrimmedVolume;
        this.maxAxisSpan = maxAxisSpan;
        this.maxClearanceCells = maxClearanceCells;
        this.maxActivationUses = maxActivationUses;
    }

    public PrefabDefinition importPrefab(PrefabMetadata metadata, RawPrefabSchematic raw)
            throws PrefabValidationException {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(raw, "raw");
        List<String> issues = new ArrayList<>();
        if (metadata.activationUses() > maxActivationUses) {
            issues.add("Activation Uses " + metadata.activationUses()
                    + " exceed the wand maximum " + maxActivationUses);
        }
        if (raw.blockEntityCount() > 0) {
            issues.add("Schematic contains " + raw.blockEntityCount()
                    + " block entities; prefab v1 accepts none");
        }
        if (raw.entityCount() > 0) {
            issues.add("Schematic contains " + raw.entityCount()
                    + " entities; prefab v1 accepts none");
        }

        RotatedVolume rotated;
        try {
            rotated = rotate(raw, metadata.sourceQuarterTurns());
        } catch (IllegalArgumentException exception) {
            issues.add("Could not rotate block state safely: " + exception.getMessage());
            throw new PrefabValidationException(issues);
        }

        Bounds bounds = findSolidBounds(rotated);
        if (bounds == null) {
            issues.add("Schematic has no placeable non-air blocks after trimming");
            throw new PrefabValidationException(issues);
        }
        PrefabDimensions dimensions = new PrefabDimensions(
                bounds.maxX - bounds.minX + 1,
                bounds.maxY - bounds.minY + 1,
                bounds.maxZ - bounds.minZ + 1
        );
        if (dimensions.volume() > maxTrimmedVolume) {
            issues.add("Trimmed prefab volume " + dimensions.volume()
                    + " exceeds limit " + maxTrimmedVolume);
        }
        if (dimensions.width() > maxAxisSpan
                || dimensions.height() > maxAxisSpan
                || dimensions.depth() > maxAxisSpan) {
            issues.add("Trimmed prefab dimensions " + dimensions.width() + "x"
                    + dimensions.height() + "x" + dimensions.depth()
                    + " exceed axis-span limit " + maxAxisSpan);
        }

        List<PrefabCell> cells = new ArrayList<>(Math.toIntExact(dimensions.volume()));
        int placementCount = 0;
        int clearanceCount = 0;
        for (int localY = 0; localY < dimensions.height(); localY++) {
            for (int localZ = 0; localZ < dimensions.depth(); localZ++) {
                for (int localX = 0; localX < dimensions.width(); localX++) {
                    int sourceX = bounds.minX + localX;
                    int sourceY = bounds.minY + localY;
                    // Local +Z means inward/north; canonical source +Z points toward the south face.
                    int sourceZ = bounds.maxZ - localZ;
                    BlockStateString state = rotated.stateAt(sourceX, sourceY, sourceZ);
                    PrefabPosition position = new PrefabPosition(localX, localY, localZ);
                    if (state.isAir()) {
                        clearanceCount++;
                        cells.add(PrefabCell.clearance(position));
                    } else if (state.isStructureVoid()) {
                        cells.add(PrefabCell.ignored(position));
                    } else {
                        placementCount++;
                        Optional<String> rejected = blockPolicy.rejectionReason(state.canonical());
                        rejected.ifPresent(reason -> issues.add(
                                state.id() + " at " + position + ": " + reason
                        ));
                        cells.add(PrefabCell.block(position, state.canonical()));
                    }
                }
            }
        }
        if (placementCount > maxPlacementCells) {
            issues.add("Prefab has " + placementCount
                    + " placeable cells; limit is " + maxPlacementCells);
        }
        if (clearanceCount > maxClearanceCells) {
            issues.add("Prefab has " + clearanceCount
                    + " clearance cells; limit is " + maxClearanceCells);
        }
        if (!issues.isEmpty()) {
            throw new PrefabValidationException(issues.stream().distinct().toList());
        }

        PrefabPosition sourceAnchor = new PrefabPosition(bounds.minX, bounds.minY, bounds.maxZ);
        String hash = PrefabContentHasher.hash(metadata, dimensions, cells);
        return new PrefabDefinition(metadata, dimensions, sourceAnchor, cells, hash);
    }

    private static RotatedVolume rotate(RawPrefabSchematic raw, int turns) {
        PrefabDimensions source = raw.dimensions();
        int normalized = Math.floorMod(turns, 4);
        int width = normalized % 2 == 0 ? source.width() : source.depth();
        int depth = normalized % 2 == 0 ? source.depth() : source.width();
        String[] states = new String[Math.toIntExact((long) width * source.height() * depth)];
        for (int y = 0; y < source.height(); y++) {
            for (int z = 0; z < source.depth(); z++) {
                for (int x = 0; x < source.width(); x++) {
                    int rotatedX;
                    int rotatedZ;
                    switch (normalized) {
                        case 0 -> {
                            rotatedX = x;
                            rotatedZ = z;
                        }
                        case 1 -> {
                            rotatedX = source.depth() - 1 - z;
                            rotatedZ = x;
                        }
                        case 2 -> {
                            rotatedX = source.width() - 1 - x;
                            rotatedZ = source.depth() - 1 - z;
                        }
                        default -> {
                            rotatedX = z;
                            rotatedZ = source.width() - 1 - x;
                        }
                    }
                    BlockStateString state = BlockStateString.parse(raw.blockStateAt(x, y, z))
                            .rotateClockwise(normalized);
                    int index = rotatedX + rotatedZ * width + y * width * depth;
                    states[index] = state.canonical();
                }
            }
        }
        return new RotatedVolume(new PrefabDimensions(width, source.height(), depth), states);
    }

    private static Bounds findSolidBounds(RotatedVolume volume) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int y = 0; y < volume.dimensions.height(); y++) {
            for (int z = 0; z < volume.dimensions.depth(); z++) {
                for (int x = 0; x < volume.dimensions.width(); x++) {
                    BlockStateString state = volume.stateAt(x, y, z);
                    if (state.isAir() || state.isStructureVoid()) {
                        continue;
                    }
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    minZ = Math.min(minZ, z);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                    maxZ = Math.max(maxZ, z);
                }
            }
        }
        return minX == Integer.MAX_VALUE ? null : new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }

    private record RotatedVolume(PrefabDimensions dimensions, String[] states) {
        private BlockStateString stateAt(int x, int y, int z) {
            int index = x + z * dimensions.width() + y * dimensions.width() * dimensions.depth();
            return BlockStateString.parse(states[index]);
        }
    }
}
