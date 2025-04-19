package com.playtheatria.buildersWand.tasks;

import com.destroystokyo.paper.ParticleBuilder;
import com.google.common.collect.Lists;
import com.playtheatria.buildersWand.utils.Err;
import com.playtheatria.buildersWand.utils.Ok;
import com.playtheatria.buildersWand.wand.WandData;
import com.playtheatria.buildersWand.wand.Wand;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public class VisualizationTask {

    static List<Material> ignoredMaterials = List.of(
            Material.AIR,
            Material.CAVE_AIR,
            Material.VOID_AIR,
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

                    ItemStack item = player.getInventory().getItemInMainHand();
                    if (!item.hasItemMeta() || !item.getItemMeta().hasLore()) continue;

                    switch (Wand.getWandData(player.getInventory().getItemInMainHand())) {
                        case Ok<WandData, Exception> ok -> {
                            Block block = player.getTargetBlock(null, 15);
                            if (ignoredMaterials.contains(block.getType())) continue;
                            playVisualEffect(block.getLocation(), ok.value());
                        }
                        case Err<WandData, Exception> err -> {}
                    }
                }
            }
        };
        return bukkitRunnable.runTaskTimer(plugin, 20, 20).getTaskId();
    }

    public static void playVisualEffect(Location location, WandData wandData) {
        for (Location locationIterate : getHollowCube(location, wandData,0.2)) {
            location.getWorld().spawnParticle(Particle.DUST, locationIterate, 1, 0.0,0.0,0.0, new Particle.DustOptions(Color.LIME, 1));
        }
    }

    public static List<Location> getHollowCube(Location loc, WandData wandData, double particleDistance) {
        List<Location> result = Lists.newArrayList();
        World world = loc.getWorld();

        double minX = Math.ceil(loc.getBlockX() - ((double) wandData.dimensions().x) / 2);
        double minY = loc.getBlockY() + 1;
        double minZ = Math.ceil(loc.getBlockZ() - ((double) wandData.dimensions().z) / 2);

        double maxX = Math.ceil(loc.getBlockX() + ((double) wandData.dimensions().x) / 2);
        double maxY = loc.getBlockY() + wandData.dimensions().y + 1;
        double maxZ = Math.ceil(loc.getBlockZ() + ((double) wandData.dimensions().z) / 2);

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
