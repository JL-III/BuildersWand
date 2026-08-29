package com.playtheatria.buildersWand.wave;

import com.playtheatria.buildersWand.wand.PrintMaterial;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;

import java.util.Objects;

/** One geometry cell with its immutable, coordinate-selected material and exact target state. */
public record PlannedCell(Location location, PrintMaterial material, BlockData blockData) {

    public PlannedCell {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(blockData, "blockData");
    }
}
