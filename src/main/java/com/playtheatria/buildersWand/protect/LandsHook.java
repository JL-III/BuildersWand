package com.playtheatria.buildersWand.protect;

import me.angeschossen.lands.api.LandsIntegration;
import me.angeschossen.lands.api.flags.type.Flags;
import me.angeschossen.lands.api.land.Area;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/** Lands claim block-place check (design §12). Only loaded when Lands is present. */
public final class LandsHook implements ProtectionBridge {

    private final LandsIntegration landsIntegration;

    public LandsHook(Plugin plugin) {
        this.landsIntegration = LandsIntegration.of(plugin);
    }

    @Override
    public boolean canBuild(Player player, Location location) {
        Area area = landsIntegration.getArea(location);
        if (area == null) {
            return true; // wilderness
        }
        return area.hasRoleFlag(player.getUniqueId(), Flags.BLOCK_PLACE);
    }

    @Override
    public Optional<String> deniedBy(Player player, Location location) {
        return canBuild(player, location) ? Optional.empty() : Optional.of("Lands");
    }
}
