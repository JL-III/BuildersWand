package com.playtheatria.buildersWand.form;

import java.util.Locale;
import java.util.Optional;

/** Whether an enclosed form emits only its skin or its complete interior volume. */
public enum Density {
    SHELL,
    SOLID;

    public static final Density DEFAULT = SHELL;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String label() {
        String key = key();
        return Character.toUpperCase(key.charAt(0)) + key.substring(1);
    }

    public static Optional<Density> fromKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String normalized = key.strip().toLowerCase(Locale.ROOT);
        for (Density density : values()) {
            if (density.key().equals(normalized)) {
                return Optional.of(density);
            }
        }
        return Optional.empty();
    }
}
