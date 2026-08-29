package com.playtheatria.buildersWand.form;

import java.util.Locale;
import java.util.Optional;

/**
 * Player-facing build forms. Most forms have a deterministic parametric expansion; Extend
 * Surface is the exception because membership depends on a connected traversal of live world
 * block data.
 */
public enum Form {
    DIAGONAL,
    BOX,
    CYLINDER,
    SPHERE,
    WALL,
    LINE,
    FLOOR,
    EXTEND_SURFACE;

    /** The wand's default form (Single was removed; owner feedback 2026-08-24). */
    public static final Form DEFAULT = BOX;

    /** Number of intermediate lock clicks before the print click (design §7.1). */
    public int lockStages() {
        return switch (this) {
            case DIAGONAL, CYLINDER, SPHERE -> 1;
            case BOX -> 2;
            case WALL, LINE, FLOOR, EXTEND_SURFACE -> 0;
        };
    }

    /** Whether Shell/Solid changes this form's expansion. */
    public boolean supportsDensity() {
        return this == BOX || this == CYLINDER || this == SPHERE;
    }

    /** Whether {@link Expansion} can derive every cell from dimensions alone. */
    public boolean hasParametricExpansion() {
        return this != EXTEND_SURFACE;
    }

    /** Number of meaningful dimension values used in diagnostics. */
    int dimensionCount() {
        return switch (this) {
            case LINE -> 1;
            case DIAGONAL, CYLINDER, SPHERE, WALL, FLOOR -> 2;
            case BOX, EXTEND_SURFACE -> 3;
        };
    }

    /** Lower-case PDC / command key, e.g. {@code "box"}. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Title-cased display label, e.g. {@code "Box"} or {@code "Extend Surface"}. */
    public String label() {
        String[] words = key().split("_");
        StringBuilder label = new StringBuilder();
        for (String word : words) {
            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return label.toString();
    }

    public static Optional<Form> fromKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String normalized = key.strip().toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        for (Form form : values()) {
            if (form.key().equals(normalized)) {
                return Optional.of(form);
            }
        }
        return Optional.empty();
    }
}
