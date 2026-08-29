package com.playtheatria.buildersWand.prefab;

/** Semantic meaning of a normalized schematic cell. */
public enum PrefabCellKind {
    /** A block that the player must supply and the placement path must match exactly. */
    BLOCK,
    /** Authored air that must remain clear but is never deleted. */
    CLEARANCE,
    /** A structure-void cell the prefab intentionally does not constrain. */
    IGNORED
}
