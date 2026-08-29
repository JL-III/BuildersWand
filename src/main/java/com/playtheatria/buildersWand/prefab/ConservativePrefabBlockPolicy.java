package com.playtheatria.buildersWand.prefab;

import java.util.Optional;
import java.util.Set;

/**
 * Dependency-free baseline policy for the v1 safe subset.
 *
 * <p>Runtime integration should compose this with a Bukkit-aware item-backed-block check. The
 * baseline deliberately rejects fluids, content-bearing blocks, administrative blocks, and
 * common multi-block placements before they can enter the catalog.</p>
 */
final class ConservativePrefabBlockPolicy implements PrefabBlockPolicy {

    private static final Set<String> EXACT_REJECTIONS = Set.of(
            "minecraft:barrier",
            "minecraft:barrel",
            "minecraft:bee_nest",
            "minecraft:beehive",
            "minecraft:bedrock",
            "minecraft:brewing_stand",
            "minecraft:bubble_column",
            "minecraft:chest",
            "minecraft:command_block",
            "minecraft:chain_command_block",
            "minecraft:chiseled_bookshelf",
            "minecraft:composter",
            "minecraft:crafter",
            "minecraft:decorated_pot",
            "minecraft:dispenser",
            "minecraft:dropper",
            "minecraft:end_gateway",
            "minecraft:end_portal",
            "minecraft:end_portal_frame",
            "minecraft:fire",
            "minecraft:glow_lichen",
            "minecraft:hopper",
            "minecraft:jigsaw",
            "minecraft:jukebox",
            "minecraft:large_fern",
            "minecraft:lava",
            "minecraft:light",
            "minecraft:lilac",
            "minecraft:lectern",
            "minecraft:moving_piston",
            "minecraft:nether_portal",
            "minecraft:piston_head",
            "minecraft:pitcher_plant",
            "minecraft:peony",
            "minecraft:reinforced_deepslate",
            "minecraft:respawn_anchor",
            "minecraft:repeating_command_block",
            "minecraft:rose_bush",
            "minecraft:soul_fire",
            "minecraft:sculk_shrieker",
            "minecraft:sculk_vein",
            "minecraft:smoker",
            "minecraft:spawner",
            "minecraft:small_dripleaf",
            "minecraft:structure_block",
            "minecraft:sunflower",
            "minecraft:tall_grass",
            "minecraft:trial_spawner",
            "minecraft:trapped_chest",
            "minecraft:vine",
            "minecraft:vault",
            "minecraft:water"
    );

    private static final Set<String> REJECTED_SUFFIXES = Set.of(
            "_bed",
            "_chest",
            "_door",
            "_furnace",
            "_hanging_sign",
            "_head",
            "_shulker_box",
            "_sign",
            "_skull"
    );

    @Override
    public Optional<String> rejectionReason(String canonicalBlockState) {
        BlockStateString state;
        try {
            state = BlockStateString.parse(canonicalBlockState);
        } catch (IllegalArgumentException exception) {
            return Optional.of(exception.getMessage());
        }
        String id = state.id();
        if (EXACT_REJECTIONS.contains(id)) {
            return Optional.of(id + " is outside the safe prefab subset");
        }
        for (String suffix : REJECTED_SUFFIXES) {
            if (id.endsWith(suffix)) {
                return Optional.of(id + " has unsupported content or multi-block semantics");
            }
        }
        if ("true".equals(state.properties().get("waterlogged"))) {
            return Optional.of(id + " contains water and fluids are unsupported in prefab v1");
        }
        return Optional.empty();
    }
}
