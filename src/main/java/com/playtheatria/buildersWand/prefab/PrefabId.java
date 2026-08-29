package com.playtheatria.buildersWand.prefab;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Validation shared by catalog metadata and the durable entitlement key. */
public final class PrefabId {

    public static final int MAX_LENGTH = 64;
    private static final Pattern VALID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
    private static final Set<String> COMMAND_WORDS = Set.of(
            "redeem", "confirm", "cancel", "rotate",
            "grant", "revoke", "voucher", "validate", "reload");

    private PrefabId() {
    }

    public static String requireValid(String id) {
        if (id == null) {
            throw new IllegalArgumentException("Prefab ID is required");
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals(id.trim()) || !VALID.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Prefab ID must use 1-64 lowercase letters, digits, underscores, or hyphens"
            );
        }
        return normalized;
    }

    /** Catalog IDs share the first argument with these commands, so they cannot shadow them. */
    public static String requireCatalogId(String id) {
        String valid = requireValid(id);
        if (COMMAND_WORDS.contains(valid)) {
            throw new IllegalArgumentException(
                    "Prefab ID '" + valid + "' is reserved for a /wand prefab command");
        }
        return valid;
    }
}
