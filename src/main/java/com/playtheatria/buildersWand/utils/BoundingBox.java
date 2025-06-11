package com.playtheatria.buildersWand.utils;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

public class BoundingBox {
    private final Location minCorner;
    private final Location maxCorner;
    private final World world;

    public BoundingBox(List<Location> locations) {
        if (locations == null || locations.isEmpty()) {
            throw new IllegalArgumentException("Location list cannot be null or empty.");
        }
        this.world = locations.getFirst().getWorld();
        this.minCorner = getMin(locations);
        this.maxCorner = getMax(locations);
        Bukkit.getConsoleSender().sendMessage(
                "BoundingBox created with min corner: " + minCorner + " and max corner: " + maxCorner
        );
    }

    public Location getMinCorner() {
        return minCorner;
    }

    public Location getMaxCorner() {
        return maxCorner;
    }

    public World getWorld() {
        return world;
    }

    public Location getMin(List<Location> locations) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;

        for (Location loc : locations) {
            if (!loc.getWorld().equals(world)) {
                throw new IllegalArgumentException("All locations must be in the same world.");
            }

            double x = loc.getX();
            double y = loc.getY();
            double z = loc.getZ();

            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (z < minZ) minZ = z;
        }

        return new Location(world, minX, minY, minZ);
    }

    public Location getMax(List<Location> locations) {
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE, maxZ = Double.MIN_VALUE;

        for (Location loc : locations) {
            if (!loc.getWorld().equals(world)) {
                throw new IllegalArgumentException("All locations must be in the same world.");
            }

            double x = loc.getX();
            double y = loc.getY();
            double z = loc.getZ();

            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
            if (z > maxZ) maxZ = z;
        }

        return new Location(world, maxX, maxY, maxZ);
    }

    public static void drawBoundingBoxOutline(Location min, Location max, Particle particle, double step, Player viewer) {
        World world = min.getWorld();
        if (!world.equals(max.getWorld())) {
            throw new IllegalArgumentException("min and max must be in the same world.");
        }

        double minX = Math.min(min.getX(), max.getX());
        double minY = Math.min(min.getY(), max.getY());
        double minZ = Math.min(min.getZ(), max.getZ());
        double maxX = Math.max(min.getX(), max.getX());
        double maxY = Math.max(min.getY(), max.getY());
        double maxZ = Math.max(min.getZ(), max.getZ());

        List<Location[]> edges = List.of(
                // bottom edges
                new Location[] { new Location(world, minX, minY, minZ), new Location(world, maxX, minY, minZ) },
                new Location[] { new Location(world, maxX, minY, minZ), new Location(world, maxX, minY, maxZ) },
                new Location[] { new Location(world, maxX, minY, maxZ), new Location(world, minX, minY, maxZ) },
                new Location[] { new Location(world, minX, minY, maxZ), new Location(world, minX, minY, minZ) },

                // top edges
                new Location[] { new Location(world, minX, maxY, minZ), new Location(world, maxX, maxY, minZ) },
                new Location[] { new Location(world, maxX, maxY, minZ), new Location(world, maxX, maxY, maxZ) },
                new Location[] { new Location(world, maxX, maxY, maxZ), new Location(world, minX, maxY, maxZ) },
                new Location[] { new Location(world, minX, maxY, maxZ), new Location(world, minX, maxY, minZ) },

                // vertical edges
                new Location[] { new Location(world, minX, minY, minZ), new Location(world, minX, maxY, minZ) },
                new Location[] { new Location(world, maxX, minY, minZ), new Location(world, maxX, maxY, minZ) },
                new Location[] { new Location(world, maxX, minY, maxZ), new Location(world, maxX, maxY, maxZ) },
                new Location[] { new Location(world, minX, minY, maxZ), new Location(world, minX, maxY, maxZ) }
        );

        for (Location[] edge : edges) {
            drawLine(edge[0], edge[1], step, particle, viewer);
        }
    }

    private static void drawLine(Location start, Location end, double step, Particle particle, Player viewer) {
        Vector direction = end.toVector().subtract(start.toVector());
        double length = direction.length();
        direction.normalize();

        for (double d = 0; d <= length; d += step) {
            Vector point = start.toVector().add(direction.clone().multiply(d));
            viewer.spawnParticle(particle, point.getX(), point.getY(), point.getZ(), 1, 0, 0, 0, new Particle.DustOptions(Color.LIME, 1f));
        }
    }

}
