package com.playtheatria.buildersWand.workload;

import com.playtheatria.buildersWand.wand.WandData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
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

    public void fillCubeBlockLocations(Block block, WandData wandData) {
        Location loc = block.getLocation();
        World world = loc.getWorld();

        double minX = Math.ceil(loc.getBlockX() - ((double) wandData.dimensions().x) / 2);
        double minY = loc.getBlockY() + 1;
        double minZ = Math.ceil(loc.getBlockZ() - ((double) wandData.dimensions().z) / 2);

        double maxX = Math.floor(loc.getBlockX() + ((double) wandData.dimensions().x) / 2);
        double maxY = loc.getBlockY() + wandData.dimensions().y;
        double maxZ = Math.floor(loc.getBlockZ() + ((double) wandData.dimensions().z) / 2);

        if (wandData.dimensions().x % 2 == 0) {
            maxX -= 1;
        }

        if (wandData.dimensions().z % 2 == 0) {
            maxZ -= 1;
        }

        for (double x = minX; x <= maxX; x++) {
            for (double y = minY; y <= maxY; y++) {
                for (double z = minZ; z <= maxZ; z++) {
                    Workload workload = new Workload(world.getUID(), (int) x, (int) y, (int) z, Material.STONE);
                    workloadRunnable.addWorkload(workload);
                    Bukkit.getOnlinePlayers().forEach(player -> {
                        player.sendMessage("Adding workload to workload runnable");
                    });
                }
            }
        }
    }
}
