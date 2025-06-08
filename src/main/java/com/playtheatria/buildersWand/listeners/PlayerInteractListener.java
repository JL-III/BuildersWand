package com.playtheatria.buildersWand.listeners;

import com.playtheatria.buildersWand.wand.Wand;
import com.playtheatria.buildersWand.wand.WandData;
import com.playtheatria.buildersWand.workload.DistributedFiller;
import com.playtheatria.jliii.generalutils.result.Err;
import com.playtheatria.jliii.generalutils.result.Ok;
import com.playtheatria.jliii.generalutils.result.Result;
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
        if (event.getAction().equals(Action.RIGHT_CLICK_AIR)) {
            // check if player is holding wand in left hand
            Result<WandData, Exception> wandDataResult = Wand.getWandData(event.getPlayer().getInventory().getItemInMainHand());
            switch (wandDataResult) {
                case Ok<WandData, Exception> wandData -> {
                    event.getPlayer().sendMessage("Right click detected");
                    distributedFiller.fillCubeBlockLocations(event.getPlayer().getTargetBlock(null, 16), wandData.value());
                }
                case Err<WandData, Exception> err -> {
                }
            }
        }
    }
}
