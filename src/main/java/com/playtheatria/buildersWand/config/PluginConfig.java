package com.playtheatria.buildersWand.config;

import org.bukkit.Color;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Immutable config holder, loaded once in {@code onEnable} from {@code config.yml}
 * (design §14.3). The 512-cell cap and dims bounds are constants, not config.
 */
public final class PluginConfig {

    public final int smallPrintMaxCells;
    public final int smallPrintTicksPerCell;
    public final int largePrintTicksPerCell;
    public final int ghostUpdateTicks;
    public final int anchorReach;
    public final float ghostScale;
    public final Color ghostGlow;

    public PluginConfig(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        FileConfiguration config = plugin.getConfig();
        this.smallPrintMaxCells = config.getInt("cadence.small-print-max-cells", 16);
        this.smallPrintTicksPerCell = config.getInt("cadence.small-print-ticks-per-cell", 2);
        this.largePrintTicksPerCell = config.getInt("cadence.large-print-ticks-per-cell", 1);
        this.ghostUpdateTicks = config.getInt("ghost.update-ticks", 2);
        this.anchorReach = config.getInt("anchor-reach", 16);
        this.ghostScale = (float) config.getDouble("ghost.scale", 0.8);
        String glowHex = config.getString("ghost.glow-rgb", "9E3DFF");
        this.ghostGlow = Color.fromRGB(Integer.parseInt(glowHex, 16));
    }
}
