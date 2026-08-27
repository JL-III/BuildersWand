package com.playtheatria.buildersWand;

import com.playtheatria.buildersWand.command.WandCommand;
import com.playtheatria.buildersWand.command.RefillService;
import com.playtheatria.buildersWand.config.PluginConfig;
import com.playtheatria.buildersWand.economy.DenariiEconomy;
import com.playtheatria.buildersWand.ghost.GhostService;
import com.playtheatria.buildersWand.gesture.GestureListener;
import com.playtheatria.buildersWand.protect.PlacementLogger;
import com.playtheatria.buildersWand.protect.ProtectionBridge;
import com.playtheatria.buildersWand.stats.BuildStatsStore;
import com.playtheatria.buildersWand.wand.WandItems;
import com.playtheatria.buildersWand.wave.StopReason;
import com.playtheatria.buildersWand.wave.WaveRunner;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class BuildersWandPlugin extends JavaPlugin {

    private PluginConfig config;
    private WandItems wandItems;
    private ProtectionBridge protectionBridge;
    private PlacementLogger placementLogger;
    private BuildStatsStore buildStats;
    private WaveRunner waveRunner;
    private GestureListener gestureListener;
    private GhostService ghostService;
    private RefillService refillService;

    @Override
    public void onEnable() {
        this.config = new PluginConfig(this);
        this.wandItems = new WandItems(this, config.wandMaxUses);
        this.protectionBridge = ProtectionBridge.composite(this);
        this.placementLogger = PlacementLogger.composite(this);
        this.buildStats = BuildStatsStore.open(this);
        this.waveRunner = new WaveRunner(this, protectionBridge, placementLogger, config, wandItems, buildStats);
        this.gestureListener = new GestureListener(wandItems, config, waveRunner);
        this.ghostService = new GhostService(this, config, wandItems, waveRunner, gestureListener);
        DenariiEconomy economy = DenariiEconomy.load(this);
        this.refillService = new RefillService(this, config, wandItems, waveRunner, economy, buildStats);
        gestureListener.setGhostClearer(ghostService::clearFor);

        getServer().getPluginManager().registerEvents(gestureListener, this);
        getServer().getPluginManager().registerEvents(refillService, this);
        PluginCommand wandCommand = getCommand("wand");
        if (wandCommand != null) {
            WandCommand executor = new WandCommand(
                    wandItems, refillService, buildStats, gestureListener::clearSession,
                    waveRunner::hasActiveWave, getLogger());
            wandCommand.setExecutor(executor);
            wandCommand.setTabCompleter(executor);
        }

        ghostService.start();
    }

    @Override
    public void onDisable() {
        if (waveRunner != null) {
            waveRunner.stopAll(StopReason.SERVER_STOPPING);
        }
        if (ghostService != null) {
            ghostService.clearAll();
        }
        if (refillService != null) {
            refillService.clearAll();
        }
        if (buildStats != null) {
            buildStats.close();
        }
    }
}
