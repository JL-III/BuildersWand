package com.playtheatria.buildersWand;

import com.playtheatria.buildersWand.commands.WandGive;
import com.playtheatria.buildersWand.listeners.PlayerInteractEvent;
import com.playtheatria.buildersWand.tasks.VisualizationTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class BuildersWand extends JavaPlugin {

    private int taskId;

    @Override
    public void onEnable() {
        // Plugin startup logic
        Bukkit.getServer().getPluginManager().registerEvents(new PlayerInteractEvent(), this);
        Objects.requireNonNull(getCommand("wand")).setExecutor(new WandGive());
        taskId = VisualizationTask.run(this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        Bukkit.getServer().getScheduler().cancelTask(taskId);
    }
}
