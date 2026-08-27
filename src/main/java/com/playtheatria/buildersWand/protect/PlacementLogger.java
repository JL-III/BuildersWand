package com.playtheatria.buildersWand.protect;

import org.bukkit.Bukkit;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Logs each printed cell to the server's grief tracker (design §12, amendment 2026-08-25).
 * Wand prints go through {@code setBlockData} and fire no {@code BlockPlaceEvent}, so without
 * this they are invisible to lookups/rollbacks; logging attributes every cell to the printing
 * player's own actor (place when the prior state was air, replace otherwise). No-op when the
 * tracker is absent.
 */
public interface PlacementLogger {

    void logPlacement(Player player, BlockState before, BlockState after);

    /**
     * The LogBlock-backed logger when LogBlock is present, else a no-op.
     *
     * <p>The LogBlock hook compiles only against the production server's {@code libs/logblock.jar}
     * (no public artifact exists, design §5.10). Until that jar is supplied the hook does not
     * exist, so this returns a no-op — and warns if LogBlock is actually running, so the missing
     * attribution is visible rather than silent.
     */
    static PlacementLogger composite(Plugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("LogBlock") != null) {
            plugin.getLogger().warning("LogBlock is present but placement logging is not compiled in"
                    + " — add libs/logblock.jar and rebuild so wand prints are attributed to the player.");
        }
        return (player, before, after) -> { };
    }
}
