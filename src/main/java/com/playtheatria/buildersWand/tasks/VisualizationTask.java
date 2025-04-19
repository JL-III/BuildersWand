package com.playtheatria.buildersWand.tasks;

import com.google.common.collect.Lists;
import com.playtheatria.buildersWand.utils.Err;
import com.playtheatria.buildersWand.utils.Ok;
import com.playtheatria.buildersWand.wand.ParsedWandData;
import com.playtheatria.buildersWand.wand.Wand;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public class VisualizationTask {

    static List<Material> ignoredMaterials = List.of(
            Material.AIR,
            Material.CAVE_AIR,
            Material.VOID_AIR,
            Material.WATER,
            Material.LAVA,
            Material.BUBBLE_COLUMN,
            Material.FIRE,
            Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE
    );

    public static int run(Plugin plugin) {
        BukkitRunnable bukkitRunnable = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    switch (Wand.getWandData(player.getInventory().getItemInMainHand())) {
                        case Ok<ParsedWandData, Exception> ok -> {
                            Block block = player.getTargetBlock(null, 10);
                            if (ignoredMaterials.contains(block.getType())) continue;
                            playVisualEffect(block.getLocation());
                            player.sendMessage("Result: " + ok.value().mode() + " " + ok.value().dimensions().x + "x" + ok.value().dimensions().y);
                        }
                        case Err<ParsedWandData, Exception> err -> {}
                    }
                }
            }
        };
        return bukkitRunnable.runTaskTimer(plugin, 20, 20).getTaskId();
    }

    public static void playVisualEffect(Location location) {
        for (Location locationIterate : getHollowCube(location, 0.2)) {
            location.getWorld().spawnParticle(Particle.ENCHANT, locationIterate, 1, 0.0,0.0,0.0, 0.01);
            location.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, locationIterate, 1, 0.0,0.0,0.0, 0.01);
        }
    }

    public static List<Location> getHollowCube(Location loc, double particleDistance) {
        List<Location> result = Lists.newArrayList();
        World world = loc.getWorld();
        double minX = loc.getBlockX();
        double minY = loc.getBlockY();
        double minZ = loc.getBlockZ();
        double maxX = loc.getBlockX()+1;
        double maxY = loc.getBlockY()+1;
        double maxZ = loc.getBlockZ()+1;

        for (double x = minX; x <= maxX; x = Math.round((x + particleDistance) * 1e2) / 1e2) {
            for (double y = minY; y <= maxY; y = Math.round((y + particleDistance) * 1e2) / 1e2) {
                for (double z = minZ; z <= maxZ; z = Math.round((z + particleDistance) * 1e2) / 1e2) {
                    int components = 0;
                    if (x == minX || x == maxX) components++;
                    if (y == minY || y == maxY) components++;
                    if (z == minZ || z == maxZ) components++;
                    if (components >= 2) {
                        result.add(new Location(world, x, y, z));
                    }
                }
            }
        }
        return result;
    }
}
