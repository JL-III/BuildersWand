package com.playtheatria.buildersWand.workload;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class SetBlockWorkload implements Workload {
    private final Block block;
    private final Player player;
    private final Material material;

    public SetBlockWorkload(Block block, Material material, Player player) {
        this.block = block;
        this.player = player;
        this.material = material;
    }

    public void compute() {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();

        RegionQuery query = container.createQuery();
        Location loc = BukkitAdapter.adapt(block.getLocation());
        ApplicableRegionSet set = query.getApplicableRegions(loc);
        LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
        if (!set.testState(localPlayer, Flags.BUILD)) {
            player.sendMessage("You do not have permission to set blocks here.");
            // Optionally log this action or handle it in a way that fits your plugin's design.
           return;
        }
        block.setType(this.material);
    }
}
