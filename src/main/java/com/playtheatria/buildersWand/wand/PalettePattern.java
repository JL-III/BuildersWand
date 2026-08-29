package com.playtheatria.buildersWand.wand;

import java.util.UUID;

/** Deterministic coordinate-to-palette mapping shared by previews and committed waves. */
public final class PalettePattern {

    private static final long X_SALT = 0x9e3779b97f4a7c15L;
    private static final long Y_SALT = 0xc2b2ae3d27d4eb4fL;
    private static final long Z_SALT = 0x165667b19e3779f9L;

    private PalettePattern() {
    }

    /** Select a weighted entry for one world coordinate. */
    public static int index(int entryCount, long paletteFingerprint, UUID worldId,
                            int x, int y, int z) {
        if (entryCount < 1 || entryCount > MaterialSelectionSnapshot.MAX_ENTRIES) {
            throw new IllegalArgumentException("Entry count must be between 1 and "
                    + MaterialSelectionSnapshot.MAX_ENTRIES + ".");
        }
        if (worldId == null) {
            throw new IllegalArgumentException("A stable world UUID is required.");
        }
        long hash = mix64(paletteFingerprint ^ worldId.getMostSignificantBits());
        hash = mix64(hash ^ worldId.getLeastSignificantBits());
        hash = mix64(hash ^ Integer.toUnsignedLong(x) * X_SALT);
        hash = mix64(hash ^ Integer.toUnsignedLong(y) * Y_SALT);
        hash = mix64(hash ^ Integer.toUnsignedLong(z) * Z_SALT);
        return (int) Math.floorMod(hash, entryCount);
    }

    /** SplitMix64 finalizer with fully specified, platform-independent arithmetic. */
    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
