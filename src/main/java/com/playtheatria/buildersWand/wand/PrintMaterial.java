package com.playtheatria.buildersWand.wand;

import org.bukkit.Material;

import java.util.Objects;

/**
 * A player's off-hand material choice, separated into the item that selects/pays for a print
 * and the block the wave actually places. Most blocks use the same material for both. A water
 * bucket is the deliberate exception: it selects source water and is a reusable catalyst.
 */
public record PrintMaterial(Material sourceItem, Material placedBlock, boolean reusable) {

    public PrintMaterial {
        Objects.requireNonNull(sourceItem, "sourceItem");
        Objects.requireNonNull(placedBlock, "placedBlock");
    }

    /** A normal one-item-per-cell block material. */
    public static PrintMaterial block(Material material) {
        return new PrintMaterial(material, material, false);
    }

    /** One retained water bucket selects any number of source-water cells in this print. */
    public static PrintMaterial water() {
        return new PrintMaterial(Material.WATER_BUCKET, Material.WATER, true);
    }

    public boolean isWater() {
        return placedBlock == Material.WATER;
    }

    /** Visible block-display proxy; vanilla liquid block models do not render in displays. */
    public Material previewBlock() {
        return isWater() ? Material.LIGHT_BLUE_STAINED_GLASS : placedBlock;
    }
}
