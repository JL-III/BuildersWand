package com.playtheatria.buildersWand.workload;

import com.google.common.collect.Lists;
import com.playtheatria.buildersWand.utils.LocationObject;
import com.playtheatria.buildersWand.wand.WandData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.List;

public class DistributedFiller {
    private final WorkloadRunnable workloadRunnable;

    public DistributedFiller(WorkloadRunnable workloadRunnable) {
        this.workloadRunnable = workloadRunnable;
    }

    public LocationObject getLocations(Block block, WandData wandData) {
        Location location = block.getLocation();
        List<Location> locations = Lists.newArrayList();

        double minX = Math.ceil(location.getBlockX() - ((double) wandData.dimensions().x) / 2);
        double minY = location.getBlockY() + 1;
        double minZ = Math.ceil(location.getBlockZ() - ((double) wandData.dimensions().z) / 2);

        double maxX = Math.floor(location.getBlockX() + ((double) wandData.dimensions().x) / 2);
        double maxY = location.getBlockY() + wandData.dimensions().y;
        double maxZ = Math.floor(location.getBlockZ() + ((double) wandData.dimensions().z) / 2);

        if (wandData.dimensions().x % 2 == 0) {
            maxX -= 1;
        }
        if (wandData.dimensions().z % 2 == 0) {
            maxZ -= 1;
        }

        for (double x = minX; x <= maxX; x++) {
            for (double y = minY; y <= maxY; y++) {
                for (double z = minZ; z <= maxZ; z++) {
                    locations.add(new Location(location.getWorld(), x, y, z));
                }
            }
        }
        Bukkit.getConsoleSender().sendMessage("DistributedFiller: Created " + locations.size() + " locations for block at " + location);
        return new LocationObject(locations, minX, minY, minZ, maxX, maxY, maxZ);
    }

    public void fillCubeBlockLocations(Block block, WandData wandData) {
        for (Location location : getLocations(block, wandData).getLocationList()) {
            Workload workload = new Workload(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ(), Material.STONE);
            workloadRunnable.addWorkload(workload);
            Bukkit.getOnlinePlayers().forEach(player -> {
                player.sendMessage("Adding workload to workload runnable");
            });
        }
    }

    public void fillHollowCubeBlockLocations(Block block, WandData wandData) {
        LocationObject locationObject = getLocations(block, wandData);
        List<Location> locations = locationObject.getLocationList().stream().filter(
                loc -> {
                    double x = loc.getBlockX();
                    double y = loc.getBlockY();
                    double z = loc.getBlockZ();

                    return (x == locationObject.getMinX() || x == locationObject.getMaxX()) ||
                            (y == locationObject.getMinY() || y == locationObject.getMaxY()) ||
                            (z == locationObject.getMinZ() || z == locationObject.getMaxZ());
                }
        ).toList();

        Bukkit.getConsoleSender().sendMessage("DistributedFiller: Filtered list down to " + locations.size() + " hollow locations for block at " + block.getLocation());
        for (Location location : locations) {
            Workload workload = new Workload(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ(), Material.STONE);
            workloadRunnable.addWorkload(workload);
            Bukkit.getOnlinePlayers().forEach(player -> {
                player.sendMessage("Adding workload to workload runnable");
            });
        }
    }

    public void fillWireCubeBlockLocations(Block block, WandData wandData) {
        LocationObject locationObject = getLocations(block, wandData);
        List<Location> locations = locationObject.getLocationList().stream().filter(
                loc -> {
                    double x = loc.getBlockX();
                    double y = loc.getBlockY();
                    double z = loc.getBlockZ();

                    double minX = locationObject.getMinX();
                    double minY = locationObject.getMinY();
                    double minZ = locationObject.getMinZ();
                    double maxX = locationObject.getMaxX();
                    double maxY = locationObject.getMaxY();
                    double maxZ = locationObject.getMaxZ();

                    return (x == minX && y == minY) ||
                            (x == maxX && y == maxY) ||
                            (x == minX && z == minZ) ||
                            (x == minX && z == maxZ) ||
                            (x == maxX && z == minZ) ||
                            (x == maxX && z == maxZ) ||
                            (y == minY && z == minZ) ||
                            (y == maxY && z == minZ) ||
                            (y == minY && z == maxZ) ||
                            (y == minY && x == maxX) ||
                            (y == maxY && x == minX) ||
                            (y == maxY && z == maxZ);
                }
        ).toList();

        Bukkit.getConsoleSender().sendMessage("DistributedFiller: Filtered list down to " + locations.size() + " wire locations for block at " + block.getLocation());

        for (Location location : locations) {
            Workload workload = new Workload(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ(), Material.STONE);
            workloadRunnable.addWorkload(workload);
            Bukkit.getOnlinePlayers().forEach(player -> {
                player.sendMessage("Adding workload to workload runnable");
            });
        }
    }
}
