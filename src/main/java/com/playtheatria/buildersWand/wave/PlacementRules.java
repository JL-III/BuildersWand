package com.playtheatria.buildersWand.wave;

import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/** Shared world-cell classification for the preview and authoritative commit. */
public final class PlacementRules {

    private PlacementRules() {
    }

    /** Exact state match, notably distinguishing source water (level 0) from flowing water. */
    public static boolean isAlreadyBuilt(Block block, BlockData target) {
        return block.getBlockData().getAsString().equals(target.getAsString());
    }

    /** A cell is printable only when it does not already match and Bukkit marks it replaceable. */
    public static boolean isPrintable(Block block, BlockData target) {
        return !isAlreadyBuilt(block, target) && block.isReplaceable();
    }
}
