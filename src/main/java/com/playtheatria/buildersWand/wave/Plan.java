package com.playtheatria.buildersWand.wave;

import com.playtheatria.buildersWand.form.Dims;
import com.playtheatria.buildersWand.form.Density;
import com.playtheatria.buildersWand.form.Form;
import com.playtheatria.buildersWand.wand.MaterialSelectionSnapshot;
import com.playtheatria.buildersWand.wand.PrintMaterial;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.BlockVector;

import java.util.List;
import java.util.Objects;

/**
 * A fully-derived print (design §5.5): the {@link com.playtheatria.buildersWand.form.Expansion}
 * cell offsets translated to immutable per-cell targets in emission order. Material assignment
 * happens before occupied-cell or affordability filtering, which keeps palette textures stable
 * when a print is resumed. The ghost may use a visible proxy for targets such as water.
 */
public record Plan(World world, Form form, Dims dims, BlockVector effectiveAnchor,
                   BlockVector interactionAnchor,
                   List<PlannedCell> targets, MaterialSelectionSnapshot materialSelection,
                   Density density, PlanOptions options) {

    /** Compatibility constructor for ordinary Shell plans. */
    public Plan(World world, Form form, Dims dims, BlockVector effectiveAnchor,
                List<PlannedCell> targets, MaterialSelectionSnapshot materialSelection) {
        this(world, form, dims, effectiveAnchor, effectiveAnchor, targets, materialSelection,
                Density.DEFAULT, PlanOptions.ordinary(form));
    }

    /** Ordinary plan with an explicit Shell/Solid preference. */
    public Plan(World world, Form form, Dims dims, BlockVector effectiveAnchor,
                List<PlannedCell> targets, MaterialSelectionSnapshot materialSelection,
                Density density) {
        this(world, form, dims, effectiveAnchor, effectiveAnchor, targets, materialSelection,
                density, PlanOptions.ordinary(form));
    }

    /** Ordinary plan whose emitted origin differs from the player's clicked anchor. */
    public Plan(World world, Form form, Dims dims, BlockVector effectiveAnchor,
                BlockVector interactionAnchor, List<PlannedCell> targets,
                MaterialSelectionSnapshot materialSelection, Density density) {
        this(world, form, dims, effectiveAnchor, interactionAnchor, targets,
                materialSelection, density, PlanOptions.ordinary(form));
    }

    public Plan {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(form, "form");
        Objects.requireNonNull(dims, "dims");
        Objects.requireNonNull(effectiveAnchor, "effectiveAnchor");
        Objects.requireNonNull(interactionAnchor, "interactionAnchor");
        targets = List.copyOf(targets);
        Objects.requireNonNull(materialSelection, "materialSelection");
        Objects.requireNonNull(density, "density");
        Objects.requireNonNull(options, "options");
    }

    /** Location-only compatibility view for geometry and protection callers. */
    public List<Location> cells() {
        return targets.stream().map(PlannedCell::location).toList();
    }

    /** Compatibility accessor for callers that only understand a single-material plan. */
    public PrintMaterial material() {
        if (!materialSelection.singleMaterial()) {
            throw new IllegalStateException("a mixed-material plan has no single material");
        }
        return materialSelection.entries().getFirst();
    }

    /** Compatibility accessor for callers that only understand a single-material plan. */
    public BlockData blockData() {
        if (!materialSelection.singleMaterial()) {
            throw new IllegalStateException("a mixed-material plan has no single block state");
        }
        if (targets.isEmpty()) {
            return material().placedBlock().createBlockData();
        }
        return targets.getFirst().blockData();
    }
}
