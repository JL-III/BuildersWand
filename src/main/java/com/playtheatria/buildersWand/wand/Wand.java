package com.playtheatria.buildersWand.wand;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class Wand {

    public static boolean isWand(ItemStack itemStack) {
        return itemStack.isSimilar(getSquareWand());
    }

    public static ItemStack getSquareWand() {
        NamespacedKey theatriaKey = new NamespacedKey("theatria", "builders_wand");
        ItemStack itemStack = new ItemStack(Material.STICK);
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.itemName(Component.text("Builders Wand").color(NamedTextColor.GOLD));
        itemMeta.lore(List.of(
                Component.text("square"),
                Component.text("3x3")
        ));
//        itemMeta.setItemModel(theatriaKey);
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }
}
