package com.playtheatria.buildersWand.prefab;

import java.util.Objects;

/** A prefab cell expressed as an offset from its world anchor for one placement rotation. */
public record TransformedPrefabCell(
        PrefabPosition anchorOffset,
        PrefabCellKind kind,
        String blockState
) {
    public TransformedPrefabCell {
        Objects.requireNonNull(anchorOffset, "anchorOffset");
        Objects.requireNonNull(kind, "kind");
        if (kind == PrefabCellKind.BLOCK) {
            blockState = BlockStateString.parse(blockState).canonical();
        } else if (blockState != null) {
            throw new IllegalArgumentException("Only block cells may carry block state");
        }
    }
}
