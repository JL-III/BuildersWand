package com.playtheatria.buildersWand.protect;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Optional;

/** WorldGuard region + bypass check (design §12). Only loaded when WorldGuard is present. */
public final class WorldGuardHook implements ProtectionBridge {

    @Override
    public boolean canBuild(Player player, Location location) {
        LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
        if (WorldGuard.getInstance().getPlatform().getSessionManager()
                .hasBypass(localPlayer, localPlayer.getWorld())) {
            return true;
        }
        return WorldGuard.getInstance().getPlatform().getRegionContainer()
                .createQuery().testBuild(BukkitAdapter.adapt(location), localPlayer);
    }

    @Override
    public Optional<String> deniedBy(Player player, Location location) {
        return canBuild(player, location) ? Optional.empty() : Optional.of("WorldGuard");
    }
}
