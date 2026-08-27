package com.playtheatria.buildersWand.wand;

import com.playtheatria.buildersWand.form.Form;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
import java.util.UUID;

/**
 * Wand item identity and state via {@link PersistentDataContainer} (design §4) — the lore and
 * display name are never parsed for behavior. Also the single place the print material is read
 * from the off hand (design §5.2), so the offhand-vs-wand distinction lives with {@link #isWand}.
 */
public final class WandItems {

    public static final String KEY_WAND = "wand";
    public static final String KEY_FORM = "form";
    public static final String KEY_ROTATION = "rotation";
    public static final String KEY_USES = "uses";
    public static final String KEY_MAX_USES = "max_uses";
    public static final String KEY_ACTIVE_TOKEN = "active_token";
    public static final String KEY_WAND_ID = "wand_id";
    public static final String KEY_FIRST_WIELDER_UUID = "first_wielder_uuid";
    public static final String KEY_FIRST_WIELDER_NAME = "first_wielder_name";
    public static final String KEY_FIRST_WIELDED_AT = "first_wielded_at";

    /** Shared player-facing hint for the two supported off-hand material kinds. */
    public static final String MATERIAL_HINT =
            "Hold a placeable block or water bucket in your off hand to choose the material.";

    /** Permission to use the wand (gesture + ghost) and {@code /wand form}. Default true. */
    public static final String PERMISSION_USE = "builderswand.use";
    /** Permission for {@code /wand give}. Default op. */
    public static final String PERMISSION_GIVE = "builderswand.give";
    /** Stable permission node for restoring Uses with Denarii. Default true. */
    public static final String PERMISSION_REFILL = "builderswand.refill";
    /** Explicit administrative bypass for use consumption. Default op. */
    public static final String PERMISSION_USES_BYPASS = "builderswand.uses.bypass";
    /** Administrative replacement of remaining Uses on the wand held by the command sender. */
    public static final String PERMISSION_ADMIN_SET_USES = "builderswand.admin.setuses";

    private final NamespacedKey wandKey;
    private final NamespacedKey formKey;
    private final NamespacedKey rotationKey;
    private final NamespacedKey usesKey;
    private final NamespacedKey maxUsesKey;
    private final NamespacedKey activeTokenKey;
    private final NamespacedKey wandIdKey;
    private final NamespacedKey firstWielderUuidKey;
    private final NamespacedKey firstWielderNameKey;
    private final NamespacedKey firstWieldedAtKey;
    private final int configuredMaxUses;

    /** Immutable item provenance; historical gameplay totals belong to the player, not the wand. */
    public record Identity(String wandId, String firstWielderUuid, String firstWielderName,
                           long firstWieldedAt) {

        public boolean claimed() {
            return wandId != null && firstWielderUuid != null;
        }
    }

    /** Exact mutation receipt, allowing a failed placement to restore the wand safely. */
    public record UseReceipt(int remainingBefore, int remainingAfter) {
    }

    /** Result embedded into a prepared item clone before a paid Use restoration is withdrawn. */
    public record RestoreReceipt(int remainingAfter) {
    }

    public WandItems(Plugin plugin, int configuredMaxUses) {
        this.wandKey = new NamespacedKey(plugin, KEY_WAND);
        this.formKey = new NamespacedKey(plugin, KEY_FORM);
        this.rotationKey = new NamespacedKey(plugin, KEY_ROTATION);
        this.usesKey = new NamespacedKey(plugin, KEY_USES);
        this.maxUsesKey = new NamespacedKey(plugin, KEY_MAX_USES);
        this.activeTokenKey = new NamespacedKey(plugin, KEY_ACTIVE_TOKEN);
        this.wandIdKey = new NamespacedKey(plugin, KEY_WAND_ID);
        this.firstWielderUuidKey = new NamespacedKey(plugin, KEY_FIRST_WIELDER_UUID);
        this.firstWielderNameKey = new NamespacedKey(plugin, KEY_FIRST_WIELDER_NAME);
        this.firstWieldedAtKey = new NamespacedKey(plugin, KEY_FIRST_WIELDED_AT);
        this.configuredMaxUses = configuredMaxUses;
    }

