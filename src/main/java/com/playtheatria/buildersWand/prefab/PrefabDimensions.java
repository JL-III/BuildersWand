package com.playtheatria.buildersWand.prefab;

/** Width (right), height (up), and depth (inward) of a normalized prefab. */
public record PrefabDimensions(int width, int height, int depth) {

    public PrefabDimensions {
        if (width <= 0 || height <= 0 || depth <= 0) {
            throw new IllegalArgumentException("Prefab dimensions must all be positive");
        }
        Math.multiplyExact(Math.multiplyExact((long) width, height), depth);
    }

    public long volume() {
        return (long) width * height * depth;
    }

    public boolean contains(PrefabPosition position) {
        return position != null
                && position.x() >= 0 && position.x() < width
                && position.y() >= 0 && position.y() < height
                && position.z() >= 0 && position.z() < depth;
    }
}
