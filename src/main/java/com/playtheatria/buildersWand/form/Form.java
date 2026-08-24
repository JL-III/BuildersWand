package com.playtheatria.buildersWand.form;

import java.util.Locale;
import java.util.Optional;

/**
 * The five parametric forms of the catalog (design §6). Pure data — no Bukkit world types.
 */
public enum Form {
    SINGLE,
    DIAGONAL,
    BOX,
    CYLINDER,
    SPHERE;

    /** Number of intermediate lock clicks before the print click (design §7.1). */
    public int lockStages() {
        return switch (this) {
            case SINGLE -> 0;
            case DIAGONAL, CYLINDER, SPHERE -> 1;
            case BOX -> 2;
        };
    }

    /** Lower-case PDC / command key, e.g. {@code "box"}. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
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