    public ItemStack createWand(Form form) {
        ItemStack item = new ItemStack(Material.STICK);
        item.editMeta(meta -> {
            applyName(meta, form);
            UseCounter.State uses = new UseCounter.State(configuredMaxUses, configuredMaxUses);
            meta.setMaxStackSize(1);
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(wandKey, PersistentDataType.BYTE, (byte) 1);
            pdc.set(formKey, PersistentDataType.STRING, form.key());
            pdc.set(usesKey, PersistentDataType.INTEGER, uses.remaining());
            pdc.set(maxUsesKey, PersistentDataType.INTEGER, uses.maximum());
            applyLore(meta, uses, readIdentity(pdc));
        });
        return item;
    }

    private static void applyLore(ItemMeta meta, UseCounter.State usesState, Identity identity) {
        Component flavor = Component.text("A mystical wand!", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false);
        NamedTextColor valueColor = usesState.depleted() ? NamedTextColor.RED : NamedTextColor.GOLD;
        Component uses = Component.text("Uses: ", NamedTextColor.DARK_GRAY)
                .append(Component.text(formatNumber(usesState.remaining()) + " / "
                        + formatNumber(usesState.maximum()), valueColor))
                .decoration(TextDecoration.ITALIC, false);
        Component firstWielded = Component.text("First wielded by: ", NamedTextColor.DARK_GRAY)
                .append(Component.text(identity.firstWielderName() == null
                        ? "Unwielded" : identity.firstWielderName(), NamedTextColor.GRAY))
                .decoration(TextDecoration.ITALIC, false);
        meta.lore(List.of(flavor, uses, firstWielded));
    }

