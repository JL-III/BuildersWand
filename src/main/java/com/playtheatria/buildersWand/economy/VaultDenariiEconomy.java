package com.playtheatria.buildersWand.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/** Vault adapter for the EssentialsX balance named Denarii on Theatria. */
final class VaultDenariiEconomy implements DenariiEconomy {

    private final JavaPlugin plugin;

    private VaultDenariiEconomy(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    static DenariiEconomy create(JavaPlugin plugin) {
        VaultDenariiEconomy bridge = new VaultDenariiEconomy(plugin);
        Economy provider = bridge.provider();
        if (provider == null) {
            plugin.getLogger().warning("Vault is present but no economy provider is registered yet.");
        } else {
            plugin.getLogger().info("Builders Wand Use restoration connected to Vault economy: " + provider.getName());
        }
        return bridge;
    }

    @Override
    public boolean available() {
        return provider() != null;
    }

    @Override
    public double balance(Player player) {
        Economy economy = provider();
        return economy == null ? 0.0 : economy.getBalance(player);
    }

    @Override
    public boolean has(Player player, long amount) {
        Economy economy = provider();
        return economy != null && economy.has(player, (double) amount);
    }

    @Override
    public Transaction withdraw(Player player, long amount) {
        Economy economy = provider();
        return economy == null
                ? unavailableTransaction()
                : transaction(economy.withdrawPlayer(player, (double) amount));
    }

    @Override
    public Transaction deposit(Player player, long amount) {
        Economy economy = provider();
        return economy == null
                ? unavailableTransaction()
                : transaction(economy.depositPlayer(player, (double) amount));
    }

    @Override
    public String format(long amount) {
        Economy economy = provider();
        return economy == null ? amount + " Denarii" : economy.format((double) amount);
    }

    private Economy provider() {
        RegisteredServiceProvider<Economy> registration =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (registration == null || registration.getProvider() == null
                || !registration.getProvider().isEnabled()) {
            return null;
        }
        return registration.getProvider();
    }

    private static Transaction unavailableTransaction() {
        return new Transaction(false, 0.0, "No Vault economy provider is available");
    }

    private static Transaction transaction(EconomyResponse response) {
        return new Transaction(response.transactionSuccess(), response.balance, response.errorMessage);
    }
}
