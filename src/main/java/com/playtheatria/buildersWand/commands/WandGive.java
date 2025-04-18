package com.playtheatria.buildersWand.commands;

import com.playtheatria.buildersWand.wand.Wand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class WandGive implements CommandExecutor, TabCompleter {

    List<String> commandArgs = List.of("give", "debug");

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (sender instanceof Player player) {
            switch (args.length) {
                case 1 -> {
                    switch (args[0]) {
                        case "give" -> {
                            player.getInventory().addItem(Wand.getSquareWand());
                        }
                        case "debug" -> player.sendMessage("isWand: " + Wand.isWand(player.getInventory().getItemInMainHand()));
                        default -> {
                            player.sendMessage("Invalid argument. Usage: /wand " + commandArgs.stream());
                        }
                    }
                }
                default -> player.sendMessage("Invalid number of arguments. Usage: /wand [blocks]");
            }
            return true;
        } else {
            sender.sendMessage("This command can only be used by players.");
        }
        return false;
    }


    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (args.length == 1) {
            return commandArgs.stream()
                    .filter(arg -> arg.startsWith(args[0]))
                    .toList();
        }
        return List.of();
    }
}
