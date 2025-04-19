package com.playtheatria.buildersWand.listeners;

import com.playtheatria.buildersWand.tasks.VisualizationTask;
import com.playtheatria.buildersWand.utils.Cube;
import com.playtheatria.buildersWand.utils.Err;
import com.playtheatria.buildersWand.utils.Ok;
import com.playtheatria.buildersWand.utils.Result;
import com.playtheatria.buildersWand.wand.Wand;
import com.playtheatria.buildersWand.wand.WandData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;

public class PlayerInteractEvent implements Listener {
    @EventHandler
    public void onPlayerClick(org.bukkit.event.player.PlayerInteractEvent event) {
        // check if player is holding wand in left hand
        // check if player has correct amount of blocks in right hand
        if (event.getAction().equals(Action.RIGHT_CLICK_AIR)) {
            // check if player is holding wand in left hand
            Result<WandData, Exception> wandDataResult = Wand.getWandData(event.getPlayer().getInventory().getItemInMainHand());
            switch (wandDataResult) {
                case Ok<WandData, Exception> wandData -> {
                    for (Location location : VisualizationTask.getCubeBlockLocations(event.getPlayer().getTargetBlock(null, 16), wandData.value())) {
                        location.getBlock().setType(Material.STONE);
                    }
                }
                case Err<WandData, Exception> err -> {
                }
            }
        }
    }
}
