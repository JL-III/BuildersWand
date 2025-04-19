package com.playtheatria.buildersWand.wand;

import com.playtheatria.buildersWand.utils.Err;
import com.playtheatria.buildersWand.utils.Ok;
import com.playtheatria.buildersWand.utils.Result;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Wand {

    private static final Pattern MODE_PATTERN = Pattern.compile("Mode:\\s*(\\w+)");
    private static final Pattern DIMENSIONS_PATTERN = Pattern.compile("^(-?\\d+)x(-?\\d+)x(-?\\d+)$");


    public static Result<WandData, Exception> getWandData(ItemStack itemStack) {
        Result<WandMode, Exception> modeResult = parseWandMode(itemStack);
        Result<WandDimensions, Exception> dimensionsResult = parseWandDimensions(itemStack);

        if (modeResult instanceof Err<WandMode, Exception> err) {
            return new Err<>(err.error());
        }

        if (dimensionsResult instanceof Err<WandDimensions, Exception> err) {
            return new Err<>(err.error());
        }

        WandMode mode = ((Ok<WandMode, Exception>) modeResult).value();
        WandDimensions dimensions = ((Ok<WandDimensions, Exception>) dimensionsResult).value();

        return new Ok<>(new WandData(itemStack, mode, dimensions));
    }

    public static ItemStack getWand(WandMode wandMode, WandDimensions wandDimensions) {
        ItemStack itemStack = new ItemStack(Material.STICK);
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.itemName(Component.text("Builders Wand").color(NamedTextColor.GOLD));
        itemMeta.lore(List.of(
                Component.text("A mystical wand!"),
                Component.text(String.format("Mode: %s", wandMode.name())),
                Component.text(String.format("%sx%sx%s", wandDimensions.x, wandDimensions.y, wandDimensions.z))
        ));
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    public static Result<WandMode, Exception> parseWandMode(ItemStack itemStack) {
        Result<String, Exception> parseStringFromWandLore = parseStringFromWandLore(itemStack, 1);
        switch (parseStringFromWandLore) {
            case Ok<String, Exception> ok -> {
                String lore = ok.value();
                Matcher matcher = MODE_PATTERN.matcher(lore);
                if (matcher.find()) {
                    String modeText = matcher.group(1);
                    try {
                        WandMode mode = WandMode.valueOf(modeText);
                        return new Ok<>(mode);
                    } catch (IllegalArgumentException ex) {
                        return new Err<>(new Exception("Invalid WandMode: " + modeText));
                    }
                } else {
                    return new Err<>(new Exception("Could not parse wand mode"));
                }
            }
            case Err<String, Exception> err -> {
                return new Err<>(err.error());
            }
        }
    }

    public static Result<WandDimensions, Exception> parseWandDimensions(ItemStack itemStack) {
        Result<String, Exception> parseStringFromWandLore = parseStringFromWandLore(itemStack, 2);
        switch (parseStringFromWandLore) {
            case Ok<String, Exception> ok -> {
                String lore = ok.value();
                Matcher matcher = DIMENSIONS_PATTERN.matcher(lore);
                if (matcher.matches()) {
                    return new Ok<>(new WandDimensions(
                            Integer.parseInt(matcher.group(1)),
                            Integer.parseInt(matcher.group(2)),
                            Integer.parseInt(matcher.group(3))
                    )
                    );
                } else {
                    return new Err<>(new Exception("Could not parse wand dimensions"));
                }
            }
            case Err<String, Exception> err -> {
                return new Err<>(err.error());
            }
        }
    }

    public static Result<String, Exception> parseStringFromWandLore(ItemStack itemStack, int index) {
        if (!itemStack.hasItemMeta()) return new Err<>(new Exception("Item does not have item meta"));
        if (!itemStack.getItemMeta().hasLore()) return new Err<>(new Exception("Item does not have lore"));
        ItemMeta itemMeta = itemStack.getItemMeta();
        List<Component> loreList = itemMeta.lore();
        if (loreList == null) return new Err<>(new Exception("Lore list is null"));
        if (loreList.size() < 3) return new Err<>(new Exception(String.format("Lore list is not large enough to be a builder wand. Size: %s", loreList.size())));
        return new Ok<>(PlainTextComponentSerializer.plainText().serialize(loreList.get(index)));
    }
}
