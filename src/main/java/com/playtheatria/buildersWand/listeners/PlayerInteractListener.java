package com.playtheatria.buildersWand.listeners;

import com.playtheatria.buildersWand.wand.Wand;
import com.playtheatria.buildersWand.wand.WandData;
import com.playtheatria.buildersWand.workload.DistributedFiller;
import com.playtheatria.jliii.generalutils.result.Err;
import com.playtheatria.jliii.generalutils.result.Ok;
import com.playtheatria.jliii.generalutils.result.Result;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.RayTraceResult;

public class PlayerInteractListener implements Listener {
    private final DistributedFiller distributedFiller;

    public PlayerInteractListener(DistributedFiller distributedFiller) {
        this.distributedFiller = distributedFiller;
    }

    @EventHandler
    public void onPlayerClick(PlayerInteractEvent event) {
        Result<WandData, Exception> wandDataResult = Wand.getWandData(event.getPlayer().getInventory().getItemInMainHand());
        switch (wandDataResult) {
            case Ok<WandData, Exception> wandData -> {
                event.getPlayer().sendMessage("Right click detected");
                switch (getTargetBlock(event.getPlayer(), 16)) {
                    case Ok<Block, Exception> ok -> {
                        distributedFiller.fillCubeLocations(DistributedFiller.getBlockLocations(ok.value(), event.getPlayer(), wandData.value()), event.getPlayer());
                    }
                    case Err<Block, Exception> err -> {
                        event.getPlayer().sendMessage(err.error().getMessage());
                    }
                }

            }
            case Err<WandData, Exception> err -> {
            }
        }
    }

    public Result<Block, Exception> getTargetBlock(Player player, int maxDistance) {
        Location eyeLocation = player.getEyeLocation();
        RayTraceResult rayTraceResult = player.getWorld().rayTraceBlocks(
                eyeLocation,
                eyeLocation.getDirection(),
                maxDistance,
                FluidCollisionMode.NEVER,
                true // ignore passable blocks
        );

        if (rayTraceResult != null && rayTraceResult.getHitBlockFace() != null) {
            Block hitBlock = rayTraceResult.getHitBlock();
            BlockFace hitFace = rayTraceResult.getHitBlockFace();

            if (hitBlock == null) {
                return new Err<>(new Exception("No block hit"));
            }

            return new Ok<>(hitBlock.getRelative(hitFace));
        }

        return new Err<>(new Exception("no valid face found")); // no valid face found
    }
}
