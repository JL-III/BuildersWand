package com.playtheatria.buildersWand.command;

import com.playtheatria.buildersWand.form.Form;
import com.playtheatria.buildersWand.wand.WandItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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

/**
 * {@code /wand give|form} plus bare {@code /wand} (design §14.1). Setting a form clears the
 * sender's live gesture (via the injected clearer, wired to the gesture listener).
 */
public final class WandCommand implements CommandExecutor, TabCompleter {

    private static final String USAGE = "Usage: /wand [give|form <diagonal|box|cylinder|sphere>]";
    private static final List<String> FORM_KEYS =
            Arrays.stream(Form.values()).map(Form::key).toList();

    private final WandItems wandItems;
    private final Consumer<Player> gestureClearer;

    public WandCommand(WandItems wandItems, Consumer<Player> gestureClearer) {
        this.wandItems = wandItems;
        this.gestureClearer = gestureClearer;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return info(sender);
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> give(sender, args);
            case "form" -> form(sender, args);
            default -> info(sender);
        };
    }

    private boolean give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("builderswand.give")) {
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

    private boolean form(CommandSender sender, String[] args) {
        if (!sender.hasPermission("builderswand.use")) {
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
        wandItems.setForm(mainHand, form.get());
        gestureClearer.accept(player);
        sender.sendMessage(Component.text("Form set to " + form.get().key() + ".", NamedTextColor.GREEN));
        return true;
    }

    private boolean info(CommandSender sender) {
        if (sender instanceof Player player) {
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            if (wandItems.isWand(mainHand)) {
                sender.sendMessage(Component.text("Form: " + wandItems.getForm(mainHand).label(), NamedTextColor.GOLD));
            } else {
                sender.sendMessage(Component.text("Not holding a Builders Wand.", NamedTextColor.GRAY));
            }
            Optional<Material> material = wandItems.selectedMaterial(player);
            if (material.isPresent()) {
                sender.sendMessage(Component.text("Material: " + WandItems.materialDisplayName(material.get()), NamedTextColor.GRAY));
            } else {
                sender.sendMessage(Component.text("Hold a placeable block in your off hand to choose the material.", NamedTextColor.GRAY));
            }
        }
        sender.sendMessage(Component.text(USAGE, NamedTextColor.GRAY));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("give", "form"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("form")) {
            return filter(FORM_KEYS, args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
