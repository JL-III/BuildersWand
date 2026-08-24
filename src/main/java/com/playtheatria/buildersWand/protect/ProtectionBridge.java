package com.playtheatria.buildersWand.protect;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The protection gate (design §12). {@link #composite} AND-s the hooks whose plugins are
 * present (an empty composite always permits).
 *
 * <p>{@link #deniedBy} names the denying plugin for the §13 refusal; it rides alongside the
 * spec's {@code canBuild} as a default so a single boolean gate is still the contract.
 */
public interface ProtectionBridge {

    boolean canBuild(Player player, Location location);

    /** The plugin denying the build here (design §13 names it), or empty if allowed. */
    default Optional<String> deniedBy(Player player, Location location) {
        return canBuild(player, location) ? Optional.empty() : Optional.of("protection");
    }

    static ProtectionBridge composite(Plugin plugin) {
        List<ProtectionBridge> hooks = new ArrayList<>();
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            hooks.add(new WorldGuardHook());
        }
        if (Bukkit.getPluginManager().getPlugin("Lands") != null) {
            hooks.add(new LandsHook(plugin));
        }
        return new ProtectionBridge() {
            @Override
            public boolean canBuild(Player player, Location location) {
                for (ProtectionBridge hook : hooks) {
                    if (!hook.canBuild(player, location)) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public Optional<String> deniedBy(Player player, Location location) {
                for (ProtectionBridge hook : hooks) {
                    Optional<String> denier = hook.deniedBy(player, location);
                    if (denier.isPresent()) {
                        return denier;
                    }
                }
                return Optional.empty();
            }
        };
    }
}
