package com.playtheatria.buildersWand.wand;

import com.playtheatria.buildersWand.form.Density;
import com.playtheatria.buildersWand.form.Form;
import com.playtheatria.buildersWand.form.SurfaceRestriction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Wand item identity and state via {@link PersistentDataContainer} (design §4) — the lore and
 * display name are never parsed for behavior. It also captures the player's live hotbar palette.
 */
public final class WandItems {

    private static final java.util.Map<Material, Boolean> BLOCK_ENTITY_MATERIALS =
            new ConcurrentHashMap<>();

    public static final String KEY_WAND = "wand";
    public static final String KEY_FORM = "form";
    public static final String KEY_DENSITY = "density";
    public static final String KEY_SURFACE_RESTRICTION = "surface_restriction";
    public static final String KEY_ROTATION = "rotation";
    public static final String KEY_USES = "uses";
    public static final String KEY_MAX_USES = "max_uses";
    public static final String KEY_ACTIVE_TOKEN = "active_token";
    public static final String KEY_WAND_ID = "wand_id";
    public static final String KEY_FIRST_WIELDER_UUID = "first_wielder_uuid";
    public static final String KEY_FIRST_WIELDER_NAME = "first_wielder_name";
    public static final String KEY_FIRST_WIELDED_AT = "first_wielded_at";

    /** Short action-bar hint when the hotbar has no printable palette material. */
    public static final String MATERIAL_HINT = "Add supported building materials to your hotbar.";

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
    private final NamespacedKey densityKey;
    private final NamespacedKey surfaceRestrictionKey;
    private final NamespacedKey rotationKey;
    private final NamespacedKey usesKey;
    private final NamespacedKey maxUsesKey;
    private final NamespacedKey activeTokenKey;
    private final NamespacedKey wandIdKey;
    private final NamespacedKey firstWielderUuidKey;
    private final NamespacedKey firstWielderNameKey;
    private final NamespacedKey firstWieldedAtKey;
    private final int configuredMaxUses;

    /** A live hotbar capture, including a useful refusal when no palette can be formed. */
    public record MaterialSelection(Optional<MaterialSelectionSnapshot> snapshot, String problem) {
        public MaterialSelection {
            snapshot = snapshot == null ? Optional.empty() : snapshot;
            problem = problem == null ? "" : problem;
            if (snapshot.isPresent() == !problem.isEmpty()) {
                throw new IllegalArgumentException("a material selection has either a snapshot or a problem");
            }
        }

        public static MaterialSelection selected(MaterialSelectionSnapshot snapshot) {
            return new MaterialSelection(Optional.of(snapshot), "");
        }

        public static MaterialSelection refused(String problem) {
            return new MaterialSelection(Optional.empty(), problem);
        }
    }

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
        this.densityKey = new NamespacedKey(plugin, KEY_DENSITY);
        this.surfaceRestrictionKey = new NamespacedKey(plugin, KEY_SURFACE_RESTRICTION);
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

    /** Server-wide ceiling used to reject prefab activation prices no wand can pay. */
    public int configuredMaximumUses() {
        return configuredMaxUses;
    }

    public ItemStack createWand(Form form) {
        ItemStack item = new ItemStack(Material.STICK);
        item.editMeta(meta -> {
            applyName(meta, form, Density.DEFAULT, SurfaceRestriction.DEFAULT);
            UseCounter.State uses = new UseCounter.State(configuredMaxUses, configuredMaxUses);
            meta.setMaxStackSize(1);
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(wandKey, PersistentDataType.BYTE, (byte) 1);
            pdc.set(formKey, PersistentDataType.STRING, form.key());
            pdc.set(densityKey, PersistentDataType.STRING, Density.DEFAULT.key());
            pdc.set(surfaceRestrictionKey, PersistentDataType.STRING,
                    SurfaceRestriction.DEFAULT.key());
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
    private static void applyName(ItemMeta meta, Form form, Density density,
                                  SurfaceRestriction restriction) {
        String densitySuffix = form.supportsDensity() ? " · " + density.label() : "";
        String surfaceSuffix = form == Form.EXTEND_SURFACE
                ? " · " + restriction.label() : "";
        meta.itemName(Component.text("Builders Wand", NamedTextColor.GOLD)
                .append(Component.text(" · " + form.label() + densitySuffix + surfaceSuffix,
                        NamedTextColor.GRAY)));
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
        Density density = getDensity(item);
        SurfaceRestriction restriction = getSurfaceRestriction(item);
        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(formKey, PersistentDataType.STRING, form.key());
            applyName(meta, form, density, restriction);
        });
    }

    /** PDC density preference, defaulting to Shell for legacy wands. */
    public Density getDensity(ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return Density.DEFAULT;
        }
        String key = item.getItemMeta().getPersistentDataContainer()
                .get(densityKey, PersistentDataType.STRING);
        return Density.fromKey(key).orElse(Density.DEFAULT);
    }

