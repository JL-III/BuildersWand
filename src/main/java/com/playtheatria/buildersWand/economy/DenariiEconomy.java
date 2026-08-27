package com.playtheatria.buildersWand.economy;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Small fail-closed economy seam; production is backed by Vault/EssentialsX. */
public interface DenariiEconomy {

    record Transaction(boolean success, double balance, String error) {
    }

    boolean available();

    double balance(Player player);

    boolean has(Player player, long amount);

    Transaction withdraw(Player player, long amount);

    Transaction deposit(Player player, long amount);

    String format(long amount);

    static DenariiEconomy load(JavaPlugin plugin) {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault is absent; Builders Wand Use restoration is unavailable.");
            return unavailable();
        }
        try {
            return VaultDenariiEconomy.create(plugin);
        } catch (LinkageError | RuntimeException error) {
            plugin.getLogger().severe("Vault was detected but its API could not be loaded: " + error.getMessage());
        }
        plugin.getLogger().warning("Vault could not be connected; Use restoration is unavailable.");
        return unavailable();
    }

    static DenariiEconomy unavailable() {
        return new DenariiEconomy() {
            private static final Transaction FAILURE =
                    new Transaction(false, 0.0, "No economy provider is available");

            @Override
            public boolean available() {
                return false;
            }

            @Override
            public double balance(Player player) {
                return 0.0;
            }

            @Override
            public boolean has(Player player, long amount) {
                return false;
            }

            @Override
            public Transaction withdraw(Player player, long amount) {
                return FAILURE;
            }

            @Override
            public Transaction deposit(Player player, long amount) {
                return FAILURE;
            }

            @Override
            public String format(long amount) {
                return amount + " Denarii";
            }
        };
    }
}
