package com.playtheatria.buildersWand.listeners;

import com.playtheatria.buildersWand.utils.Err;
import com.playtheatria.buildersWand.utils.Ok;
import com.playtheatria.buildersWand.utils.Result;
import com.playtheatria.buildersWand.wand.Wand;
import com.playtheatria.buildersWand.wand.WandData;
import com.playtheatria.buildersWand.workload.DistributedFiller;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class PlayerInteractListener implements Listener {
    private final DistributedFiller distributedFiller;

    public PlayerInteractListener(DistributedFiller distributedFiller) {
        this.distributedFiller = distributedFiller;
    }

    @EventHandler
    public void onPlayerClick(PlayerInteractEvent event) {
        if (event.getAction().equals(Action.RIGHT_CLICK_AIR) || event.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {
            if (event.getPlayer().getInventory().getItemInMainHand().getType() != Material.STICK) {
                return; // only handle right clicks with the wand (stick)
            }
            // check if player is holding wand in left hand
            Result<WandData, Exception> wandDataResult = Wand.getWandData(event.getPlayer().getInventory().getItemInMainHand());
            switch (wandDataResult) {
                case Ok<WandData, Exception> wandData -> {
                    event.getPlayer().sendMessage("Right click detected");
                    switch (Wand.getTargetBlock(event.getPlayer(), 16)) {
                        case Ok<Block, Exception> ok -> {
                            event.getPlayer().sendMessage("Target block found: " + ok.value().getType());
                            switch (wandData.value().mode()) {
                                case CUBE -> distributedFiller.fillCubeBlockLocations(ok.value(), wandData.value());
                                case CUBE_HOLLOW -> distributedFiller.fillHollowCubeBlockLocations(ok.value(), wandData.value());
                                case CUBE_WIRE -> distributedFiller.fillWireCubeBlockLocations(ok.value(), wandData.value());
                            }
                        }
                        case Err<Block, Exception> err -> {
                            event.getPlayer().sendMessage("No valid target block found: " + err.error().getMessage());
                        }
                    }
                }
                case Err<WandData, Exception> ignored -> {}
            }
        }
    }
}
