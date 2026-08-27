package com.playtheatria.buildersWand.command;

import com.playtheatria.buildersWand.config.PluginConfig;
import com.playtheatria.buildersWand.economy.DenariiEconomy;
import com.playtheatria.buildersWand.stats.BuildStatsStore;
import com.playtheatria.buildersWand.wand.UseCounter;
import com.playtheatria.buildersWand.wand.WandItems;
import com.playtheatria.buildersWand.wave.WaveRunner;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Two-step, token-bound Denarii flow for restoring Uses on the wand in the player's main hand. */
public final class RefillService implements Listener {

    private record Quote(UUID playerId, String confirmationToken, String wandToken,
                         int expectedUses, int maximumUses, int addedUses,
                         long unitPrice, long totalPrice, long expiresAtNanos) {
    }

    private final JavaPlugin plugin;
    private final PluginConfig config;
    private final WandItems wandItems;
    private final WaveRunner waveRunner;
    private final DenariiEconomy economy;
    private final BuildStatsStore buildStats;
    private final Map<UUID, Quote> quotes = new HashMap<>();

    public RefillService(JavaPlugin plugin, PluginConfig config, WandItems wandItems,
                         WaveRunner waveRunner, DenariiEconomy economy, BuildStatsStore buildStats) {
        this.plugin = plugin;
        this.config = config;
        this.wandItems = wandItems;
        this.waveRunner = waveRunner;
        this.economy = economy;
        this.buildStats = buildStats;
    }

    /** Quote restoration of all missing Uses for null/all, or exactly {@code requested} Uses. */
    public void quote(Player player, String requested) {
        if (!precheck(player)) {
            return;
        }
        ItemStack wand = heldSingleWand(player);
        if (wand == null) {
            return;
        }
        wandItems.ensureFirstWielder(wand, player);
        UseCounter.State state = wandItems.initializeUses(wand);
        player.getInventory().setItemInMainHand(wand);
        int missing = state.maximum() - state.remaining();
        if (missing == 0) {
            player.sendMessage(Component.text("This Builders Wand is already full ("
                    + state.remaining() + "/" + state.maximum() + ").", NamedTextColor.YELLOW));
            return;
        }

        Integer added = parseAddedUses(player, requested, missing);
        if (added == null) {
            return;
        }
        if (added < config.restoreMinimumUses) {
            long minimumPrice;
            try {
                minimumPrice = Math.multiplyExact(
                        (long) config.restoreMinimumUses, config.restoreDenariiPerUse);
            } catch (ArithmeticException overflow) {
                plugin.getLogger().severe("Configured minimum Builders Wand restoration price overflows a long");
                player.sendMessage(Component.text("The Use-restoration price is misconfigured; no money was taken.",
                        NamedTextColor.RED));
                return;
            }
            player.sendMessage(Component.text("The minimum restoration is " + config.restoreMinimumUses
                    + " Uses (" + format(minimumPrice)
                    + "). Use the wand more before restoring Uses.", NamedTextColor.RED));
            return;
        }

        long price;
        try {
            price = Math.multiplyExact((long) added, config.restoreDenariiPerUse);
        } catch (ArithmeticException overflow) {
            plugin.getLogger().severe("Restoration price overflow for " + added + " Uses");
            player.sendMessage(Component.text("That restoration amount cannot be priced safely.", NamedTextColor.RED));
            return;
        }

        String wandToken = wandItems.rotateActiveToken(wand);
        player.getInventory().setItemInMainHand(wand);
        String confirmationToken = UUID.randomUUID().toString();
        long expiresAt = System.nanoTime() + config.restoreConfirmationSeconds * 1_000_000_000L;
        Quote quote = new Quote(player.getUniqueId(), confirmationToken, wandToken,
                state.remaining(), state.maximum(), added, config.restoreDenariiPerUse, price, expiresAt);
        quotes.put(player.getUniqueId(), quote);

        int after = state.remaining() + added;
        player.sendMessage(Component.text("Builders Wand: " + state.remaining() + "/" + state.maximum()
                + " Uses", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Restore Uses: +" + added + " → " + after + "/" + state.maximum(),
                NamedTextColor.GRAY));
        player.sendMessage(Component.text("Cost: " + format(price) + " ("
                + format(config.restoreDenariiPerUse) + " per Use)", NamedTextColor.GRAY));
        Component confirm = Component.text("[CONFIRM]", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand("/wand restore confirm " + confirmationToken))
                .hoverEvent(HoverEvent.showText(Component.text("Pay " + format(price))));
        Component cancel = Component.text("[CANCEL]", NamedTextColor.RED)
                .clickEvent(ClickEvent.runCommand("/wand restore cancel"));
        player.sendMessage(confirm.append(Component.space()).append(cancel)
                .append(Component.text(" · expires in " + config.restoreConfirmationSeconds + "s",
                        NamedTextColor.DARK_GRAY)));
    }

