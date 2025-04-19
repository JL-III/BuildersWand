package com.playtheatria.buildersWand.commands;

import com.playtheatria.buildersWand.utils.ConfigManager;
import com.playtheatria.buildersWand.utils.Err;
import com.playtheatria.buildersWand.utils.Ok;
import com.playtheatria.buildersWand.utils.Result;
import com.playtheatria.buildersWand.wand.WandData;
import com.playtheatria.buildersWand.wand.WandMode;
import com.playtheatria.buildersWand.wand.Wand;
import com.playtheatria.buildersWand.wand.WandDimensions;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class WandGive implements CommandExecutor, TabCompleter {
    private final ConfigManager configManager;

    public WandGive(ConfigManager configManager) {
        this.configManager = configManager;
    }

    List<String> commandArgs = List.of("give", "debug", "set", "speed");

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (sender instanceof Player player) {
            switch (args.length) {
                case 1 -> {
                    switch (args[0]) {
                        case "give" -> {
                            player.getInventory().addItem(Wand.getWand(WandMode.BLOCK, new WandDimensions(3, 3, 3)));
                        }
                        case "debug" -> player.sendMessage("isWand: " + Wand.getWandData(player.getInventory().getItemInMainHand()));
                        case "set" -> {
                            player.sendMessage("Usage: /wand set [mode] [x] [y] [z]");
                        }
                        default -> {
                            player.sendMessage("Invalid argument. Usage: /wand " + commandArgs.stream());
                        }
                    }
                }
                case 2 -> {
                    switch (args[0]) {
                        case "speed" -> {
                            try {
                                configManager.maxMillisecondsPerTick = Double.parseDouble(args[1]);
                            } catch (NumberFormatException ex) {
                                player.sendMessage("Invalid number format. Speed must be a double.");
                                return true;
                            }
                        }
                        default -> {
                            player.sendMessage("Invalid argument. Usage: /wand " + commandArgs.stream());
                        }
                    }

                }
                case 5 -> {
                    if (args[0].equals("set")) {
                        WandMode mode;
                        int x, y, z;
                        try {
                            mode = WandMode.valueOf(args[1].toUpperCase());
                        } catch (IllegalArgumentException e) {
                            player.sendMessage("Invalid mode. Available modes: " + Arrays.toString(WandMode.values()));
                            return true;
                        }

                        try {
                            x = Integer.parseInt(args[2]);
                            y = Integer.parseInt(args[3]);
                            z = Integer.parseInt(args[4]);
                        } catch (NumberFormatException e) {
                            player.sendMessage("Invalid number format. Dimensions must be integers.");
                            return true;
                        }

                        if (x < 1 || y < 1 || z < 1) {
                            player.sendMessage("Dimensions must be greater than 0.");
                            return true;
                        }

                        if (x > 9 || y > 9 || z > 9) {
                            player.sendMessage("Dimensions must be less than or equal to 9.");
                            return true;
                        }

                        Result<WandData, Exception> parsedWandDataExceptionResult = Wand.getWandData(player.getInventory().getItemInMainHand());
                        switch (parsedWandDataExceptionResult) {
                            case Ok<WandData, Exception> ok -> {
                                player.getInventory().setItemInMainHand(Wand.getWand(mode, new WandDimensions(x, y, z)));
                                player.sendMessage("Wand set to " + mode + " with dimensions " + x + "x" + y + "x" + z);
                            }
                            case Err<WandData, Exception> err -> {
                                player.sendMessage("You must hold a wand to set its dimensions.");
                                return true;
                            }
                        }
                    } else {
                        player.sendMessage("Invalid argument. Usage: /wand set [mode] [x] [y] [z]");
                    }
                }
                default -> player.sendMessage("Invalid argument. Usage: /wand [blocks]");
            }
            return true;
        } else {
            sender.sendMessage("This command can only be used by players.");
            return false;
        }
    }


    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        switch (args.length) {
            case 1 -> {
                return commandArgs.stream()
                        .filter(arg -> arg.startsWith(args[0]))
                        .toList();
            }
            case 2 -> {
                if (args[0].equals("set")) {
                    return Arrays.stream(WandMode.values()).toList().stream()
                            .map(WandMode::name)
                            .filter(arg -> arg.startsWith(args[1]))
                            .toList();
                }
                if (args[0].equals("speed")) {
                    return List.of("<double>");
                }
            }
            case 3,4 -> {
                if (args[0].equals("set")) {
                    return List.of("1", "2", "3", "4", "5", "6", "7", "8", "9");
                }
            }

        }
        return List.of();
    }
}
