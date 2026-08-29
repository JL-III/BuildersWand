package com.playtheatria.buildersWand.command;

import com.playtheatria.buildersWand.prefab.PrefabDefinition;
import com.playtheatria.buildersWand.prefab.PrefabService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Command-facing prefab catalog and ownership shell. Placement confirmation is delegated. */
public final class PrefabCommandHandler {

    public interface PlacementCommands {
        void confirm(Player player, String opaqueToken);

        void cancel(Player player);

        void rotate(Player player);

        static PlacementCommands unavailable() {
            return new PlacementCommands() {
                @Override
                public void confirm(Player player, String opaqueToken) {
                    player.sendMessage(Component.text(
                            "There is no prefab placement awaiting confirmation.",
                            NamedTextColor.RED
                    ));
                }

                @Override
                public void cancel(Player player) {
                    // Selection clearing remains useful before the placement bridge is installed.
                }

                @Override
                public void rotate(Player player) {
                    player.sendMessage(Component.text(
                            "There is no anchored prefab to rotate.", NamedTextColor.YELLOW));
                }
            };
        }
    }

    private static final String PLAYER_USAGE =
            "Usage: /wand prefab [<id>|redeem|rotate|cancel|confirm <token>]";
    private static final String ADMIN_USAGE =
            "Admin: /wand prefab <grant|revoke|voucher|validate|reload> ...";

    private final PrefabService prefabs;
    private PlacementCommands placementCommands = PlacementCommands.unavailable();

    public PrefabCommandHandler(PrefabService prefabs) {
        this.prefabs = Objects.requireNonNull(prefabs, "prefabs");
    }

    public void setPlacementCommands(PlacementCommands placementCommands) {
        this.placementCommands = placementCommands == null
                ? PlacementCommands.unavailable()
                : placementCommands;
    }

    /** Silent exit used when the player deliberately selects a normal wand form. */
    public void exitPlacement(Player player) {
        placementCommands.cancel(player);
        prefabs.clearSelection(player);
    }

    /** Handles arguments after the {@code prefab} root. */
    public boolean execute(CommandSender sender, String[] args) {
        Objects.requireNonNull(sender, "sender");
        args = args == null ? new String[0] : args;
        if (args.length == 0) {
            return list(sender);
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "redeem" -> redeem(sender, args);
            case "confirm" -> confirm(sender, args);
            case "cancel" -> cancel(sender, args);
            case "rotate" -> rotate(sender, args);
            case "grant" -> grant(sender, args);
            case "revoke" -> revoke(sender, args);
            case "voucher" -> voucher(sender, args);
            case "validate" -> validate(sender, args);
            case "reload" -> reload(sender, args);
            default -> select(sender, args);
        };
    }