    public void confirm(Player player, String suppliedToken) {
        Quote quote = quotes.get(player.getUniqueId());
        if (quote == null || (suppliedToken != null && !quote.confirmationToken().equals(suppliedToken))) {
            player.sendMessage(Component.text("There is no matching restoration quote. Run /wand restore again.",
                    NamedTextColor.RED));
            return;
        }
        if (expired(quote)) {
            quotes.remove(player.getUniqueId());
            player.sendMessage(Component.text("That restoration quote expired. Run /wand restore again.",
                    NamedTextColor.RED));
            return;
        }
        if (!precheck(player)) {
            return;
        }
        ItemStack wand = player.getInventory().getItemInMainHand();
        if (!matchesQuote(wand, quote)) {
            quotes.remove(player.getUniqueId());
            player.sendMessage(Component.text("The held wand changed; request a new restoration quote.",
                    NamedTextColor.RED));
            return;
        }
        if (quote.unitPrice() != config.restoreDenariiPerUse) {
            quotes.remove(player.getUniqueId());
            player.sendMessage(Component.text("The restoration rate changed; request a new quote.", NamedTextColor.RED));
            return;
        }

        int newUses = quote.expectedUses() + quote.addedUses();
        ItemStack restored = wand.clone();
        WandItems.RestoreReceipt restoreReceipt;
        try {
            var prepared = wandItems.applyPaidRestore(restored, quote.wandToken(),
                    quote.expectedUses(), newUses);
            if (prepared.isEmpty()) {
                quotes.remove(player.getUniqueId());
                player.sendMessage(Component.text("That wand's Uses could not be restored safely; no money was taken.",
                        NamedTextColor.RED));
                return;
            }
            restoreReceipt = prepared.get();
        } catch (RuntimeException error) {
            quotes.remove(player.getUniqueId());
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Could not prepare a Builders Wand Use restoration for " + player.getUniqueId(), error);
            player.sendMessage(Component.text("That wand's Uses could not be restored safely; no money was taken.",
                    NamedTextColor.RED));
            return;
        }
        try {
            if (!economy.has(player, quote.totalPrice())) {
                player.sendMessage(Component.text("You need " + format(quote.totalPrice())
                        + " but have " + format((long) Math.floor(economy.balance(player))) + ".",
                        NamedTextColor.RED));
                return;
            }
        } catch (RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Vault threw while checking a Builders Wand restoration balance for "
                            + player.getUniqueId(), error);
            player.sendMessage(Component.text("The economy provider failed before payment; no money was taken.",
                    NamedTextColor.RED));
            return;
        }

