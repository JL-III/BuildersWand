package com.playtheatria.buildersWand.prefab;

import com.playtheatria.buildersWand.form.Density;
import com.playtheatria.buildersWand.form.Dims;
import com.playtheatria.buildersWand.form.Form;
import com.playtheatria.buildersWand.wand.MaterialSelectionSnapshot;
import com.playtheatria.buildersWand.wand.PrintMaterial;
import com.playtheatria.buildersWand.wave.Plan;
import com.playtheatria.buildersWand.wave.PlanOptions;
import com.playtheatria.buildersWand.wave.PlannedCell;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.util.BlockVector;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Converts one catalog-validated prefab into the common immutable placement plan consumed by the
 * preview, quote, and wave pipeline.
 *
 * <p>The prefab definition remains offset-independent. This boundary resolves its authored state
 * strings against the running server version, translates every offset to an absolute world cell,
 * and opts the plan into strict prefab semantics. No live hotbar palette is consulted.</p>
 */
public final class PrefabPlanFactory {

    @FunctionalInterface
    interface BlockDataResolver {
        BlockData resolve(String canonicalBlockState);
    }

    @FunctionalInterface
    interface MaterialValidator {
        Optional<String> rejectionReason(Material material);
    }

    private final PrefabBlockPolicy blockPolicy;
    private final BlockDataResolver blockDataResolver;
    private final MaterialValidator materialValidator;

    /** Production factory using the conservative safe subset and the running Bukkit registry. */
    public PrefabPlanFactory() {
        this(PrefabBlockPolicy.conservativeDefaults(), Bukkit::createBlockData,
                PrefabPlanFactory::productionMaterialRejection);
    }

    /** Package-private seam for registry-independent unit tests. */
    PrefabPlanFactory(
            PrefabBlockPolicy blockPolicy,
            BlockDataResolver blockDataResolver,
            MaterialValidator materialValidator
    ) {
        this.blockPolicy = Objects.requireNonNull(blockPolicy, "blockPolicy");
        this.blockDataResolver = Objects.requireNonNull(blockDataResolver, "blockDataResolver");
        this.materialValidator = Objects.requireNonNull(materialValidator, "materialValidator");
    }

    /**
     * Creates an authored prefab plan at an absolute block anchor.
     *
     * @param definition catalog-validated immutable prefab definition
     * @param world destination world
     * @param anchor absolute lower-left-front block anchor
     * @param clockwiseQuarterTurns placement rotation; normalized modulo four
     * @param requirePlacementLogging whether unavailable placement attribution blocks this plan
     * @param confirmationSeconds expiry for the mandatory prefab confirmation
     * @throws IllegalArgumentException if activation Uses cannot fit the shared integer counter,
     *                                  a state is unsafe on this server, or coordinate translation
     *                                  overflows the block-coordinate range
     */
    public Plan create(
            PrefabDefinition definition,
            World world,
            BlockVector anchor,
            int clockwiseQuarterTurns,
            boolean requirePlacementLogging,
            int confirmationSeconds
    ) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(anchor, "anchor");

        int activationUses = activationUsesAsInt(definition.metadata().activationUses());
        int turns = Math.floorMod(clockwiseQuarterTurns, 4);
        BlockVector stableAnchor = blockAnchor(anchor);

