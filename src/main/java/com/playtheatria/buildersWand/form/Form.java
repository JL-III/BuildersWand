package com.playtheatria.buildersWand.form;

import java.util.Locale;
import java.util.Optional;

/**
 * The five parametric forms of the catalog (design §6). Pure data — no Bukkit world types.
 */
public enum Form {
    DIAGONAL,
    BOX,
    CYLINDER,
    SPHERE;

    /** The wand's default form (Single was removed; owner feedback 2026-08-24). */
    public static final Form DEFAULT = BOX;

    /** Number of intermediate lock clicks before the print click (design §7.1). */
    public int lockStages() {
        return switch (this) {
            case DIAGONAL, CYLINDER, SPHERE -> 1;
            case BOX -> 2;
        };
    }

    /** Lower-case PDC / command key, e.g. {@code "box"}. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Capitalised display label, e.g. {@code "Box"}. */
    public String label() {
        String key = key();
        return Character.toUpperCase(key.charAt(0)) + key.substring(1);
    }

    public static Optional<Form> fromKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        for (Form form : values()) {
            if (form.key().equals(normalized)) {
                return Optional.of(form);
            }
        }
        return Optional.empty();
    }
}
