package com.playtheatria.buildersWand.wave;

import com.playtheatria.buildersWand.wand.WandItems;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;
import java.util.Optional;

/**
 * Inventory feedstock accounting (design §11). Matching is by {@link Material} only, ignoring
 * item meta, and skipping any stack carrying this plugin's wand PDC (design §5.2) — hence the
 * {@link WandItems} collaborator the spec's skip clause requires. Feedstock is spent one item
 * per placed cell as the wave runs (never reserved up front), so unspent items simply stay in
 * the inventory and there is nothing to refund on a stop.
 */
public final class Feedstock {

    /** Exact one-item removal, retained only until the corresponding block is placed. */
    public record Receipt(ItemStack item, int storageSlot, boolean offhand) {
    }

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
     * Remove one {@code material} — storage slots 0..35 first, offhand last — returning an exact
     * receipt when one was found. Called immediately before each placement; the receipt supports
     * lossless rollback if the world mutation itself fails.
     */
    public static Optional<Receipt> spendOne(PlayerInventory inventory, Material material,
                                             WandItems wandItems) {
        ItemStack[] storage = inventory.getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            ItemStack stack = storage[i];
            if (matches(stack, material, wandItems)) {
                ItemStack removed = oneOf(stack);
                if (stack.getAmount() <= 1) {
                    storage[i] = null;
                } else {
                    stack.setAmount(stack.getAmount() - 1);
                }
                inventory.setStorageContents(storage);
                return Optional.of(new Receipt(removed, i, false));
            }
        }
        ItemStack offhand = inventory.getItemInOffHand();
        if (matches(offhand, material, wandItems)) {
            ItemStack removed = oneOf(offhand);
            if (offhand.getAmount() <= 1) {
                inventory.setItemInOffHand(null);
            } else {
                offhand.setAmount(offhand.getAmount() - 1);
                inventory.setItemInOffHand(offhand);
            }
            return Optional.of(new Receipt(removed, -1, true));
        }
        return Optional.empty();
    }

    /**
     * Restore an exact removal to its original slot when possible, then any storage slot.
     * Returns the still-unrestored item (at most one) so the caller can drop it recoverably.
     */
    public static ItemStack restoreOne(PlayerInventory inventory, Receipt receipt) {
        ItemStack restored = receipt.item().clone();
        if (receipt.offhand()) {
            ItemStack current = inventory.getItemInOffHand();
            ItemStack merged = merge(current, restored);
            if (merged != null) {
                inventory.setItemInOffHand(merged);
                return null;
            }
        } else {
            ItemStack[] storage = inventory.getStorageContents();
            if (receipt.storageSlot() >= 0 && receipt.storageSlot() < storage.length) {
                ItemStack merged = merge(storage[receipt.storageSlot()], restored);
                if (merged != null) {
                    storage[receipt.storageSlot()] = merged;
                    inventory.setStorageContents(storage);
                    return null;
                }
            }
        }
        Map<Integer, ItemStack> leftovers = inventory.addItem(restored);
        return leftovers.isEmpty() ? null : leftovers.values().iterator().next();
    }

    private static ItemStack oneOf(ItemStack stack) {
        ItemStack one = stack.clone();
        one.setAmount(1);
        return one;
    }

    /** Null means the exact slot cannot accept this item. */
    private static ItemStack merge(ItemStack current, ItemStack one) {
        if (current == null || current.getType().isAir()) {
            return one;
        }
        if (!current.isSimilar(one) || current.getAmount() >= current.getMaxStackSize()) {
            return null;
        }
        current.setAmount(current.getAmount() + 1);
        return current;
    }

    private static boolean matches(ItemStack stack, Material material, WandItems wandItems) {
        return stack != null && stack.getType() == material && !wandItems.isWand(stack);
    }
}
