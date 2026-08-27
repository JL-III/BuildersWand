package com.playtheatria.buildersWand.wave;

import com.playtheatria.buildersWand.form.Dims;
import com.playtheatria.buildersWand.form.Form;
import com.playtheatria.buildersWand.wand.PrintMaterial;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.BlockVector;

import java.util.List;

/**
 * A fully-derived print (design §5.5): the {@link com.playtheatria.buildersWand.form.Expansion}
 * cell offsets translated to absolute world locations, in emission order. {@code material}
 * separates the selecting item, placed block, and cost policy; {@code blockData} is the exact
 * target state. The ghost may use a visible proxy for target states such as water.
 */
public record Plan(World world, Form form, Dims dims, BlockVector effectiveAnchor,
                   List<Location> cells, PrintMaterial material, BlockData blockData) {
}
