package com.playtheatria.buildersWand.utils;

import com.google.common.collect.Lists;
import com.playtheatria.buildersWand.wand.WandData;
import org.bukkit.Location;

import java.util.List;

public class Cube {

    public static List<Location> calculateLocations(Location location, WandData wandData) {
        int radius = wandData.dimensions().x - 1;
        int bx = location.getBlockX();
        int by = location.getBlockY();
        int bz = location.getBlockZ();
        int positiveXBorder = bx + radius;
        int negativeXBorder = bx - radius;
        int positiveYBorder = by + radius;
        int negativeYBorder = by - radius;
        int positiveZBorder = bz + radius;
        int negativeZBorder = bz - radius;

        List<Location> locations = Lists.newArrayList();
        for (int x = negativeXBorder; x <= positiveXBorder; x++) {
            for (int y = negativeYBorder; y <= positiveYBorder; y++) {
                for (int z = negativeZBorder; z <= positiveZBorder; z++) {


                    if (
                        //top conditional creates the top portion of the cube.
                            (((y == negativeYBorder) || (y == positiveYBorder)) && (((x == negativeXBorder) || (x == positiveXBorder)) && ((bz - z) % 5 == 0) || ((z == negativeZBorder) || (z == positiveZBorder)) && ((bx - x) % 5 == 0)))
                                    //bottom conditional creates the pillars
                                    || (((z == negativeZBorder) || (z == positiveZBorder)) && ((x == negativeXBorder) || (x == positiveXBorder)) && (by - y) % 5 == 0)
                    ) {
                        locations.add(new Location(location.getWorld(), x, y, z));
                    }

                }
            }
        }
        return locations;
    }
}
