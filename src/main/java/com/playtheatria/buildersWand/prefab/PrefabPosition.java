package com.playtheatria.buildersWand.prefab;

/** A local integer coordinate inside a normalized prefab. */
public record PrefabPosition(int x, int y, int z) implements Comparable<PrefabPosition> {

    @Override
    public int compareTo(PrefabPosition other) {
        int byY = Integer.compare(y, other.y);
        if (byY != 0) {
            return byY;
        }
        int byZ = Integer.compare(z, other.z);
        if (byZ != 0) {
            return byZ;
        }
        return Integer.compare(x, other.x);
    }
}