    public void setDensity(ItemStack item, Density density) {
        Form form = getForm(item);
        SurfaceRestriction restriction = getSurfaceRestriction(item);
        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(densityKey, PersistentDataType.STRING, density.key());
            applyName(meta, form, density, restriction);
        });
    }

    /** Toggle Shell/Solid and return the persisted preference. */
    public Density cycleDensity(ItemStack item) {
        Density next = getDensity(item) == Density.SHELL ? Density.SOLID : Density.SHELL;
        setDensity(item, next);
        return next;
    }

    /** Connected-face restriction, defaulting to Free for legacy wands. */
    public SurfaceRestriction getSurfaceRestriction(ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return SurfaceRestriction.DEFAULT;
        }
        String key = item.getItemMeta().getPersistentDataContainer()
                .get(surfaceRestrictionKey, PersistentDataType.STRING);
        return SurfaceRestriction.fromKey(key).orElse(SurfaceRestriction.DEFAULT);
    }

    public void setSurfaceRestriction(ItemStack item, SurfaceRestriction restriction) {
        Form form = getForm(item);
        Density density = getDensity(item);
        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(
                    surfaceRestrictionKey, PersistentDataType.STRING, restriction.key());
            applyName(meta, form, density, restriction);
        });
    }

    public SurfaceRestriction cycleSurfaceRestriction(ItemStack item) {
        SurfaceRestriction[] values = SurfaceRestriction.values();
        SurfaceRestriction current = getSurfaceRestriction(item);
        SurfaceRestriction next = values[(current.ordinal() + 1) % values.length];
        setSurfaceRestriction(item, next);
        return next;
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
        Form form = getForm(item);
        Density density = getDensity(item);
        SurfaceRestriction restriction = getSurfaceRestriction(item);
        item.editMeta(meta -> {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(usesKey, PersistentDataType.INTEGER, state.remaining());
            pdc.set(maxUsesKey, PersistentDataType.INTEGER, state.maximum());
            meta.setMaxStackSize(1);
            applyName(meta, form, density, restriction);
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
        Density density = getDensity(item);
        SurfaceRestriction restriction = getSurfaceRestriction(item);
        item.editMeta(meta -> {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(usesKey, PersistentDataType.INTEGER, updated.remaining());
            pdc.set(maxUsesKey, PersistentDataType.INTEGER, updated.maximum());
            pdc.set(activeTokenKey, PersistentDataType.STRING, UUID.randomUUID().toString());
            meta.setMaxStackSize(1);
            applyName(meta, form, density, restriction);
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

    /** Immutable input for one preview/plan, captured directly from all nine hotbar slots. */
    public Optional<MaterialSelectionSnapshot> selectedMaterialSnapshot(Player player) {
        return materialSelection(player).snapshot();
    }

    /**
     * Capture a live weighted palette. Supported solid blocks participate; tools, food, and other
     * ordinary non-block items are ignored. Water buckets form a water-only palette and may not be
     * mixed with solid blocks. Moving samples between slots does not change the resulting recipe.
     */
    public MaterialSelection materialSelection(Player player) {
        java.util.Objects.requireNonNull(player, "player");
        List<Material> hotbar = new ArrayList<>(MaterialSelectionSnapshot.MAX_ENTRIES);
        for (int slot = 0; slot < MaterialSelectionSnapshot.MAX_ENTRIES; slot++) {
            ItemStack sample = player.getInventory().getItem(slot);
            hotbar.add(paletteMaterial(sample == null ? Material.AIR : sample.getType(),
                    sample != null && sample.hasItemMeta()));
        }
        return materialSelection(hotbar, WandItems::printMaterialFor, Material::isBlock);
    }

    /** Customized plugin or player items are utilities, never palette samples. */
    static Material paletteMaterial(Material material, boolean hasItemMeta) {
        return hasItemMeta ? Material.AIR : material;
    }

    /** Pure palette interpreter shared by production hotbar capture and unit tests. */
    static MaterialSelection materialSelection(
            List<Material> hotbar, Function<Material, Optional<PrintMaterial>> printPolicy,
            Predicate<Material> blockPolicy) {
        java.util.Objects.requireNonNull(hotbar, "hotbar");
        java.util.Objects.requireNonNull(printPolicy, "printPolicy");
        java.util.Objects.requireNonNull(blockPolicy, "blockPolicy");
        if (hotbar.size() > MaterialSelectionSnapshot.MAX_ENTRIES) {
            throw new IllegalArgumentException("a hotbar palette can contain at most nine slots");
        }
        List<PrintMaterial> solids = new ArrayList<>(MaterialSelectionSnapshot.MAX_ENTRIES);
        boolean water = false;
        for (int slot = 0; slot < hotbar.size(); slot++) {
            Material material = hotbar.get(slot);
            if (material == null || material == Material.AIR
                    || material == Material.CAVE_AIR || material == Material.VOID_AIR) {
                continue;
            }
            if (material == Material.WATER_BUCKET) {
                water = true;
                continue;
            }
            if (material.name().endsWith("BUCKET")) {
                return MaterialSelection.refused("Hotbar slot " + (slot + 1) + " contains "
                        + materialDisplayName(material) + "; only water buckets are supported.");
            }
            if (!blockPolicy.test(material)) {
                continue;
            }
            Optional<PrintMaterial> printable = printPolicy.apply(material);
            if (printable.isEmpty()) {
                return MaterialSelection.refused("Hotbar slot " + (slot + 1) + " contains "
                        + materialDisplayName(material) + ", which the Builders Wand cannot place.");
            }
            solids.add(printable.get());
        }
        if (water && !solids.isEmpty()) {
            return MaterialSelection.refused(
                    "Water buckets cannot be mixed with solid blocks. Use a water-only hotbar palette.");
        }
        if (water) {
            return MaterialSelection.selected(
                    MaterialSelectionSnapshot.palette(List.of(PrintMaterial.water())));
        }
        if (solids.isEmpty()) {
            return MaterialSelection.refused(
                    "Add supported building blocks to your hotbar. Water buckets work as a water-only palette.");
        }
        return MaterialSelection.selected(MaterialSelectionSnapshot.palette(solids));
    }

    /**
     * Maps a captured palette entry to its print policy. A water bucket is retained and maps to a
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
        String name = type.name();
        if (name.endsWith("_DOOR") || name.endsWith("_BED")
                || name.equals("SHULKER_BOX") || name.endsWith("_SHULKER_BOX")
                || name.equals("CHEST") || name.endsWith("_CHEST")) {
            return true;
        }
        if (!type.isBlock()) {
            return false;
        }
        return BLOCK_ENTITY_MATERIALS.computeIfAbsent(type, WandItems::hasBlockEntity);
    }

    private static boolean hasBlockEntity(Material type) {
        try {
            return type.createBlockData().createBlockState() instanceof TileState;
        } catch (RuntimeException unsafeBlockState) {
            // Material selection is fail-closed: a block that cannot prove it has ordinary,
            // stateless placement semantics must not enter a palette.
            return true;
        }
    }

    /** Player-facing material name (design §13): {@code SMOOTH_STONE} → {@code smooth stone}. */
    public static String materialDisplayName(Material material) {
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
