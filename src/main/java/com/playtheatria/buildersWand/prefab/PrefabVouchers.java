package com.playtheatria.buildersWand.prefab;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Creates and identifies one-use PDC-backed Blueprint Vouchers. */
public final class PrefabVouchers {

    public static final String KEY_VOUCHER = "prefab_voucher";
    public static final String KEY_PREFAB_ID = "prefab_id";

    private final NamespacedKey voucherKey;
    private final NamespacedKey prefabIdKey;

    public PrefabVouchers(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        this.voucherKey = new NamespacedKey(plugin, KEY_VOUCHER);
        this.prefabIdKey = new NamespacedKey(plugin, KEY_PREFAB_ID);
    }

    public ItemStack create(PrefabDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        ItemStack voucher = new ItemStack(Material.PAPER);
        voucher.editMeta(meta -> {
            meta.itemName(Component.text("Blueprint Voucher", NamedTextColor.AQUA));
            meta.lore(List.of(
                    plainLore("Unlocks: ", NamedTextColor.DARK_GRAY)
                            .append(Component.text(definition.metadata().name(), NamedTextColor.WHITE)
                                    .decoration(TextDecoration.ITALIC, false)),
                    plainLore("Redeem with /wand prefab redeem", NamedTextColor.GRAY)
            ));
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(voucherKey, PersistentDataType.BYTE, (byte) 1);
            pdc.set(prefabIdKey, PersistentDataType.STRING, definition.id());
        });
        return voucher;
    }

    /** Returns the stable prefab ID only when both voucher PDC fields are valid. */
    public Optional<String> prefabId(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return Optional.empty();
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(voucherKey, PersistentDataType.BYTE)) {
            return Optional.empty();
        }
        String id = pdc.get(prefabIdKey, PersistentDataType.STRING);
        try {
            return Optional.of(PrefabId.requireValid(id));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return Optional.empty();
        }
    }

    public boolean isVoucher(ItemStack item) {
        return prefabId(item).isPresent();
    }

    /**
     * Consumes exactly one matching main-hand voucher after its durable entitlement was written.
     * A changed hand is left untouched so a command cannot consume an unrelated item.
     */
    public boolean consumeOneFromMainHand(Player player, String expectedPrefabId) {
        Objects.requireNonNull(player, "player");
        String validId = PrefabId.requireValid(expectedPrefabId);
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!prefabId(held).filter(validId::equals).isPresent()) {
            return false;
        }
        if (held.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            held.setAmount(held.getAmount() - 1);
            player.getInventory().setItemInMainHand(held);
        }
        return true;
    }

    private static Component plainLore(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
