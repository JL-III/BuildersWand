package com.playtheatria.buildersWand.workload;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.UUID;

public class Workload {
    private final UUID worldID;
    private final int blockX;
    private final int blockY;
    private final int blockZ;
    private final Material material;

    public Workload(UUID worldID, int blockX, int blockY, int blockZ, Material material) {
        this.worldID = worldID;
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
        this.material = material;
    }

    public void compute() {
        World world = Bukkit.getWorld(this.worldID);
        if (world == null) return;
        world.getBlockAt(this.blockX, this.blockY, this.blockZ).setType(this.material);
        world.getBlockAt(this.blockX, this.blockY, this.blockZ).getState().update(true, false);
    }
}
