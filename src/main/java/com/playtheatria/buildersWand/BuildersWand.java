package com.playtheatria.buildersWand;

import com.playtheatria.buildersWand.commands.WandGive;
import com.playtheatria.buildersWand.listeners.PlayerInteractListener;
import com.playtheatria.buildersWand.tasks.VisualizationTask;
import com.playtheatria.buildersWand.utils.ConfigManager;
import com.playtheatria.buildersWand.workload.DistributedFiller;
import com.playtheatria.buildersWand.workload.WorkloadRunnable;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class BuildersWand extends JavaPlugin {

    private int taskId;

    @Override
    public void onEnable() {
        // Plugin startup logic
        ConfigManager configManager = new ConfigManager();
        Objects.requireNonNull(getCommand("wand")).setExecutor(new WandGive(configManager));
        taskId = VisualizationTask.run(this);
        WorkloadRunnable workloadRunnable = new WorkloadRunnable(configManager);
        DistributedFiller distributedFiller = new DistributedFiller(workloadRunnable);
        Bukkit.getServer().getPluginManager().registerEvents(new PlayerInteractListener(distributedFiller), this);
        Bukkit.getScheduler().runTaskTimer(this, workloadRunnable, 1, 1);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        Bukkit.getServer().getScheduler().cancelTask(taskId);
    }
}
