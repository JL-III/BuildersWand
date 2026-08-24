package com.playtheatria.buildersWand.wand;

import com.playtheatria.buildersWand.form.Form;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Wand item identity and state via {@link PersistentDataContainer} (design §4) — the lore and
 * display name are never parsed for behavior. Also the single place the print material is read
 * from the off hand (design §5.2), so the offhand-vs-wand distinction lives with {@link #isWand}.
 */
public final class WandItems {

    public static final String KEY_WAND = "wand";
    public static final String KEY_FORM = "form";

    private final NamespacedKey wandKey;
    private final NamespacedKey formKey;

    public WandItems(Plugin plugin) {
        this.wandKey = new NamespacedKey(plugin, KEY_WAND);
        this.formKey = new NamespacedKey(plugin, KEY_FORM);
    }

    public ItemStack createWand(Form form) {
        ItemStack item = new ItemStack(Material.STICK);
        item.editMeta(meta -> {
            meta.itemName(Component.text("Builders Wand").color(NamedTextColor.GOLD));
            meta.lore(List.of(Component.text("A mystical wand!")));
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(wandKey, PersistentDataType.BYTE, (byte) 1);
            pdc.set(formKey, PersistentDataType.STRING, form.key());
        });
        return item;
    }

    /** Null-safe; true iff the PDC carries the wand key (design §4 — the only wand test). */
    public boolean isWand(ItemStack item) {
        if (item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    /** PDC form, defaulting to {@link Form#DEFAULT} if absent or unknown. */
    public Form getForm(ItemStack item) {
        if (item == null) {
            return Form.DEFAULT;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return Form.DEFAULT;
        }
        String key = meta.getPersistentDataContainer().get(formKey, PersistentDataType.STRING);
        if (key == null) {
            return Form.DEFAULT;
        }
        return Form.fromKey(key).orElse(Form.DEFAULT);
    }

    public void setForm(ItemStack item, Form form) {
        item.editMeta(meta ->
                meta.getPersistentDataContainer().set(formKey, PersistentDataType.STRING, form.key()));
    }

    /**
     * The print material read from the off hand, if it is an allowed material (design §5.2):
     * empty when the offhand is empty, is this plugin's wand, or is not placeable.
     */
    public Optional<Material> selectedMaterial(Player player) {
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand.getType().isAir() || isWand(offhand)) {
            return Optional.empty();
        }
        Material type = offhand.getType();
        return isAllowedMaterial(type) ? Optional.of(type) : Optional.empty();
    }

    /**
     * Allowed print material: a solid placeable block that is not multi-block or
     * content-carrying (design §5.2).
     */
    public static boolean isAllowedMaterial(Material type) {
        return type.isBlock() && type.isItem() && type.isSolid() && !isDenylisted(type);
    }

    /** Multi-block or content-carrying materials excluded from printing (design §5.2). */
    public static boolean isDenylisted(Material type) {
        return Tag.DOORS.isTagged(type) || Tag.BEDS.isTagged(type) || Tag.SHULKER_BOXES.isTagged(type);
    }

    /** Player-facing material name (design §13): {@code SMOOTH_STONE} → {@code smooth stone}. */
    public static String materialDisplayName(Material material) {
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