    /** Completes arguments after the {@code prefab} root. */
    public List<String> tabComplete(CommandSender sender, String[] args) {
        args = args == null ? new String[0] : args;
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of(
                    "redeem", "rotate", "cancel", "confirm"));
            if (sender instanceof Player player) {
                PrefabService.AccessiblePrefabs accessible = prefabs.accessiblePrefabs(player);
                if (accessible.available()) {
                    accessible.prefabs().forEach(definition -> options.add(definition.id()));
                }
            }
            if (sender.hasPermission(PrefabService.PERMISSION_ADMIN)) {
                options.addAll(List.of("grant", "revoke", "voucher", "validate", "reload"));
            }
            return filter(options, args[0]);
        }
        if (!sender.hasPermission(PrefabService.PERMISSION_ADMIN)) {
            return List.of();
        }
        if (args.length == 2 && switch (args[0].toLowerCase(Locale.ROOT)) {
            case "grant", "revoke", "voucher" -> true;
            default -> false;
        }) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("validate")) {
            return prefabs.available()
                    ? filter(prefabs.catalogSnapshot().prefabs().keySet(), args[1])
                    : List.of();
        }
        if (args.length == 3 && switch (args[0].toLowerCase(Locale.ROOT)) {
            case "grant", "revoke", "voucher" -> true;
            default -> false;
        }) {
            return prefabs.available()
                    ? filter(prefabs.catalogSnapshot().prefabs().keySet(), args[2])
                    : List.of();
        }
        return List.of();
    }

    private boolean list(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(PLAYER_USAGE, NamedTextColor.RED));
            sender.sendMessage(Component.text(ADMIN_USAGE, NamedTextColor.GRAY));
            return true;
        }
        PrefabService.AccessiblePrefabs accessible = prefabs.accessiblePrefabs(player);
        if (!accessible.available()) {
            sender.sendMessage(Component.text(
                    prefabs.enabled()
                            ? "Prefab access is temporarily unavailable."
                            : "Prefabs are disabled on this server.",
                    NamedTextColor.RED
            ));
            return true;
        }
        if (accessible.prefabs().isEmpty()) {
            sender.sendMessage(Component.text(
                    "You have not unlocked any prefab designs yet.",
                    NamedTextColor.YELLOW
            ));
            return true;
        }
        sender.sendMessage(Component.text("Your Prefab Designs", NamedTextColor.GOLD));
        for (PrefabDefinition definition : accessible.prefabs()) {
            sender.sendMessage(Component.text("• " + definition.id(), NamedTextColor.GREEN)
                    .append(Component.text(" — " + definition.metadata().name()
                            + " · " + definition.metadata().activationUses()
                            + " activation Uses", NamedTextColor.GRAY)));
        }
        sender.sendMessage(Component.text(
                "Select one with /wand prefab <id> while your wand is in your offhand.",
                NamedTextColor.GRAY
        ));
        return true;
    }

    private boolean select(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(Component.text(PLAYER_USAGE, NamedTextColor.RED));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only a player can select a prefab.", NamedTextColor.RED));
            return true;
        }
        PrefabService.SelectionResult result = prefabs.select(player, args[0]);
        switch (result.status()) {
            case SELECTED -> {
                PrefabDefinition definition = result.definition().orElseThrow();
                sender.sendMessage(Component.text(
                        "Selected " + definition.metadata().name() + ".",
                        NamedTextColor.GREEN
                ));
                sender.sendMessage(Component.text(
                        "Right-click a block face to set its lower-left-front anchor.",
                        NamedTextColor.GRAY
                ));
            }
            case LOCKED -> sender.sendMessage(Component.text(
                    "You have not unlocked that prefab design.", NamedTextColor.RED));
            case NOT_FOUND -> sender.sendMessage(Component.text(
                    "Unknown prefab design: " + args[0], NamedTextColor.RED));
            case NOT_HOLDING_WAND -> sender.sendMessage(Component.text(
                    "Put one Builders Wand in your offhand before selecting a prefab.",
                    NamedTextColor.RED));
            case NO_WAND_PERMISSION -> sender.sendMessage(Component.text(
                    "You don't have permission to use the Builders Wand.", NamedTextColor.RED));
            case UNAVAILABLE -> sender.sendMessage(Component.text(
                    "Prefab access is temporarily unavailable.", NamedTextColor.RED));
            case DISABLED -> sender.sendMessage(Component.text(
                    "Prefabs are disabled on this server.", NamedTextColor.RED));
        }
        return true;
    }

    private boolean redeem(CommandSender sender, String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            sender.sendMessage(Component.text(
                    "Hold a Blueprint Voucher in your main hand and use /wand prefab redeem.",
                    NamedTextColor.RED
            ));
            return true;
        }
        PrefabService.RedeemOutcome result = prefabs.redeem(player);
        sender.sendMessage(switch (result) {
            case REDEEMED -> Component.text(
                    "Blueprint redeemed. The prefab is now permanently unlocked.",
                    NamedTextColor.GREEN);
            case NOT_A_VOUCHER -> Component.text(
                    "Hold a Blueprint Voucher in your main hand first.", NamedTextColor.RED);
            case NOT_FOUND -> Component.text(
                    "That voucher refers to a prefab that is not currently available.",
                    NamedTextColor.RED);
            case ALREADY_UNLOCKED -> Component.text(
                    "You already own that prefab. The voucher was not consumed.",
                    NamedTextColor.YELLOW);
            case UNAVAILABLE -> Component.text(
                    "Prefab ownership is temporarily unavailable. The voucher was not consumed.",
                    NamedTextColor.RED);
            case DISABLED -> Component.text(
                    "Prefabs are disabled. The voucher was not consumed.", NamedTextColor.RED);
            case UNLOCKED_BUT_NOT_CONSUMED -> Component.text(
                    "The prefab was unlocked, but the voucher could not be removed. "
                            + "It cannot be redeemed again; please contact staff.",
                    NamedTextColor.YELLOW);
        });
        return true;
    }

    private boolean confirm(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || args.length != 2) {
            sender.sendMessage(Component.text(
                    "Usage: /wand prefab confirm <token>", NamedTextColor.RED));
            return true;
        }
        placementCommands.confirm(player, args[1]);
        return true;
    }

    private boolean cancel(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) {
            sender.sendMessage(Component.text("Usage: /wand prefab cancel", NamedTextColor.RED));
            return true;
        }
        placementCommands.cancel(player);
        boolean selected = prefabs.clearSelection(player);
        sender.sendMessage(Component.text(
                selected ? "Prefab preview cancelled." : "No prefab preview was selected.",
                selected ? NamedTextColor.GREEN : NamedTextColor.YELLOW
        ));
        return true;
    }

    private boolean rotate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) {
            sender.sendMessage(Component.text("Usage: /wand prefab rotate", NamedTextColor.RED));
            return true;
        }
        placementCommands.rotate(player);
        return true;
    }

    private boolean grant(CommandSender sender, String[] args) {
        if (!requireAdmin(sender) || args.length != 3) {
            sender.sendMessage(Component.text(
                    "Usage: /wand prefab grant <player> <id>", NamedTextColor.RED));
            return true;
        }
        Optional<OfflinePlayer> resolved = resolvePlayer(args[1]);
        if (resolved.isEmpty()) {
            sender.sendMessage(Component.text(
                    "Unknown player: " + args[1] + ". They must have joined this server before.",
                    NamedTextColor.RED));
            return true;
        }
        OfflinePlayer target = resolved.get();
        PrefabService.GrantOutcome result = prefabs.grant(
                target.getUniqueId(), args[2], "admin-command:" + sender.getName(), sender.getName()
        );
        sender.sendMessage(switch (result) {
            case GRANTED -> Component.text("Unlocked " + args[2] + " for "
                    + displayName(target) + ".", NamedTextColor.GREEN);
            case ALREADY_UNLOCKED -> Component.text(displayName(target)
                    + " already owns " + args[2] + ".", NamedTextColor.YELLOW);
            case NOT_FOUND -> Component.text("Unknown prefab design: " + args[2], NamedTextColor.RED);
            case UNAVAILABLE -> Component.text("Prefab ownership is unavailable.", NamedTextColor.RED);
            case DISABLED -> Component.text("Prefabs are disabled.", NamedTextColor.RED);
        });
        return true;
    }

    private boolean revoke(CommandSender sender, String[] args) {
        if (!requireAdmin(sender) || args.length != 3) {
            sender.sendMessage(Component.text(
                    "Usage: /wand prefab revoke <player> <id>", NamedTextColor.RED));
            return true;
        }
        Optional<OfflinePlayer> resolved = resolvePlayer(args[1]);
        if (resolved.isEmpty()) {
            sender.sendMessage(Component.text(
                    "Unknown player: " + args[1] + ". They must have joined this server before.",
                    NamedTextColor.RED));
            return true;
        }
        OfflinePlayer target = resolved.get();
        PrefabService.RevokeOutcome result = prefabs.revoke(
                target.getUniqueId(), args[2], sender.getName()
        );
        sender.sendMessage(switch (result) {
            case REVOKED -> Component.text("Revoked " + args[2] + " from "
                    + displayName(target) + ".", NamedTextColor.GREEN);
            case NOT_UNLOCKED -> Component.text(displayName(target)
                    + " did not own " + args[2] + ".", NamedTextColor.YELLOW);
            case INVALID_ID -> Component.text("Invalid prefab ID: " + args[2], NamedTextColor.RED);
            case UNAVAILABLE -> Component.text("Prefab ownership is unavailable.", NamedTextColor.RED);
            case DISABLED -> Component.text("Prefabs are disabled.", NamedTextColor.RED);
        });
        return true;
    }

    private boolean voucher(CommandSender sender, String[] args) {
        if (!requireAdmin(sender) || args.length != 3) {
            sender.sendMessage(Component.text(
                    "Usage: /wand prefab voucher <player> <id>", NamedTextColor.RED));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text(
                    "That player must be online to receive a voucher.", NamedTextColor.RED));
            return true;
        }
        PrefabService.VoucherIssue result = prefabs.createVoucher(
                target.getUniqueId(), args[2], sender.getName()
        );
        if (result.outcome() == PrefabService.VoucherIssueOutcome.ISSUED) {
            ItemStack voucher = result.voucher().orElseThrow();
            target.getInventory().addItem(voucher).values().forEach(leftover ->
                    target.getWorld().dropItemNaturally(target.getLocation(), leftover));
            sender.sendMessage(Component.text("Gave a " + args[2]
                    + " Blueprint Voucher to " + target.getName() + ".", NamedTextColor.GREEN));
            return true;
        }
        sender.sendMessage(switch (result.outcome()) {
            case NOT_FOUND -> Component.text("Unknown prefab design: " + args[2], NamedTextColor.RED);
            case UNAVAILABLE -> Component.text("Prefab ownership is unavailable.", NamedTextColor.RED);
            case DISABLED -> Component.text("Prefabs are disabled.", NamedTextColor.RED);
            case ISSUED -> throw new IllegalStateException("unreachable");
        });
        return true;
    }

    private boolean validate(CommandSender sender, String[] args) {
        if (!requireAdmin(sender) || args.length != 2) {
            sender.sendMessage(Component.text(
                    "Usage: /wand prefab validate <id>", NamedTextColor.RED));
            return true;
        }
        PrefabService.ValidationReport report = prefabs.validate(args[1]);
        if (!report.success()) {
            sender.sendMessage(Component.text("Prefab validation failed:", NamedTextColor.RED));
            report.issues().forEach(issue -> sender.sendMessage(
                    Component.text("• " + issue, NamedTextColor.RED)));
            return true;
        }
        PrefabDefinition definition = report.definition().orElseThrow();
        sender.sendMessage(Component.text("Prefab is valid: " + definition.id(), NamedTextColor.GREEN));
        sender.sendMessage(Component.text("Name: " + definition.metadata().name()
                + " · version " + definition.metadata().version(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Dimensions: " + definition.dimensions().width() + "x"
                + definition.dimensions().height() + "x" + definition.dimensions().depth()
                + " · blocks " + definition.placementCells().size()
                + " · clearance " + definition.clearanceCells().size(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Inferred source anchor: "
                + definition.inferredSourceAnchor() + " · activation "
                + definition.metadata().activationUses() + " Uses", NamedTextColor.GRAY));
        return true;
    }

    private boolean reload(CommandSender sender, String[] args) {
        if (!requireAdmin(sender) || args.length != 1) {
            sender.sendMessage(Component.text("Usage: /wand prefab reload", NamedTextColor.RED));
            return true;
        }
        var result = prefabs.reload(sender.getName());
        if (result.success()) {
            sender.sendMessage(Component.text("Loaded prefab catalog generation "
                    + result.liveSnapshot().generation() + " with "
                    + result.liveSnapshot().prefabs().size() + " design(s).", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text(
                    "Prefab reload failed; the previous catalog is still active.",
                    NamedTextColor.RED
            ));
            result.issues().forEach(issue -> sender.sendMessage(
                    Component.text("• " + issue, NamedTextColor.RED)));
        }
        return true;
    }

    private static boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission(PrefabService.PERMISSION_ADMIN)) {
            return true;
        }
        sender.sendMessage(Component.text(
                "You don't have permission to administer prefabs.", NamedTextColor.RED));
        return false;
    }

    private static Optional<OfflinePlayer> resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        return Optional.ofNullable(online == null ? Bukkit.getOfflinePlayerIfCached(name) : online);
    }

    private static String displayName(OfflinePlayer player) {
        return Optional.ofNullable(player.getName()).orElse(player.getUniqueId().toString());
    }

    private static List<String> filter(Collection<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower))
                .distinct()
                .sorted()
                .toList();
    }
}
