package com.playtheatria.buildersWand.wand;

import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable hotbar-palette input captured before a plan is derived. Every occupied supported
 * hotbar slot is one equal-weight entry; duplicate materials therefore increase their weight.
 * Entries are canonicalized by material so moving a stack to another slot does not change the
 * texture. Selection is based on world coordinates rather than emission order, so kept cells
 * cannot re-phase a texture on a later print.
 */
public record MaterialSelectionSnapshot(List<PrintMaterial> entries, long fingerprint) {

    public static final int MAX_ENTRIES = 9;

    public MaterialSelectionSnapshot {
        Objects.requireNonNull(entries, "entries");
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("a material selection must contain at least one entry");
        }
        if (entries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("a material selection can contain at most "
                    + MAX_ENTRIES + " entries");
        }
        if (entries.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("material selection entries cannot contain null");
        }
        if (entries.size() > 1 && entries.stream().anyMatch(PrintMaterial::reusable)) {
            throw new IllegalArgumentException("reusable catalysts are supported only in single-material mode");
        }
        entries = List.copyOf(entries);
    }

    /** Single-material selection used by pure planning callers. */
    public static MaterialSelectionSnapshot single(PrintMaterial material) {
        return palette(List.of(Objects.requireNonNull(material, "material")));
    }

    /**
     * Canonical, duplicate-preserving hotbar palette. Slot order and empty gaps are deliberately
     * ignored; only each material's number of occupied slots contributes to the recipe.
     */
    public static MaterialSelectionSnapshot palette(List<PrintMaterial> samples) {
        Objects.requireNonNull(samples, "samples");
        if (samples.isEmpty()) {
            throw new IllegalArgumentException("a material selection must contain at least one entry");
        }
        if (samples.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("material selection entries cannot contain null");
        }
        List<PrintMaterial> canonical = new ArrayList<>(samples);
        canonical.sort(Comparator.comparing(entry -> entry.sourceItem().name()));
        if (canonical.stream().allMatch(PrintMaterial::isWater)) {
            canonical = new ArrayList<>(List.of(PrintMaterial.water()));
        }
        return new MaterialSelectionSnapshot(canonical, fingerprint(canonical));
    }

    public boolean singleMaterial() {
        return entries.stream().distinct().limit(2).count() == 1;
    }

    public boolean containsWater() {
        return entries.stream().anyMatch(PrintMaterial::isWater);
    }

    /** Number of occupied palette slots represented by each source material. */
    public Map<Material, Integer> weights() {
        Map<Material, Integer> weights = new LinkedHashMap<>();
        entries.forEach(entry -> weights.merge(entry.sourceItem(), 1, Integer::sum));
        return Collections.unmodifiableMap(weights);
    }

    /**
     * Select one entry with a stable 64-bit coordinate mix. Duplicate entries intentionally
     * occupy multiple indices and therefore act as weights.
     */
    public PrintMaterial select(World world, int x, int y, int z) {
        Objects.requireNonNull(world, "world");
        return select(world.getUID(), x, y, z);
    }

    /** Pure UUID overload used by unit tests and non-world planning callers. */
    public PrintMaterial select(UUID worldId, int x, int y, int z) {
        Objects.requireNonNull(worldId, "worldId");
        if (singleMaterial()) {
            return entries.getFirst();
        }
        int index = PalettePattern.index(entries.size(), fingerprint, worldId, x, y, z);
        return entries.get(index);
    }

    /** Stable FNV-1a fingerprint of the canonical weighted material recipe. */
    private static long fingerprint(List<PrintMaterial> canonical) {
        long hash = 0xcbf29ce484222325L;
        for (PrintMaterial entry : canonical) {
            String key = entry.sourceItem().name();
            for (int index = 0; index < key.length(); index++) {
                hash ^= key.charAt(index);
                hash *= 0x100000001b3L;
            }
            hash ^= 0xffL;
            hash *= 0x100000001b3L;
        }
        return hash;
    }
}
