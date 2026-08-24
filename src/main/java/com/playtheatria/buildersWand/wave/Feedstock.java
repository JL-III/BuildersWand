package com.playtheatria.buildersWand.wave;

import com.playtheatria.buildersWand.wand.WandItems;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Inventory feedstock accounting (design §11). Matching is by {@link Material} only, ignoring
 * item meta, and skipping any stack carrying this plugin's wand PDC (design §5.2) — hence the
 * {@link WandItems} collaborator the spec's skip clause requires. Feedstock is spent one item
 * per placed cell as the wave runs (never reserved up front), so unspent items simply stay in
 * the inventory and there is nothing to refund on a stop.
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

    /**
     * Remove one {@code material} — storage slots 0..35 first, offhand last — returning true if
     * one was found and removed. Called immediately before each placement (remove-then-place),
     * so a block is never placed for free.
     */
    public static boolean spendOne(PlayerInventory inventory, Material material, WandItems wandItems) {
        ItemStack[] storage = inventory.getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            ItemStack stack = storage[i];
            if (matches(stack, material, wandItems)) {
                if (stack.getAmount() <= 1) {
                    storage[i] = null;
                } else {
                    stack.setAmount(stack.getAmount() - 1);
                }
                inventory.setStorageContents(storage);
                return true;
            }
        }
        ItemStack offhand = inventory.getItemInOffHand();
        if (matches(offhand, material, wandItems)) {
            if (offhand.getAmount() <= 1) {
                inventory.setItemInOffHand(null);
            } else {
                offhand.setAmount(offhand.getAmount() - 1);
                inventory.setItemInOffHand(offhand);
            }
            return true;
        }
        return false;
    }

    private static boolean matches(ItemStack stack, Material material, WandItems wandItems) {
        return stack != null && stack.getType() == material && !wandItems.isWand(stack);
    }
}
