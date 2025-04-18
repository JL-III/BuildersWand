package com.playtheatria.buildersWand.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

public class Player implements Listener {
    @EventHandler
    public void onPlayerClick(PlayerInteractEvent event) {
        // check if player is holding wand in left hand
        // check if player has correct amount of blocks in right hand
    }
}