        quotes.remove(player.getUniqueId()); // double-confirm becomes harmless before withdrawal
        DenariiEconomy.Transaction withdrawal;
        try {
            withdrawal = economy.withdraw(player, quote.totalPrice());
        } catch (RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Vault threw while withdrawing for a Builders Wand Use restoration for "
                            + player.getUniqueId(), error);
            player.sendMessage(Component.text("The economy provider failed during payment. Check your balance and "
                    + "contact staff if it changed; no Uses were added.", NamedTextColor.RED));
            return;
        }
        if (!withdrawal.success()) {
            if (!expired(quote)) {
                quotes.put(player.getUniqueId(), quote);
            }
            player.sendMessage(Component.text("Use-restoration payment failed: " + safeError(withdrawal.error())
                    + ". No Uses were added.", NamedTextColor.RED));
            return;
        }

        boolean heldStillMatches;
        try {
            heldStillMatches = matchesQuote(player.getInventory().getItemInMainHand(), quote);
        } catch (RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Could not revalidate a paid Builders Wand Use restoration for " + player.getUniqueId(), error);
            refund(player, quote, "wand revalidation failed after payment");
            return;
        }
        if (!heldStillMatches) {
            refund(player, quote, "held wand changed during payment");
            return;
        }
        try {
            player.getInventory().setItemInMainHand(restored);
        } catch (RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Could not apply a paid Builders Wand Use restoration for " + player.getUniqueId(), error);
            if (!applied(player, quote, restoreReceipt)) {
                refund(player, quote, "wand update failed after payment");
                return;
            }
        }
        if (!applied(player, quote, restoreReceipt)) {
            refund(player, quote, "wand update could not be verified after payment");
            return;
        }
        String wandId = wandItems.identity(restored).wandId();
        try {
            buildStats.recordPaidRefill(player.getUniqueId(), player.getName(),
                    quote.addedUses(), quote.totalPrice());
        } catch (RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Unexpected failure recording paid Builders Wand restoration statistics for "
                            + player.getUniqueId(), error);
        }
        plugin.getLogger().info("Wand restoration player=" + player.getUniqueId() + " wand=" + wandId
                + " uses=" + quote.expectedUses() + "->" + newUses + " price=" + quote.totalPrice());
        try {
            double newBalance = economy.balance(player);
            player.sendMessage(Component.text("Restored +" + quote.addedUses() + " Uses for "
                    + format(quote.totalPrice()) + ". Wand: " + newUses + "/" + quote.maximumUses()
                    + ". Balance: " + format((long) Math.floor(newBalance)) + ".", NamedTextColor.GREEN));
        } catch (RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Use restoration succeeded but Vault could not format the receipt for " + player.getUniqueId(), error);
            player.sendMessage(Component.text("Restored +" + quote.addedUses() + " Uses. Wand: "
                    + newUses + "/" + quote.maximumUses() + ".", NamedTextColor.GREEN));
        }
    }

    public void cancel(Player player) {
        Quote removed = quotes.remove(player.getUniqueId());
        player.sendMessage(Component.text(removed == null ? "There is no pending Use restoration."
                : "Use restoration cancelled.", NamedTextColor.GRAY));
    }

    /** Invalidate a quote because another trusted operation changed the held wand's Uses. */
    public boolean invalidateQuote(Player player) {
        return quotes.remove(player.getUniqueId()) != null;
    }

    public void clearAll() {
        quotes.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        quotes.remove(event.getPlayer().getUniqueId());
    }

    private boolean precheck(Player player) {
        if (!player.hasPermission(WandItems.PERMISSION_REFILL)) {
            player.sendMessage(Component.text("You don't have permission to restore Builders Wand Uses.",
                    NamedTextColor.RED));
            return false;
        }
        if (waveRunner.hasActiveWave(player)) {
            player.sendMessage(Component.text("Wait for your current print to finish before restoring Uses.",
                    NamedTextColor.RED));
            return false;
        }
        if (!economy.available()) {
            player.sendMessage(Component.text("Use restoration is unavailable right now; no money was taken.",
                    NamedTextColor.RED));
            return false;
        }
        return true;
    }

    private ItemStack heldSingleWand(Player player) {
        ItemStack wand = player.getInventory().getItemInMainHand();
        if (!wandItems.isWand(wand)) {
            player.sendMessage(Component.text("Hold the Builders Wand in your main hand to restore its Uses.",
                    NamedTextColor.RED));
            return null;
        }
        if (wand.getAmount() != 1) {
            player.sendMessage(Component.text("Separate these legacy stacked wands before restoring Uses.",
                    NamedTextColor.RED));
            return null;
        }
        return wand;
    }

    private Integer parseAddedUses(Player player, String requested, int missing) {
        if (requested == null || requested.equalsIgnoreCase("all")) {
            return missing;
        }
        int added;
        try {
            added = Integer.parseInt(requested);
        } catch (NumberFormatException error) {
            player.sendMessage(Component.text("Usage: /wand restore [all|uses]", NamedTextColor.RED));
            return null;
        }
        if (added <= 0) {
            player.sendMessage(Component.text("The number of Uses to restore must be positive.", NamedTextColor.RED));
            return null;
        }
        if (added > missing) {
            player.sendMessage(Component.text("This wand only has room for " + missing
                    + " more Uses; request that amount or use 'all'.", NamedTextColor.RED));
            return null;
        }
        return added;
    }

    private boolean matchesQuote(ItemStack wand, Quote quote) {
        if (!wandItems.hasActiveToken(wand, quote.wandToken())) {
            return false;
        }
        UseCounter.State state = wandItems.uses(wand);
        return state.remaining() == quote.expectedUses() && state.maximum() == quote.maximumUses();
    }

    private boolean applied(Player player, Quote quote, WandItems.RestoreReceipt receipt) {
        try {
            ItemStack held = player.getInventory().getItemInMainHand();
            UseCounter.State state = wandItems.uses(held);
            return wandItems.hasActiveToken(held, quote.wandToken())
                    && state.remaining() == receipt.remainingAfter()
                    && state.maximum() == quote.maximumUses();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void refund(Player player, Quote quote, String reason) {
        DenariiEconomy.Transaction refund;
        try {
            refund = economy.deposit(player, quote.totalPrice());
        } catch (RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "WAND RESTORATION REFUND THREW player=" + player.getUniqueId()
                            + " price=" + quote.totalPrice() + " reason=" + reason, error);
            player.sendMessage(Component.text("Use restoration failed and the automatic refund failed. Contact staff immediately.",
                    NamedTextColor.RED));
            return;
        }
        if (refund.success()) {
            plugin.getLogger().warning("Refunded failed wand restoration player=" + player.getUniqueId()
                    + " price=" + quote.totalPrice() + " reason=" + reason);
            player.sendMessage(Component.text("The Use restoration was cancelled and your payment refunded.",
                    NamedTextColor.RED));
            return;
        }
        plugin.getLogger().severe("WAND RESTORATION REFUND FAILED player=" + player.getUniqueId()
                + " price=" + quote.totalPrice() + " reason=" + reason + " error=" + safeError(refund.error()));
        player.sendMessage(Component.text("Use restoration failed and the automatic refund failed. Contact staff immediately.",
                NamedTextColor.RED));
    }

    private static boolean expired(Quote quote) {
        return System.nanoTime() > quote.expiresAtNanos();
    }

    private static String safeError(String error) {
        return error == null || error.isBlank() ? "economy provider rejected the transaction" : error;
    }

    private String format(long amount) {
        try {
            return economy.format(amount);
        } catch (RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Vault could not format a Builders Wand restoration amount", error);
            return amount + " Denarii";
        }
    }
}
