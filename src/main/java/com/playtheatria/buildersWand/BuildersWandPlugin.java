package com.playtheatria.buildersWand;

import com.playtheatria.buildersWand.command.WandCommand;
import com.playtheatria.buildersWand.config.PluginConfig;
import com.playtheatria.buildersWand.gesture.GestureListener;
import com.playtheatria.buildersWand.protect.ProtectionBridge;
import com.playtheatria.buildersWand.wand.WandItems;
import com.playtheatria.buildersWand.wave.StopReason;
import com.playtheatria.buildersWand.wave.WaveRunner;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class BuildersWandPlugin extends JavaPlugin {

    private PluginConfig config;
    private WandItems wandItems;
    private ProtectionBridge protectionBridge;
    private WaveRunner waveRunner;
    private GestureListener gestureListener;

    @Override
    public void onEnable() {
        this.config = new PluginConfig(this);
        this.wandItems = new WandItems(this);
        this.protectionBridge = ProtectionBridge.composite(this);
        this.waveRunner = new WaveRunner(this, protectionBridge, config, wandItems);
        // Ghost clearer is a no-op until the ghost service exists (CP6).
        this.gestureListener = new GestureListener(wandItems, config, waveRunner, player -> { });
        getServer().getPluginManager().registerEvents(gestureListener, this);

        PluginCommand wandCommand = getCommand("wand");
        if (wandCommand != null) {
            WandCommand executor = new WandCommand(wandItems, gestureListener::clearSession);
            wandCommand.setExecutor(executor);
            wandCommand.setTabCompleter(executor);
        }
    }

    @Override
    public void onDisable() {
        if (waveRunner != null) {
            waveRunner.stopAll(StopReason.SERVER_STOPPING);
        }
    }
}
