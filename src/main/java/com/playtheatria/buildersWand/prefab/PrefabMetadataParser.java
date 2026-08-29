package com.playtheatria.buildersWand.prefab;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parser for the intentionally flat prefab sidecar format.
 *
 * <p>This is not a general YAML implementation. Rejecting nesting, aliases, tags, duplicate keys,
 * and unknown fields keeps administrator-authored catalog input deterministic and auditable.</p>
 */
public final class PrefabMetadataParser {

    private static final Set<String> ALLOWED_KEYS = Set.of(
            "id",
            "name",
            "version",
            "schematic",
            "source-rotation",
            "activation-uses",
            "allow-rotation",
            "clearance"
    );

    public PrefabMetadata parse(Path path, long defaultActivationUses)
            throws IOException, PrefabValidationException {
        if (defaultActivationUses < 0) {
            throw new IllegalArgumentException("Default activation Uses cannot be negative");
        }
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        Map<String, String> fields = new LinkedHashMap<>();
        for (int index = 0; index < lines.size(); index++) {
            String raw = lines.get(index);
            String line = stripComment(raw).trim();
            if (line.isEmpty() || line.equals("---") || line.equals("...")) {
                continue;
            }
            if (Character.isWhitespace(raw.charAt(0)) || raw.indexOf('\t') >= 0) {
                throw invalid(path, index, "nested or tab-indented YAML is not supported");
            }
            int separator = line.indexOf(':');
            if (separator <= 0) {
                throw invalid(path, index, "expected key: value");
            }
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (!ALLOWED_KEYS.contains(key)) {
                throw invalid(path, index, "unknown field '" + key + "'");
            }
            if (fields.putIfAbsent(key, unquote(value, path, index)) != null) {
                throw invalid(path, index, "duplicate field '" + key + "'");
            }
        }

        try {
            return new PrefabMetadata(
                    required(fields, "id"),
                    required(fields, "name"),
                    positiveInt(required(fields, "version"), "version"),
                    required(fields, "schematic"),
                    rotation(fields.getOrDefault("source-rotation", "0")),
                    nonNegativeLong(fields.getOrDefault(
                            "activation-uses",
                            Long.toString(defaultActivationUses)
                    ), "activation-uses"),
                    strictBoolean(fields.getOrDefault("allow-rotation", "true"), "allow-rotation"),
                    PrefabClearance.parse(fields.getOrDefault("clearance", "schematic-air"))
            );
        } catch (IllegalArgumentException exception) {
            throw new PrefabValidationException(path.getFileName() + ": " + exception.getMessage());
        }
    }

    private static String stripComment(String input) throws PrefabValidationException {
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean escaped = false;
        for (int index = 0; index < input.length(); index++) {
            char character = input.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (character == '\\' && doubleQuoted) {
                escaped = true;
            } else if (character == '\'' && !doubleQuoted) {
                singleQuoted = !singleQuoted;
            } else if (character == '"' && !singleQuoted) {
                doubleQuoted = !doubleQuoted;
            } else if (character == '#' && !singleQuoted && !doubleQuoted) {
                return input.substring(0, index);
            }
        }
        if (singleQuoted || doubleQuoted || escaped) {
            throw new PrefabValidationException("Unterminated quoted YAML value");
        }
        return input;
    }

    private static String unquote(String value, Path path, int line)
            throws PrefabValidationException {
        if (value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        if (first != '\'' && first != '"') {
            if (value.indexOf('[') >= 0
                    || value.indexOf('{') >= 0
                    || value.startsWith("&")
                    || value.startsWith("*")
                    || value.startsWith("!")) {
                throw invalid(path, line, "YAML collections, anchors, and tags are not supported");
            }
            return value;
        }
        if (value.length() < 2 || value.charAt(value.length() - 1) != first) {
            throw invalid(path, line, "unterminated quoted value");
        }
        String inner = value.substring(1, value.length() - 1);
        if (first == '\'') {
            return inner.replace("''", "'");
        }
        return inner.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static String required(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required field '" + key + "'");
        }
        return value;
    }

    private static int positiveInt(String value, String field) {
        long parsed = nonNegativeLong(value, field);
        if (parsed == 0 || parsed > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(field + " must be a positive integer");
        }
        return (int) parsed;
    }

    private static int rotation(String value) {
        long parsed = nonNegativeLong(value, "source-rotation");
        if (parsed > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("source-rotation is too large");
        }
        return (int) parsed;
    }

    private static long nonNegativeLong(String value, String field) {
        if (!value.matches("0|[1-9][0-9]*")) {
            throw new IllegalArgumentException(field + " must be a non-negative integer");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " is too large", exception);
        }
    }

    private static boolean strictBoolean(String value, String field) {
        if (value.equals("true")) {
            return true;
        }
        if (value.equals("false")) {
            return false;
        }
        throw new IllegalArgumentException(field + " must be true or false");
    }

    private static PrefabValidationException invalid(Path path, int zeroBasedLine, String issue) {
        return new PrefabValidationException(
                path.getFileName() + ":" + (zeroBasedLine + 1) + ": " + issue
        );
    }
}
