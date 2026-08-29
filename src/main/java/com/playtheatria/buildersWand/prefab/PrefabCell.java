package com.playtheatria.buildersWand.prefab;

import java.util.Objects;

/** One cell in an immutable normalized prefab definition. */
public record PrefabCell(PrefabPosition position, PrefabCellKind kind, String blockState) {

    public PrefabCell {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(kind, "kind");
        if (kind == PrefabCellKind.BLOCK) {
            blockState = BlockStateString.parse(blockState).canonical();
        } else if (blockState != null) {
            throw new IllegalArgumentException("Only block cells may carry block state");
        }
    }

    public static PrefabCell block(PrefabPosition position, String blockState) {
        return new PrefabCell(position, PrefabCellKind.BLOCK, blockState);
    }

    public static PrefabCell clearance(PrefabPosition position) {
        return new PrefabCell(position, PrefabCellKind.CLEARANCE, null);
    }

    public static PrefabCell ignored(PrefabPosition position) {
        return new PrefabCell(position, PrefabCellKind.IGNORED, null);
    }
}
