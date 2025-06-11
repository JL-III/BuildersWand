package com.playtheatria.buildersWand.workload;

import com.playtheatria.buildersWand.utils.ConfigManager;

import java.util.ArrayDeque;
import java.util.Deque;

public class WorkloadRunnable implements Runnable {
    private final ConfigManager configManager;

    public WorkloadRunnable(ConfigManager configManager) {
        this.configManager = configManager;
    }

    private final Deque<Workload> workloadArrayDeque = new ArrayDeque<>();

    public void addWorkload(Workload setBlockWorkload) {
        this.workloadArrayDeque.add(setBlockWorkload);
    }

    @Override
    public void run() {
        long stopTime = System.nanoTime() + (int) (configManager.maxMillisecondsPerTick * 1E6);
        Workload workload;

        while (System.nanoTime() <= stopTime && (workload = this.workloadArrayDeque.poll()) != null) {
            workload.compute();
        }
    }
}