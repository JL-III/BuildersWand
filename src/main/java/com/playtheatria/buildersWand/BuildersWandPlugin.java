package com.playtheatria.buildersWand;

import com.playtheatria.buildersWand.command.WandCommand;
import com.playtheatria.buildersWand.config.PluginConfig;
import com.playtheatria.buildersWand.wand.WandItems;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class BuildersWandPlugin extends JavaPlugin {

    private PluginConfig config;
    private WandItems wandItems;

    @Override
    public void onEnable() {
        this.config = new PluginConfig(this);
        this.wandItems = new WandItems(this);

        PluginCommand wandCommand = getCommand("wand");
        if (wandCommand != null) {
            // Gesture clearer is a no-op until the gesture listener exists (CP5).
            WandCommand executor = new WandCommand(wandItems, player -> { });
            wandCommand.setExecutor(executor);
            wandCommand.setTabCompleter(executor);
        }
    }

    @Override
    public void onDisable() {
    }
}
