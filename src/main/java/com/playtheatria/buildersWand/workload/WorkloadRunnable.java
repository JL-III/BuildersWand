package com.playtheatria.buildersWand.workload;

import com.playtheatria.buildersWand.utils.ConfigManager;
import org.bukkit.Bukkit;

import java.util.ArrayDeque;
import java.util.Deque;

public class WorkloadRunnable implements Runnable {
    private final ConfigManager configManager;

    public WorkloadRunnable(ConfigManager configManager) {
        this.configManager = configManager;
    }

    private final Deque<Workload> workloadDeque = new ArrayDeque<>();

    public void addWorkload(Workload workload) {
        this.workloadDeque.add(workload);
    }

    @Override
    public void run() {
        long stopTime = System.nanoTime() + (int) (configManager.maxMillisecondsPerTick * 1E6);
        Workload nextLoad;

        while (System.nanoTime() <= stopTime && (nextLoad = this.workloadDeque.poll()) != null) {
            nextLoad.compute();
            Bukkit.getOnlinePlayers().forEach(player -> {
                player.sendMessage("WorkloadRunnable running - maxmillispertick: " + configManager.maxMillisecondsPerTick);
            });
        }
    }
}