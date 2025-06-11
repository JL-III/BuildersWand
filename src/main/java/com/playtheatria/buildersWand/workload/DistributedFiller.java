package com.playtheatria.buildersWand.workload;

import com.playtheatria.buildersWand.listeners.PlayerInteractListener;
import com.playtheatria.buildersWand.wand.WandData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class DistributedFiller {
    private final WorkloadRunnable workloadRunnable;

    public DistributedFiller(WorkloadRunnable workloadRunnable) {
        this.workloadRunnable = workloadRunnable;
    }

    public static List<Location> getBlockLocations(Block block, Player player, WandData wandData) {
        Location blockOrigin = block.getLocation();
        Location playerEyeLocation = player.getEyeLocation();
        List<Location> blockLocations = new ArrayList<>();

        int dimX = wandData.dimensions().x;
        int dimY = wandData.dimensions().y;
        int dimZ = wandData.dimensions().z;

        boolean playerXisLessThanOrigin = playerEyeLocation.getX() <= blockOrigin.x();
        boolean playerYisLessThanOrigin = playerEyeLocation.getY() <= blockOrigin.y();
        boolean playerZisLessThanOrigin = playerEyeLocation.getZ() <= blockOrigin.z();


        int destinationX = playerXisLessThanOrigin ? blockOrigin.getBlockX() - dimX : blockOrigin.getBlockX() + dimX;
        int destinationY = playerYisLessThanOrigin ? blockOrigin.getBlockY() - dimY : blockOrigin.getBlockY() + dimY;
        int destinationZ = playerZisLessThanOrigin ? blockOrigin.getBlockZ() - dimZ : blockOrigin.getBlockZ() + dimZ;

        int diffX = playerXisLessThanOrigin ? -1 : 1;
        int diffY = playerYisLessThanOrigin ? -1 : 1;
        int diffZ = playerZisLessThanOrigin ? -1 : 1;

        Bukkit.getConsoleSender().sendMessage("blockOrigin: " + blockOrigin.getBlockX() + ", " + blockOrigin.getBlockY() + ", " + blockOrigin.getBlockZ());
        Bukkit.getConsoleSender().sendMessage("destination: " + destinationX + ", " + destinationY + ", " + destinationZ);

        for (int x = blockOrigin.getBlockX(); playerXisLessThanOrigin ? x > destinationX : x < destinationX; x += diffX) {
            for (int z = blockOrigin.getBlockZ(); playerZisLessThanOrigin ? z > destinationZ : z < destinationZ; z += diffZ) {
                for (int y = blockOrigin.getBlockY(); playerYisLessThanOrigin ? y > destinationY: y < destinationY; y += diffY) {
                    blockLocations.add(new Location(blockOrigin.getWorld(), x, y, z));
                }
            }
        }
        return blockLocations;
    }

    public void fillCubeLocations(List<Location> locations, Player player) {
        for (Location location : locations) {
            Workload workload = new SetBlockWorkload(location.getBlock(), Material.STONE, player);
            workloadRunnable.addWorkload(workload);
        }
    }
}
