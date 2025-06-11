package com.playtheatria.buildersWand.tasks;

import com.google.common.collect.Lists;
import com.playtheatria.buildersWand.utils.BoundingBox;
import com.playtheatria.buildersWand.wand.Wand;
import com.playtheatria.buildersWand.wand.WandData;
import com.playtheatria.buildersWand.workload.DistributedFiller;
import com.playtheatria.jliii.generalutils.result.Err;
import com.playtheatria.jliii.generalutils.result.Ok;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
                            Block block = player.getTargetBlock(null, 16);
                            if (ignoredMaterials.contains(block.getType())) continue;
                            playVisualEffect(block, player, ok.value());
                        }
                        case Err<WandData, Exception> err -> {
                            Bukkit.getConsoleSender().sendMessage(
                                    Component.text("Error getting wand data: " + err.error().getMessage())
                                            .color(NamedTextColor.DARK_RED)
                            );
                        }
                    }
                }
            }
        };
        return bukkitRunnable.runTaskTimer(plugin, 20, 30).getTaskId();
    }

    public static void playVisualEffect(Block block, Player player, WandData wandData) {
        List<Location> locations =  DistributedFiller.getBlockLocations(block, player, wandData);
        BoundingBox boundingBox = new BoundingBox(locations);
        BoundingBox.drawBoundingBoxOutline(boundingBox.getMinCorner(), boundingBox.getMaxCorner(), Particle.DUST, 0.5, player);
    }

    public static List<Location> getCubeParticleLocations(Block block, WandData wandData, double particleDistance) {
        List<Location> particleLocations = Lists.newArrayList();
        Location loc = block.getLocation();
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
                    if ((x == minX || x == maxX) && (y == minY || y == maxY) && (z == minZ || z == maxZ)) {
                        particleLocations.add(new Location(world, x, y, z));
                    }
                }
            }
        }
        return particleLocations;
    }
}
