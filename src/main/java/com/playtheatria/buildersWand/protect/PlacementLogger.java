package com.playtheatria.buildersWand.protect;

import org.bukkit.Bukkit;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Logs each printed cell to the server's grief tracker (design §12, amendment 2026-08-25).
 * Wand prints go through {@code setBlockData} and fire no {@code BlockPlaceEvent}, so without
 * this they are invisible to lookups/rollbacks; logging attributes every cell to the printing
 * player's own actor (place when the prior state was air, replace otherwise). No-op when the
 * tracker is absent.
 */
public interface PlacementLogger {

    void logPlacement(Player player, BlockState before, BlockState after);

    /** Whether placements are currently reaching a real rollback backend. */
    boolean available();

    /** Short diagnostic suitable for validation output and startup logs. */
    String diagnostic();

    /**
     * The LogBlock-backed logger when LogBlock is present, else a no-op.
     *
     * <p>The adapter deliberately resolves LogBlock through its plugin class loader. This keeps
     * LogBlock optional at compile time while still calling its real current API when installed:
     * {@code Actor(playerName, playerUuid)} plus {@code queueBlockPlace/queueBlockReplace}.
     */
    static PlacementLogger composite(Plugin plugin) {
        Plugin logBlock = Bukkit.getPluginManager().getPlugin("LogBlock");
        if (logBlock == null || !logBlock.isEnabled()) {
            plugin.getLogger().warning("LogBlock is not available; wand placement rollback attribution is disabled.");
            return unavailable("LogBlock is not installed or enabled");
        }
        try {
            PlacementLogger logger = new ReflectiveLogBlockLogger(logBlock);
            if (logger.available()) {
                plugin.getLogger().info("BuildersWand placement logging connected to LogBlock.");
            } else {
                plugin.getLogger().warning("BuildersWand connected to LogBlock's API, but "
                        + logger.diagnostic() + "; prefab placement will fail closed until ready.");
            }
            return logger;
        } catch (ReflectiveOperationException | RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "LogBlock is installed but its placement API could not be connected."
                            + " Prefab validation must fail closed until this is fixed.", error);
            return unavailable("LogBlock API is incompatible: " + error.getClass().getSimpleName());
        }
    }

    static PlacementLogger unavailable(String reason) {
        return new PlacementLogger() {
            @Override
            public void logPlacement(Player player, BlockState before, BlockState after) {
                // Ordinary wand placement retains its historical optional-logging behavior.
                // Prefab admission checks available() and fails closed before reaching this path.
            }

            @Override
            public boolean available() {
                return false;
            }

            @Override
            public String diagnostic() {
                return reason;
            }
        };
    }

    /** Current LogBlock API adapter without a hard class-linkage dependency. */
    final class ReflectiveLogBlockLogger implements PlacementLogger {

        private final Plugin logBlock;
        private final Object consumer;
        private final Constructor<?> actorConstructor;
        private final Method queuePlace;
        private final Method queueReplace;
        private final Method completelyEnabled;

        ReflectiveLogBlockLogger(Plugin logBlock) throws ReflectiveOperationException {
            this.logBlock = logBlock;
            ClassLoader loader = logBlock.getClass().getClassLoader();
            Class<?> actorClass = Class.forName("de.diddiz.LogBlock.Actor", true, loader);
            this.actorConstructor = actorClass.getConstructor(String.class, UUID.class);

            Method getConsumer = logBlock.getClass().getMethod("getConsumer");
            this.consumer = getConsumer.invoke(logBlock);
            if (consumer == null) {
                throw new IllegalStateException("LogBlock consumer is not initialized");
            }
            Class<?> consumerClass = consumer.getClass();
            this.queuePlace = consumerClass.getMethod(
                    "queueBlockPlace", actorClass, BlockState.class);
            this.queueReplace = consumerClass.getMethod(
                    "queueBlockReplace", actorClass, BlockState.class, BlockState.class);
            Method readiness;
            try {
                readiness = logBlock.getClass().getMethod("isCompletelyEnabled");
            } catch (NoSuchMethodException ignored) {
                // Compatibility with older LogBlock releases: Bukkit's enabled state was the
                // only readiness signal exposed by those versions.
                readiness = null;
            }
            this.completelyEnabled = readiness;
        }

        @Override
        public void logPlacement(Player player, BlockState before, BlockState after) {
            if (!available()) {
                throw new IllegalStateException("LogBlock became unavailable during placement");
            }
            try {
                Object actor = actorConstructor.newInstance(player.getName(), player.getUniqueId());
                if (before.getType().isAir()) {
                    queuePlace.invoke(consumer, actor, after);
                } else {
                    queueReplace.invoke(consumer, actor, before, after);
                }
            } catch (InvocationTargetException error) {
                Throwable cause = error.getCause();
                if (cause instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException("LogBlock rejected a placement", cause);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Could not queue a LogBlock placement", error);
            }
        }

        @Override
        public boolean available() {
            if (!logBlock.isEnabled()) {
                return false;
            }
            if (completelyEnabled == null) {
                return true;
            }
            try {
                return Boolean.TRUE.equals(completelyEnabled.invoke(logBlock));
            } catch (ReflectiveOperationException | RuntimeException error) {
                return false;
            }
        }

        @Override
        public String diagnostic() {
            if (available()) {
                return "LogBlock connected";
            }
            return logBlock.isEnabled()
                    ? "LogBlock is enabled but its database is not ready"
                    : "LogBlock is disabled";
        }
    }
}
