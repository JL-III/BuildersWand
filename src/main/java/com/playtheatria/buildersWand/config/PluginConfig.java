package com.playtheatria.buildersWand.config;

import com.playtheatria.buildersWand.form.Form;
import com.playtheatria.buildersWand.form.FormLimits;
import com.playtheatria.buildersWand.prefab.PrefabRuntimeSettings;
import org.bukkit.Color;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.List;

/**
 * Immutable config holder, loaded once in {@code onEnable} from {@code config.yml}
 * (design §14.3). Placement, scan, chunk, and dimension limits are validated at startup.
 */
public final class PluginConfig {

    public final int smallPrintMaxCells;
    public final int smallPrintTicksPerCell;
    public final int largePrintTicksPerCell;
    public final int ghostUpdateTicks;
    public final int anchorReach;
    public final float ghostScale;
    public final Color ghostReadyGlow;
    public final Color ghostPartialGlow;
    public final Color ghostBlockedGlow;
    public final int wandMaxUses;
    public final int waterUsesPerSource;
    public final int partialConfirmationSeconds;
    /** One shared legality policy for measurement, preview, and final preflight. */
    public final FormLimits formLimits;
    /** Validated server-owned prefab catalog and placement settings. */
    public final PrefabRuntimeSettings prefabSettings;
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
        this.anchorReach = requiredPositive(config, "anchor-reach", 16);
        if (anchorReach > 256) {
            throw new IllegalArgumentException("anchor-reach cannot exceed 256");
        }
        this.ghostScale = (float) config.getDouble("ghost.scale", 0.8);
        this.ghostReadyGlow = color(config, "ghost.ready-glow-rgb", "55FF55");
        this.ghostPartialGlow = color(config, "ghost.partial-glow-rgb", "FFFF55");
        this.ghostBlockedGlow = color(config, "ghost.blocked-glow-rgb", "FF5555");
        this.wandMaxUses = positive(config.getInt("wand.max-uses", 5000), 5000);
        this.waterUsesPerSource = positive(config.getInt("wand.water-uses-per-source", 3), 3);
        this.partialConfirmationSeconds = positive(
                config.getInt("placement.partial-confirmation-seconds", 10), 10);
        this.formLimits = loadFormLimits(config);
        this.prefabSettings = loadPrefabSettings(plugin, config, wandMaxUses);
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

    private static FormLimits loadFormLimits(FileConfiguration config) {
        FormLimits defaults = FormLimits.DEFAULTS;
        int maxCells = requiredPositive(config,
                "limits.max-cells-per-print", defaults.maxCellsPerPrint());
        int defaultScanCells = Math.max(defaults.maxScannedCellsPerPlan(), maxCells);
        FormLimits.Builder builder = FormLimits.builder()
                .maxCellsPerPrint(maxCells)
                .maxScannedCellsPerPlan(requiredPositive(config,
                        "limits.max-scanned-cells-per-plan", defaultScanCells))
                .maxChunksPerPrint(requiredPositive(config,
                        "limits.max-chunks-per-print", defaults.maxChunksPerPrint()));
        for (Form form : Form.values()) {
            String path = "limits.max-span." + form.key().replace('_', '-');
            var fallback = defaults.maxSpans(form);
            if (config.isInt(path)) {
                int scalar = requiredPositive(config, path, fallback.primary());
                switch (form) {
                    case LINE -> builder.maxSpans(form, scalar, 1, 1);
                    case WALL, FLOOR, EXTEND_SURFACE -> builder.maxSpans(form, scalar, scalar, 1);
                    case BOX -> builder.maxSpans(form, scalar, scalar, scalar);
                    case DIAGONAL, CYLINDER, SPHERE -> builder.maxSpans(form, scalar,
                            fallback.secondary(), fallback.tertiary());
                }
                continue;
            }
            int primary = configuredSpan(config, path, "primary", fallback.primary());
            int secondary = configuredSpan(config, path, "secondary", fallback.secondary());
            int tertiary = configuredSpan(config, path, "tertiary", fallback.tertiary());
            builder.maxSpans(form, primary, secondary, tertiary);
        }
        return builder.build();
    }

    private static PrefabRuntimeSettings loadPrefabSettings(
            JavaPlugin plugin,
            FileConfiguration config,
            int wandMaxUses
    ) {
        String configuredDirectory = config.getString("prefabs.directory", "prefabs");
        if (configuredDirectory == null || configuredDirectory.isBlank()) {
            throw new IllegalArgumentException("prefabs.directory must not be blank");
        }
        Path relative = Path.of(configuredDirectory.trim()).normalize();
        if (relative.isAbsolute()
                || relative.getNameCount() == 0
                || relative.toString().isBlank()
                || relative.toString().equals(".")
                || relative.startsWith("..")) {
            throw new IllegalArgumentException(
                    "prefabs.directory must stay inside the BuildersWand data directory"
            );
        }

        long activationUses = requiredNonNegativeLong(
                config, "prefabs.default-activation-uses", 20L
        );
        if (activationUses > wandMaxUses) {
            throw new IllegalArgumentException("prefabs.default-activation-uses cannot exceed "
                    + "wand.max-uses (" + wandMaxUses + ")");
        }
        return new PrefabRuntimeSettings(
                config.getBoolean("prefabs.enabled", true),
                plugin.getDataFolder().toPath().resolve(relative),
                requiredPositive(config, "prefabs.confirmation-seconds", 30),
                activationUses,
                requiredPositive(config, "prefabs.max-cells", 512),
                requiredPositive(config, "prefabs.max-volume", 8192),
                requiredPositive(config, "prefabs.max-axis-span", 32),
                requiredPositive(config, "prefabs.max-chunks", 8),
                requiredPositive(config, "prefabs.max-clearance-cells", 8192),
                config.getBoolean("prefabs.require-logblock", true)
        );
    }

    /** Supports either a convenient scalar or explicit per-axis values for unusual forms. */
    private static int configuredSpan(FileConfiguration config, String path, String axis,
                                      int fallback) {
        return requiredPositive(config, path + "." + axis, fallback);
    }

    private static int requiredPositive(FileConfiguration config, String path, int fallback) {
        int value = config.getInt(path, fallback);
        if (value <= 0) {
            throw new IllegalArgumentException(path + " must be positive (was " + value + ")");
        }
        return value;
    }

    private static long requiredNonNegativeLong(
            FileConfiguration config,
            String path,
            long fallback
    ) {
        long value = config.getLong(path, fallback);
        if (value < 0L) {
            throw new IllegalArgumentException(path + " cannot be negative (was " + value + ")");
        }
        return value;
    }

    private static Color color(FileConfiguration config, String path, String fallback) {
        String value = config.getString(path, fallback);
        try {
            return Color.fromRGB(Integer.parseInt(value, 16));
        } catch (NumberFormatException ignored) {
            return Color.fromRGB(Integer.parseInt(fallback, 16));
        }
    }
}
