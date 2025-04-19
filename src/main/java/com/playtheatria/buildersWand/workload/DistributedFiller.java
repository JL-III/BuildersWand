package com.playtheatria.buildersWand.workload;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

public class DistributedFiller {
    private final WorkloadRunnable workloadRunnable;

    public DistributedFiller(WorkloadRunnable workloadRunnable) {
        this.workloadRunnable = workloadRunnable;
    }

    public void fill(Location cornerA, Location cornerB, Material material) {
        if (cornerA.getWorld() != cornerB.getWorld()) return;
        if (cornerA.getWorld() == null) return;

        BoundingBox box = BoundingBox.of(cornerA.getBlock(), cornerB.getBlock());
        Vector max = box.getMax();
        Vector min = box.getMin();

        World world = cornerA.getWorld();

        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    Workload workload = new Workload(world.getUID(), x, y, z, material);
                    this.workloadRunnable.addWorkload(workload);
                }
            }
        }
    }
}
