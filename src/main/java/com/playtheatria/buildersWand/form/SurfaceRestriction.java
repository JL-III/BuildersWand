package com.playtheatria.buildersWand.form;

import java.util.Locale;
import java.util.Optional;

/** Connected-face restriction used by Extend Surface. */
public enum SurfaceRestriction {
    FREE,
    ROW,
    COLUMN;

    public static final SurfaceRestriction DEFAULT = FREE;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String label() {
        String key = key();
        return Character.toUpperCase(key.charAt(0)) + key.substring(1);
    }

    public static Optional<SurfaceRestriction> fromKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String normalized = key.strip().toLowerCase(Locale.ROOT);
        for (SurfaceRestriction restriction : values()) {
            if (restriction.key().equals(normalized)) {
                return Optional.of(restriction);
            }
        }
        return Optional.empty();
    }
}