        List<PlannedCell> targets = new ArrayList<>(definition.placementCells().size());
        for (TransformedPrefabCell cell : definition.transformedPlacementCells(turns)) {
            BlockVector absolute = translate(stableAnchor, cell.anchorOffset());
            BlockData blockData = resolveSafeBlockData(cell.blockState());
            PrintMaterial material = PrintMaterial.block(blockData.getMaterial());
            targets.add(new PlannedCell(
                    absolute.toLocation(world),
                    material,
                    blockData
            ));
        }
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("Prefab must contain at least one placement cell");
        }

        List<BlockVector> clearance = definition.transformedCells(turns).stream()
                .filter(cell -> cell.kind() == PrefabCellKind.CLEARANCE)
                .map(cell -> translate(stableAnchor, cell.anchorOffset()))
                .toList();

        PlanOptions options = PlanOptions.prefab(
                definition.metadata().name(),
                contentBinding(definition),
                activationUses,
                clearance,
                requirePlacementLogging,
                confirmationSeconds
        );

        // Authored prefab plans carry their material on every PlannedCell and explicitly bypass
        // live-palette validation. Plan retains a non-empty selection for compatibility callers;
        // a representative target avoids imposing the hotbar palette's nine-entry limit on
        // arbitrary prefabs.
        MaterialSelectionSnapshot representative = MaterialSelectionSnapshot.single(
                targets.getFirst().material()
        );
        return new Plan(
                world,
                Form.BOX,
                rotatedDimensions(definition.dimensions(), turns),
                stableAnchor,
                stableAnchor,
                targets,
                representative,
                Density.SOLID,
                options
        );
    }

    static int activationUsesAsInt(long activationUses) {
        if (activationUses < 0 || activationUses > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Prefab activation Uses must be between 0 and " + Integer.MAX_VALUE
            );
        }
        return (int) activationUses;
    }

    static Dims rotatedDimensions(PrefabDimensions dimensions, int clockwiseQuarterTurns) {
        Objects.requireNonNull(dimensions, "dimensions");
        boolean swapsHorizontalAxes = Math.floorMod(clockwiseQuarterTurns, 2) == 1;
        int width = swapsHorizontalAxes ? dimensions.depth() : dimensions.width();
        int depth = swapsHorizontalAxes ? dimensions.width() : dimensions.depth();
        return new Dims(width, dimensions.height(), depth);
    }

    static BlockVector translate(BlockVector anchor, PrefabPosition offset) {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(offset, "offset");
        return new BlockVector(
                Math.addExact(anchor.getBlockX(), offset.x()),
                Math.addExact(anchor.getBlockY(), offset.y()),
                Math.addExact(anchor.getBlockZ(), offset.z())
        );
    }

    static String contentBinding(PrefabDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        return "prefab:" + definition.id()
                + ":v" + definition.metadata().version()
                + ":" + definition.contentHash();
    }

    private BlockData resolveSafeBlockData(String canonicalBlockState) {
        Objects.requireNonNull(canonicalBlockState, "canonicalBlockState");
        Optional<String> policyRejection = blockPolicy.rejectionReason(canonicalBlockState);
        if (policyRejection.isPresent()) {
            throw unsafeState(canonicalBlockState, policyRejection.get());
        }

        final BlockData blockData;
        try {
            blockData = blockDataResolver.resolve(canonicalBlockState);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw unsafeState(canonicalBlockState,
                    "state is not recognized by this server version", exception);
        }
        if (blockData == null) {
            throw unsafeState(canonicalBlockState, "state resolver returned no block data");
        }

        Material material = Objects.requireNonNull(
                blockData.getMaterial(),
                "resolved block material"
        );
        if (material == Material.WATER
                || material == Material.LAVA
                || material == Material.BUBBLE_COLUMN
                || (blockData instanceof Waterlogged waterlogged && waterlogged.isWaterlogged())) {
            throw unsafeState(canonicalBlockState, "fluids are not supported in prefab placement");
        }
        Optional<String> materialRejection = materialValidator.rejectionReason(material);
        if (materialRejection.isPresent()) {
            throw unsafeState(canonicalBlockState, materialRejection.get());
        }
        return blockData;
    }

    private static Optional<String> productionMaterialRejection(Material material) {
        if (!material.isBlock() || material.isAir()) {
            return Optional.of(material.name() + " is not a placeable block");
        }
        if (!material.isItem() || material.getMaxStackSize() <= 0) {
            return Optional.of(material.name() + " has no one-item feedstock mapping");
        }
        return Optional.empty();
    }

    private static BlockVector blockAnchor(BlockVector anchor) {
        // Rebuild instead of retaining the caller's mutable Vector instance.
        return new BlockVector(anchor.getBlockX(), anchor.getBlockY(), anchor.getBlockZ());
    }

    private static IllegalArgumentException unsafeState(String state, String reason) {
        return new IllegalArgumentException("Unsafe prefab block state '" + state + "': " + reason);
    }

    private static IllegalArgumentException unsafeState(
            String state,
            String reason,
            RuntimeException cause
    ) {
        return new IllegalArgumentException(
                "Unsafe prefab block state '" + state + "': " + reason,
                cause
        );
    }
}
