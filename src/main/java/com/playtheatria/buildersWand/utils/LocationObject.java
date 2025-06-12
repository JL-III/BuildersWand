package com.playtheatria.buildersWand.utils;

import org.bukkit.Location;

import java.util.List;

public class LocationObject {
    private final List<Location> locationList;
    private final double minX;
    private final double minY;
    private final double minZ;
    private final double maxX;
    private final double maxY;
    private final double maxZ;

    public LocationObject(List<Location> locationList, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.locationList = locationList;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public List<Location> getLocationList() {
        return locationList;
    }

    public double getMinX() {
        return minX;
    }
    public double getMinY() {
        return minY;
    }
    public double getMinZ() {
        return minZ;
    }
    public double getMaxX() {
        return maxX;
    }
    public double getMaxY() {
        return maxY;
    }
    public double getMaxZ() {
        return maxZ;
    }
}
