package com.playtheatria.buildersWand.prefab;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Slab;

import java.util.Objects;
import java.util.Optional;

/**
 * Runtime policy that verifies an authored state resolves to an ordinary item-backed Bukkit block.
 *
 * <p>The dependency-free conservative policy rejects known content-bearing, fluid, administrative,
 * and multi-cell blocks. This final Bukkit-aware layer also rejects unknown registry entries and
 * blocks for which a player cannot supply a matching inventory item.</p>
 */
public final class BukkitPrefabBlockPolicy implements PrefabBlockPolicy {

    private final PrefabBlockPolicy baseline;

    public BukkitPrefabBlockPolicy(PrefabBlockPolicy baseline) {
        this.baseline = Objects.requireNonNull(baseline, "baseline");
    }

    public static BukkitPrefabBlockPolicy conservativeDefaults() {
        return new BukkitPrefabBlockPolicy(PrefabBlockPolicy.conservativeDefaults());
    }

    @Override
    public Optional<String> rejectionReason(String canonicalBlockState) {
        Optional<String> baselineRejection = baseline.rejectionReason(canonicalBlockState);
        if (baselineRejection.isPresent()) {
            return baselineRejection;
        }

        final BlockData blockData;
        try {
            blockData = Bukkit.createBlockData(canonicalBlockState);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return Optional.of("block state is not recognized by this server version");
        }

        Material material = blockData.getMaterial();
        if (!material.isBlock()) {
            return Optional.of(material.getKey() + " is not a placeable block");
        }
        if (!material.isItem() || material.getMaxStackSize() <= 0) {
            return Optional.of(material.getKey() + " has no player-supplied inventory item");
        }
        if (material.isAir()) {
            return Optional.of(material.getKey() + " cannot be a prefab placement cell");
        }
        if (blockData instanceof Slab slab && slab.getType() == Slab.Type.DOUBLE) {
            return Optional.of(material.getKey() + " would require two inventory items in one cell");
        }
        BlockStateString parsed = BlockStateString.parse(canonicalBlockState);
        for (String countProperty : new String[]{
                "candles", "pickles", "eggs", "layers", "flower_amount"
        }) {
            String rawCount = parsed.properties().get(countProperty);
            if (rawCount != null && parsePositive(rawCount) > 1) {
                return Optional.of(material.getKey()
                        + " would require multiple inventory items in one cell");
            }
        }
        String age = parsed.properties().get("age");
        if (age != null && parsePositive(age) > 0) {
            return Optional.of(material.getKey()
                    + " cannot import a pre-grown state from one inventory item");
        }
        if ("true".equals(parsed.properties().get("extended"))) {
            return Optional.of(material.getKey() + " cannot import an extended piston state safely");
        }
        return Optional.empty();
    }

    private static int parsePositive(String value) {
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
