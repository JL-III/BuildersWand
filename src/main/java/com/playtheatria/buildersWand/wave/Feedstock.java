package com.playtheatria.buildersWand.wave;

import com.playtheatria.buildersWand.wand.WandItems;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Inventory feedstock accounting (design §11). Matching is by {@link Material} only, ignoring
 * item meta, and skipping any stack carrying this plugin's wand PDC (design §5.2) — hence the
 * {@link WandItems} collaborator the spec's skip clause requires.
 */
public final class Feedstock {

    private Feedstock() {
    }

    /** Count of {@code material} across storage slots 0..35 and the off hand. */
    public static int count(PlayerInventory inventory, Material material, WandItems wandItems) {
        int total = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (matches(stack, material, wandItems)) {
                total += stack.getAmount();
            }
        }
        ItemStack offhand = inventory.getItemInOffHand();
        if (matches(offhand, material, wandItems)) {
            total += offhand.getAmount();
        }
        return total;
    }

    /** Remove exactly {@code n} items of {@code material}: storage slots 0..35 first, offhand last. */
    public static void reserve(PlayerInventory inventory, Material material, int n, WandItems wandItems) {
        int remaining = n;
        ItemStack[] storage = inventory.getStorageContents();
        for (int i = 0; i < storage.length && remaining > 0; i++) {
            ItemStack stack = storage[i];
            if (!matches(stack, material, wandItems)) {
                continue;
            }
            int amount = stack.getAmount();
            int take = Math.min(remaining, amount);
            if (take >= amount) {
                storage[i] = null;
            } else {
                stack.setAmount(amount - take);
            }
            remaining -= take;
        }
        inventory.setStorageContents(storage);

        if (remaining > 0) {
            ItemStack offhand = inventory.getItemInOffHand();
            if (matches(offhand, material, wandItems)) {
                int amount = offhand.getAmount();
                int take = Math.min(remaining, amount);
                if (take >= amount) {
                    inventory.setItemInOffHand(null);
                } else {
                    offhand.setAmount(amount - take);
                    inventory.setItemInOffHand(offhand);
                }
                remaining -= take;
            }
        }
    }

    /** Return {@code n} items to the player, dropping any that do not fit (design §10.3). */
    public static void refund(Player player, Material material, int n) {
        if (n <= 0) {
            return;
        }
        int remaining = n;
        int maxStack = material.getMaxStackSize();
        List<ItemStack> stacks = new ArrayList<>();
        while (remaining > 0) {
            int chunk = Math.min(remaining, maxStack);
            stacks.add(new ItemStack(material, chunk));
            remaining -= chunk;
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stacks.toArray(new ItemStack[0]));
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private static boolean matches(ItemStack stack, Material material, WandItems wandItems) {
        return stack != null && stack.getType() == material && !wandItems.isWand(stack);
    }
}
