package com.playtheatria.buildersWand.config;

import org.bukkit.Color;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

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
    public final int wandMaxUses;
    public final int waterUsesPerSource;
    public final long restoreDenariiPerUse;
    public final int restoreMinimumUses;
    public final int restoreConfirmationSeconds;
    public final boolean recognitionAnnouncements;
    public final List<Long> totalBlockMilestones;

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
        this.wandMaxUses = positive(config.getInt("wand.max-uses", 5000), 5000);
        this.waterUsesPerSource = positive(config.getInt("wand.water-uses-per-source", 3), 3);
        boolean legacyRestoreConfig = !config.contains("wand.refill.denarii-per-use", true)
                && config.contains("wand.recharge.denarii-per-use", true);
        String restoreRoot = legacyRestoreConfig ? "wand.recharge" : "wand.refill";
        if (legacyRestoreConfig) {
            plugin.getLogger().warning("Using legacy wand.recharge config; rename it to wand.refill.");
        }
        this.restoreDenariiPerUse = positive(config.getLong(restoreRoot + ".denarii-per-use", 50L), 50L);
        this.restoreMinimumUses = Math.min(wandMaxUses,
                positive(config.getInt(restoreRoot + ".minimum-uses", 100), 100));
        this.restoreConfirmationSeconds = positive(
                config.getInt(restoreRoot + ".confirmation-seconds", 30), 30);
        this.recognitionAnnouncements = config.getBoolean("recognition.announcements", true);
        List<Long> milestones = config.getLongList("recognition.total-block-milestones").stream()
                .filter(value -> value > 0L)
                .distinct()
                .sorted()
                .toList();
        this.totalBlockMilestones = milestones.isEmpty() ? List.of(1_000_000L) : milestones;
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private static long positive(long value, long fallback) {
        return value > 0 ? value : fallback;
    }
}
