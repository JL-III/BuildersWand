package com.playtheatria.buildersWand.wave;

import com.playtheatria.buildersWand.wand.PrintMaterial;
import com.playtheatria.buildersWand.wand.WandItems;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Inventory feedstock accounting. Every supported block stack in hotbar slots 0–8 contributes
 * one protected palette sample. Backups in slots 9–35 are consumed first, followed by hotbar
 * stack surplus, and the last sample in every participating slot is never consumed.
 */
public final class Feedstock {

    private static final int HOTBAR_SIZE = 9;

    /** Exact one-item removal, retained only until the corresponding block is placed. */
    public record Receipt(ItemStack item, int storageSlot) {
    }

    /** Pure inventory projection used by the reservation policy and its unit tests. */
    record SupplySlot(int slot, int amount) {
        SupplySlot {
            if (slot < 0 || amount < 1) {
                throw new IllegalArgumentException("a supply slot needs a non-negative slot and positive amount");
            }
        }
    }

    private Feedstock() {
    }

    /** Available amount under the selected material's consumed-versus-retained policy. */
    public static int available(PlayerInventory inventory, PrintMaterial material,
                                WandItems wandItems) {
        return material.reusable()
                ? countAll(inventory, material.sourceItem(), wandItems)
                : countConsumable(inventory, material.sourceItem(), wandItems);
    }

    /** Total matching items in storage. Used for reusable catalysts such as a water bucket. */
    public static int countAll(PlayerInventory inventory, Material material, WandItems wandItems) {
        return countAll(inventory.getStorageContents(), material, wandItems);
    }

    /**
     * Consumable count after reserving one matching sample in each occupied hotbar slot.
     * Stack amounts do not affect palette weight, but amounts above one remain valid feedstock.
     */
    public static int countConsumable(PlayerInventory inventory, Material material,
                                      WandItems wandItems) {
        return countConsumable(inventory.getStorageContents(), material, wandItems);
    }

    static int countAll(ItemStack[] storage, Material material, WandItems wandItems) {
        return matchingSlots(storage, material, wandItems).stream()
                .mapToInt(SupplySlot::amount)
                .reduce(0, Math::addExact);
    }

    static int countConsumable(ItemStack[] storage, Material material, WandItems wandItems) {
        return consumableCount(matchingSlots(storage, material, wandItems));
    }

    static int consumableCount(List<SupplySlot> matching) {
        int total = 0;
        for (SupplySlot supply : matching) {
            int reserved = supply.slot() < HOTBAR_SIZE ? 1 : 0;
            total = Math.addExact(total, Math.max(0, supply.amount() - reserved));
        }
        return total;
    }

    /** Main inventory first, then a hotbar stack with surplus above its protected sample. */
    static OptionalInt nextSpendSlot(List<SupplySlot> matching) {
        OptionalInt mainInventory = matching.stream()
                .filter(supply -> supply.slot() >= HOTBAR_SIZE)
                .mapToInt(SupplySlot::slot)
                .findFirst();
        if (mainInventory.isPresent()) {
            return mainInventory;
        }
        return matching.stream()
                .filter(supply -> supply.slot() < HOTBAR_SIZE && supply.amount() > 1)
                .mapToInt(SupplySlot::slot)
                .findFirst();
    }

    /**
     * Remove one ordinary feedstock item. Main-inventory stacks are preferred; hotbar stacks may
     * be reduced only to one. The receipt supports lossless rollback if world placement fails.
     */
    public static Optional<Receipt> spendOne(PlayerInventory inventory, Material material,
                                             WandItems wandItems) {
        ItemStack[] storage = inventory.getStorageContents();
        OptionalInt selected = nextSpendSlot(matchingSlots(storage, material, wandItems));
        if (selected.isEmpty()) {
            return Optional.empty();
        }
        int slot = selected.getAsInt();
        ItemStack stack = storage[slot];
        ItemStack removed = oneOf(stack);
        if (stack.getAmount() <= 1) {
            storage[slot] = null;
        } else {
            stack.setAmount(stack.getAmount() - 1);
        }
        inventory.setStorageContents(storage);
        return Optional.of(new Receipt(removed, slot));
    }

    /**
     * Restore an exact removal to its original slot when possible, then any storage slot.
     * Returns the still-unrestored item so the caller can drop it recoverably.
     */
    public static ItemStack restoreOne(PlayerInventory inventory, Receipt receipt) {
        ItemStack restored = receipt.item().clone();
        ItemStack[] storage = inventory.getStorageContents();
        if (receipt.storageSlot() >= 0 && receipt.storageSlot() < storage.length) {
            ItemStack merged = merge(storage[receipt.storageSlot()], restored);
            if (merged != null) {
                storage[receipt.storageSlot()] = merged;
                inventory.setStorageContents(storage);
                return null;
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
        return stack != null && isPlainFeedstock(stack.getType(), stack.hasItemMeta(), material,
                material == Material.STICK && wandItems.isWand(stack));
    }

    /** Pure eligibility rule shared with tests: customized items are never spent as blocks. */
    static boolean isPlainFeedstock(Material stackMaterial, boolean hasItemMeta,
                                    Material requestedMaterial, boolean buildersWand) {
        return !hasItemMeta && !buildersWand && stackMaterial == requestedMaterial;
    }

    private static List<SupplySlot> matchingSlots(ItemStack[] storage, Material material,
                                                   WandItems wandItems) {
        List<SupplySlot> matching = new ArrayList<>();
        for (int slot = 0; slot < storage.length; slot++) {
            ItemStack stack = storage[slot];
            if (matches(stack, material, wandItems)) {
                matching.add(new SupplySlot(slot, stack.getAmount()));
            }
        }
        return matching;
    }
}
