package com.playtheatria.buildersWand.wave;

import com.playtheatria.buildersWand.form.Dims;
import com.playtheatria.buildersWand.form.Form;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.BlockVector;

import java.util.List;

/**
 * A fully-derived print (design §5.5): the {@link com.playtheatria.buildersWand.form.Expansion}
 * cell offsets translated to absolute world locations, in emission order.
 */
public record Plan(World world, Form form, Dims dims, BlockVector effectiveAnchor,
                   List<Location> cells, Material material) {
}