    /**
     * Writes the current form into the item name for at-a-glance mode feedback (shown in the
     * hotbar name popup when the wand is selected). Display only — the PDC form remains the
     * single source of truth and is never derived from this name (design §4).
     */
    private static void applyName(ItemMeta meta, Form form) {
        meta.itemName(Component.text("Builders Wand", NamedTextColor.GOLD)
                .append(Component.text(" · " + form.label(), NamedTextColor.GRAY)));
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
        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(formKey, PersistentDataType.STRING, form.key());
            applyName(meta, form);
        });
    }

    /** Placement rotation step (design: oriented placement, owner feature). Defaults to 0. */
    public int getRotation(ItemStack item) {
        if (item == null) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0;
        }
        Integer rotation = meta.getPersistentDataContainer().get(rotationKey, PersistentDataType.INTEGER);
        return rotation == null ? 0 : rotation;
    }

    /** Advance the placement rotation by one step and return the persisted zero-based step. */
    public int cycleRotation(ItemStack item) {
        int next = (getRotation(item) + 1) % BlockOrientation.STATES;
        item.editMeta(meta -> meta.getPersistentDataContainer()
                .set(rotationKey, PersistentDataType.INTEGER, next));
        return next;
    }

    /** Authoritative uses, with missing legacy fields interpreted as a full configured wand. */
    public UseCounter.State uses(ItemStack item) {
        if (!isWand(item)) {
            return new UseCounter.State(0, configuredMaxUses);
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        Integer remaining = pdc.get(usesKey, PersistentDataType.INTEGER);
        Integer maximum = pdc.get(maxUsesKey, PersistentDataType.INTEGER);
        return UseCounter.normalize(remaining, maximum, configuredMaxUses);
    }

    /** Persist normalized legacy Uses/lore and make a single wand non-stackable. */
    public UseCounter.State initializeUses(ItemStack item) {
        UseCounter.State state = uses(item);
        if (!isWand(item) || item.getAmount() != 1) {
            return state;
        }
        item.editMeta(meta -> {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(usesKey, PersistentDataType.INTEGER, state.remaining());
            pdc.set(maxUsesKey, PersistentDataType.INTEGER, state.maximum());
            meta.setMaxStackSize(1);
            applyName(meta, getForm(item));
            applyLore(meta, state, readIdentity(pdc));
        });
        return state;
    }

    /**
     * Administratively replace the remaining Uses on one exact wand without changing its
     * maximum or any player-global history. Rotating the active token invalidates stale waves
     * and restoration quotes that might still refer to the item's prior state.
     */
    public Optional<UseCounter.State> setRemainingUses(ItemStack item, int remaining) {
        if (!isWand(item) || item.getAmount() != 1) {
            return Optional.empty();
        }
        UseCounter.State current = uses(item);
        Optional<UseCounter.State> replacement = UseCounter.setRemaining(current, remaining);
        if (replacement.isEmpty()) {
            return Optional.empty();
        }
        UseCounter.State updated = replacement.get();
        Form form = getForm(item);
        item.editMeta(meta -> {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(usesKey, PersistentDataType.INTEGER, updated.remaining());
            pdc.set(maxUsesKey, PersistentDataType.INTEGER, updated.maximum());
            pdc.set(activeTokenKey, PersistentDataType.STRING, UUID.randomUUID().toString());
            meta.setMaxStackSize(1);
            applyName(meta, form);
            applyLore(meta, updated, readIdentity(pdc));
        });
        return Optional.of(updated);
    }

    /**
     * Claims an unowned kit/template wand at its first real wield. Both the serial and first
     * wielder are minted lazily so Essentials kit signs cannot clone one permanent identity.
     */
    public Identity ensureFirstWielder(ItemStack item, Player player) {
        initializeUses(item);
        if (!isWand(item) || item.getAmount() != 1) {
            return identity(item);
        }
        long now = System.currentTimeMillis();
        item.editMeta(meta -> {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (pdc.get(wandIdKey, PersistentDataType.STRING) == null) {
                pdc.set(wandIdKey, PersistentDataType.STRING, UUID.randomUUID().toString());
            }
            if (pdc.get(firstWielderUuidKey, PersistentDataType.STRING) == null) {
                pdc.set(firstWielderUuidKey, PersistentDataType.STRING, player.getUniqueId().toString());
                pdc.set(firstWielderNameKey, PersistentDataType.STRING, player.getName());
                pdc.set(firstWieldedAtKey, PersistentDataType.LONG, now);
            }
            applyLore(meta, readUses(pdc), readIdentity(pdc));
        });
        return identity(item);
    }

    public Identity identity(ItemStack item) {
        if (!isWand(item)) {
            return new Identity(null, null, null, 0L);
        }
        return readIdentity(item.getItemMeta().getPersistentDataContainer());
    }

    /**
     * Rotate the short-lived identity bound to one wave or Use-restoration quote. This deliberately
     * happens lazily so an Essentials kit template cannot clone a permanent wand UUID.
     */
    public String rotateActiveToken(ItemStack item) {
        initializeUses(item);
        String token = UUID.randomUUID().toString();
        item.editMeta(meta -> meta.getPersistentDataContainer()
                .set(activeTokenKey, PersistentDataType.STRING, token));
        return token;
    }

    public boolean hasActiveToken(ItemStack item, String token) {
        if (!isWand(item) || item.getAmount() != 1 || token == null) {
            return false;
        }
        String held = item.getItemMeta().getPersistentDataContainer()
                .get(activeTokenKey, PersistentDataType.STRING);
        return token.equals(held);
    }

    /** Spend one cell-use from the exact token-bound wand. */
    public Optional<UseReceipt> spendUse(ItemStack item, String token, boolean consumeRemaining) {
        return spendUses(item, token, 1, consumeRemaining);
    }

    /** Atomically spend an exact placement cost. */
    public Optional<UseReceipt> spendUses(ItemStack item, String token, int amount,
                                          boolean consumeRemaining) {
        if (amount < 1) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (!hasActiveToken(item, token)) {
            return Optional.empty();
        }
        UseCounter.State before = uses(item);
        UseCounter.State after = before;
        if (consumeRemaining) {
            Optional<UseCounter.State> spent = UseCounter.spend(before, amount);
            if (spent.isEmpty()) {
                return Optional.empty();
            }
            after = spent.get();
        }
        UseReceipt receipt = new UseReceipt(before.remaining(), after.remaining());
        writeUses(item, after);
        return Optional.of(receipt);
    }

    /** Restore just-spent Uses after a later mutation fails. */
    public boolean restoreUse(ItemStack item, String token, UseReceipt receipt) {
        if (!hasActiveToken(item, token)) {
            return false;
        }
        UseCounter.State current = uses(item);
        if (current.remaining() != receipt.remainingAfter()) {
            return false;
        }
        writeUses(item, new UseCounter.State(receipt.remainingBefore(), current.maximum()));
        return true;
    }

    /** Prepare a paid Use restoration on an exact token-bound clone. */
    public Optional<RestoreReceipt> applyPaidRestore(ItemStack item, String token, int expectedRemaining,
                                                     int newRemaining) {
        if (!hasActiveToken(item, token)) {
            return Optional.empty();
        }
        UseCounter.State before = uses(item);
        if (before.remaining() != expectedRemaining || newRemaining < before.remaining()
                || newRemaining > before.maximum()) {
            return Optional.empty();
        }
        writeUses(item, new UseCounter.State(newRemaining, before.maximum()));
        return Optional.of(new RestoreReceipt(newRemaining));
    }

    private void writeUses(ItemStack item, UseCounter.State state) {
        item.editMeta(meta -> {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(usesKey, PersistentDataType.INTEGER, state.remaining());
            pdc.set(maxUsesKey, PersistentDataType.INTEGER, state.maximum());
            meta.setMaxStackSize(1);
            applyLore(meta, state, readIdentity(pdc));
        });
    }

    private UseCounter.State readUses(PersistentDataContainer pdc) {
        return UseCounter.normalize(pdc.get(usesKey, PersistentDataType.INTEGER),
                pdc.get(maxUsesKey, PersistentDataType.INTEGER), configuredMaxUses);
    }

    private Identity readIdentity(PersistentDataContainer pdc) {
        return new Identity(
                pdc.get(wandIdKey, PersistentDataType.STRING),
                pdc.get(firstWielderUuidKey, PersistentDataType.STRING),
                pdc.get(firstWielderNameKey, PersistentDataType.STRING),
                valueOrZero(pdc.get(firstWieldedAtKey, PersistentDataType.LONG)));
    }

    private static long valueOrZero(Long value) {
        return value == null || value < 0L ? 0L : value;
    }

    private static String formatNumber(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    /**
     * The print material read from the off hand: a supported solid block or water bucket.
     * Empty when the offhand is empty, is this plugin's wand, or is not a supported selector.
     */
    public Optional<PrintMaterial> selectedMaterial(Player player) {
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand.getType().isAir() || isWand(offhand)) {
            return Optional.empty();
        }
        return printMaterialFor(offhand.getType());
    }

    /**
     * Maps the off-hand item to its print policy. A water bucket is retained and maps to a
     * level-0 water block; ordinary allowed blocks remain one-item-per-cell feedstock.
     */
    public static Optional<PrintMaterial> printMaterialFor(Material type) {
        if (type == Material.WATER_BUCKET) {
            return Optional.of(PrintMaterial.water());
        }
        // Water is the only bucket policy. Reject every other bucket before asking Paper's
        // block registry whether the item is placeable.
        if (type.name().endsWith("BUCKET")) {
            return Optional.empty();
        }
        return isAllowedMaterial(type) ? Optional.of(PrintMaterial.block(type)) : Optional.empty();
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
