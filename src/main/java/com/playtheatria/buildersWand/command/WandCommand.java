package com.playtheatria.buildersWand.command;

import com.playtheatria.buildersWand.form.Form;
import com.playtheatria.buildersWand.stats.BuildStatsStore;
import com.playtheatria.buildersWand.stats.PlayerBuildStats;
import com.playtheatria.buildersWand.wand.PrintMaterial;
import com.playtheatria.buildersWand.wand.UseCounter;
import com.playtheatria.buildersWand.wand.WandItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@code /wand give|form|restore|stats|setuses} plus bare {@code /wand}. Setting a form or
 * administratively changing Uses clears the target's live gesture.
 */
public final class WandCommand implements CommandExecutor, TabCompleter {

    private static final String USAGE =
            "Usage: /wand [stats|give|form <shape>|restore [all|uses|confirm|cancel]]";
    private static final String SET_USES_USAGE = "Usage: /wand setuses <uses>";
    private static final List<String> FORM_KEYS =
            Arrays.stream(Form.values()).map(Form::key).toList();

    private final WandItems wandItems;
    private final RefillService refillService;
    private final BuildStatsStore buildStats;
    private final Consumer<Player> gestureClearer;
    private final Predicate<Player> activeWave;
    private final Logger auditLogger;

    public WandCommand(WandItems wandItems, RefillService refillService, BuildStatsStore buildStats,
                       Consumer<Player> gestureClearer, Predicate<Player> activeWave,
                       Logger auditLogger) {
        this.wandItems = wandItems;
        this.refillService = refillService;
        this.buildStats = buildStats;
        this.gestureClearer = gestureClearer;
        this.activeWave = activeWave;
        this.auditLogger = auditLogger;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return info(sender);
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> give(sender, args);
            case "form" -> form(sender, args);
            case "restore", "refill", "recharge" -> restore(sender, args);
            case "stats" -> stats(sender, args);
            case "setuses" -> setUses(sender, args);
            default -> info(sender);
        };
    }

    private boolean give(CommandSender sender, String[] args) {
        if (!sender.hasPermission(WandItems.PERMISSION_GIVE)) {
            sender.sendMessage(Component.text("You don't have permission to use the Builders Wand.", NamedTextColor.RED));
            return true;
        }
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found: " + args[1], NamedTextColor.RED));
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(Component.text("Specify a player: /wand give <player>", NamedTextColor.RED));
            return true;
        }
        ItemStack wand = wandItems.createWand(Form.DEFAULT);
        target.getInventory().addItem(wand).values()
                .forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
        sender.sendMessage(Component.text("Gave a Builders Wand to " + target.getName() + ".", NamedTextColor.GREEN));
        return true;
    }

    private boolean setUses(CommandSender sender, String[] args) {
        if (!sender.hasPermission(WandItems.PERMISSION_ADMIN_SET_USES)) {
            sender.sendMessage(Component.text("You don't have permission to set Builders Wand Uses.",
                    NamedTextColor.RED));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(
                    "Only an in-game administrator holding a Builders Wand can set its Uses.",
                    NamedTextColor.RED));
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage(Component.text(SET_USES_USAGE, NamedTextColor.RED));
            return true;
        }

        int requested;
        try {
            requested = Integer.parseInt(args[1]);
        } catch (NumberFormatException error) {
            sender.sendMessage(Component.text("Uses must be a whole number.", NamedTextColor.RED));
            sender.sendMessage(Component.text(SET_USES_USAGE, NamedTextColor.RED));
            return true;
        }
        if (requested < 0) {
            sender.sendMessage(Component.text("Uses cannot be negative.", NamedTextColor.RED));
            return true;
        }

        if (activeWave.test(player)) {
            sender.sendMessage(Component.text(
                    "Wait for your active wand print to finish before changing its Uses.",
                    NamedTextColor.RED));
            return true;
        }
        ItemStack wand = player.getInventory().getItemInMainHand();
        if (!wandItems.isWand(wand)) {
            sender.sendMessage(Component.text(
                    "Hold the Builders Wand whose Uses should be changed in your main hand.",
                    NamedTextColor.RED));
            return true;
        }
        if (wand.getAmount() != 1) {
            sender.sendMessage(Component.text(
                    "Separate these legacy stacked wands before changing Uses.", NamedTextColor.RED));
            return true;
        }

        UseCounter.State current = wandItems.uses(wand);
        if (requested > current.maximum()) {
            sender.sendMessage(Component.text("That wand accepts 0–" + current.maximum()
                    + " Uses; " + requested + " is too high.", NamedTextColor.RED));
            return true;
        }

        ItemStack replacement = wand.clone();
        Optional<UseCounter.State> updated;
        try {
            updated = wandItems.setRemainingUses(replacement, requested);
        } catch (RuntimeException error) {
            auditLogger.log(Level.SEVERE, "Could not prepare an administrative wand Uses change for "
                    + player.getUniqueId(), error);
            sender.sendMessage(Component.text("The held wand's Uses could not be changed safely.",
                    NamedTextColor.RED));
            return true;
        }
        if (updated.isEmpty()) {
            sender.sendMessage(Component.text("The held wand's Uses could not be changed safely.",
                    NamedTextColor.RED));
            return true;
        }
        try {
            player.getInventory().setItemInMainHand(replacement);
        } catch (RuntimeException error) {
            auditLogger.log(Level.SEVERE, "Could not apply an administrative wand Uses change for "
                    + player.getUniqueId(), error);
            sender.sendMessage(Component.text("The held wand's Uses could not be changed safely.",
                    NamedTextColor.RED));
            return true;
        }
        boolean quoteInvalidated = refillService.invalidateQuote(player);
        try {
            gestureClearer.accept(player);
        } catch (RuntimeException error) {
            auditLogger.log(Level.WARNING, "Changed administrative wand Uses but could not clear the gesture for "
                    + player.getUniqueId(), error);
        }

        UseCounter.State state = updated.get();
        WandItems.Identity identity = wandItems.identity(replacement);
        auditLogger.info("Administrative wand Uses change administrator=" + sender.getName()
                + " holder=" + player.getUniqueId() + " wand="
                + (identity.wandId() == null ? "unclaimed" : identity.wandId())
                + " uses=" + current.remaining() + "->" + state.remaining()
                + " maximum=" + state.maximum());
        sender.sendMessage(Component.text("Set the held Builders Wand Uses from "
                + current.remaining() + "/" + current.maximum() + " to "
                + state.remaining() + "/" + state.maximum()
                + ". Lifetime stats were not changed.", NamedTextColor.GREEN));
        if (quoteInvalidated) {
            sender.sendMessage(Component.text("Cancelled your pending Use-restoration quote.",
                    NamedTextColor.YELLOW));
        }
        return true;
    }

    private boolean form(CommandSender sender, String[] args) {
        if (!sender.hasPermission(WandItems.PERMISSION_USE)) {
            sender.sendMessage(Component.text("You don't have permission to use the Builders Wand.", NamedTextColor.RED));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Hold the Builders Wand to set its form.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text(USAGE, NamedTextColor.RED));
            return true;
        }
        Optional<Form> form = Form.fromKey(args[1]);
        if (form.isEmpty()) {
            sender.sendMessage(Component.text("Unknown form: " + args[1], NamedTextColor.RED));
            return true;
        }
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (!wandItems.isWand(mainHand)) {
            sender.sendMessage(Component.text("Hold the Builders Wand to set its form.", NamedTextColor.RED));
            return true;
        }
        if (mainHand.getAmount() != 1) {
            sender.sendMessage(Component.text("Separate these legacy stacked wands before changing form.",
                    NamedTextColor.RED));
            return true;
        }
        wandItems.ensureFirstWielder(mainHand, player);
        wandItems.setForm(mainHand, form.get());
        player.getInventory().setItemInMainHand(mainHand);
        gestureClearer.accept(player);
        sender.sendMessage(Component.text("Form set to " + form.get().key() + ".", NamedTextColor.GREEN));
        return true;
    }

    private boolean restore(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only a player can restore Uses on a held Builders Wand.",
                    NamedTextColor.RED));
            return true;
        }
        if (args.length == 1) {
            refillService.quote(player, null);
            return true;
        }
        if (args.length > 3) {
            sender.sendMessage(Component.text(USAGE, NamedTextColor.RED));
            return true;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "confirm" -> refillService.confirm(player, args.length == 3 ? args[2] : null);
            case "cancel" -> {
                if (args.length == 2) {
                    refillService.cancel(player);
                } else {
                    sender.sendMessage(Component.text(USAGE, NamedTextColor.RED));
                }
            }
            case "all" -> {
                if (args.length == 2) {
                    refillService.quote(player, "all");
                } else {
                    sender.sendMessage(Component.text(USAGE, NamedTextColor.RED));
                }
            }
            default -> {
                if (args.length == 2) {
                    refillService.quote(player, args[1]);
                } else {
                    sender.sendMessage(Component.text(USAGE, NamedTextColor.RED));
                }
            }
        }
        return true;
    }

    private boolean info(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(USAGE, NamedTextColor.GRAY));
            return true;
        }
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (!wandItems.isWand(mainHand)) {
            sender.sendMessage(Component.text(
                    "Not holding a Builders Wand. Hold one to inspect it, or use /wand stats for your lifetime totals.",
                    NamedTextColor.GRAY));
            sender.sendMessage(Component.text(USAGE, NamedTextColor.GRAY));
            return true;
        }
        sender.sendMessage(Component.text("Held Builders Wand", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Form: " + wandItems.getForm(mainHand).label(), NamedTextColor.GRAY));
        UseCounter.State uses = wandItems.initializeUses(mainHand);
        player.getInventory().setItemInMainHand(mainHand);
        String bypassSuffix = player.hasPermission(WandItems.PERMISSION_USES_BYPASS) ? " (bypassed)" : "";
        sender.sendMessage(Component.text("Uses: " + uses.remaining() + "/" + uses.maximum()
                + bypassSuffix, uses.depleted() ? NamedTextColor.RED : NamedTextColor.GRAY));
        WandItems.Identity identity = wandItems.identity(mainHand);
        sender.sendMessage(Component.text("First wielded by: "
                + (identity.firstWielderName() == null ? "Unwielded" : identity.firstWielderName()),
                NamedTextColor.GRAY));
        if (identity.wandId() != null) {
            sender.sendMessage(Component.text("Wand serial: " + shortSerial(identity.wandId()),
                    NamedTextColor.DARK_GRAY));
        }
        Optional<PrintMaterial> material = wandItems.selectedMaterial(player);
        if (material.isPresent()) {
            String materialSuffix = material.get().reusable() ? " (reusable bucket)" : "";
            sender.sendMessage(Component.text("Material: "
                    + WandItems.materialDisplayName(material.get().placedBlock()) + materialSuffix,
                    NamedTextColor.GRAY));
        } else {
            sender.sendMessage(Component.text(WandItems.MATERIAL_HINT, NamedTextColor.GRAY));
        }
        sender.sendMessage(Component.text(USAGE, NamedTextColor.GRAY));
        return true;
    }

    private boolean stats(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(Component.text("Usage: /wand stats", NamedTextColor.RED));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only a player can view lifetime builder stats.",
                    NamedTextColor.RED));
            return true;
        }
        if (!buildStats.isAvailable()) {
            sender.sendMessage(Component.text("Lifetime builder stats are temporarily unavailable.",
                    NamedTextColor.RED));
            return true;
        }
        sender.sendMessage(Component.text("Your Lifetime Builder Stats (all wands)", NamedTextColor.GOLD));
        buildStats.findPlayer(player.getUniqueId())
                .ifPresentOrElse(stats -> sendPlayerStats(sender, stats),
                        () -> sendZeroPlayerStats(sender));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> roots = new ArrayList<>(List.of("give", "form", "restore", "stats"));
            if (sender.hasPermission(WandItems.PERMISSION_ADMIN_SET_USES)) {
                roots.add("setuses");
            }
            return filter(roots, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("form")) {
            return filter(FORM_KEYS, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("restore")) {
            return filter(List.of("all", "confirm", "cancel"), args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }

    private static String shortSerial(String serial) {
        return serial.length() <= 8 ? serial : serial.substring(0, 8);
    }

    private static void sendPlayerStats(CommandSender sender, PlayerBuildStats stats) {
        sender.sendMessage(Component.text("Uses spent: " + format(stats.totalUses()), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Blocks placed: " + format(stats.totalBlocksPlaced()) + " ("
                + format(stats.waterSourceBlocksPlaced()) + " water sources)", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Prints completed: " + format(stats.printsCompleted())
                + " · Largest completed print: " + format(stats.largestCompletedPrint()) + " cells",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Uses restored: " + format(stats.refillUsesPurchased())
                + " across " + format(stats.refillsCompleted()) + " "
                + (stats.refillsCompleted() == 1L ? "purchase" : "purchases") + " · "
                + format(stats.refillDenariiSpent()) + " Denarii spent", NamedTextColor.DARK_GRAY));
    }

    private static void sendZeroPlayerStats(CommandSender sender) {
        sender.sendMessage(Component.text("Uses spent: 0", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Blocks placed: 0 (0 water sources)", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                "Prints completed: 0 · Largest completed print: 0 cells", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                "Uses restored: 0 across 0 purchases · 0 Denarii spent", NamedTextColor.DARK_GRAY));
    }

    private static String format(long value) {
        return String.format(Locale.US, "%,d", value);
    }
}
