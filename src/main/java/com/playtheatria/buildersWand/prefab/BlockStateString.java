package com.playtheatria.buildersWand.prefab;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Parser, canonicalizer, and deterministic quarter-turn transformer for block-state strings. */
final class BlockStateString {

    private static final Pattern BLOCK_ID = Pattern.compile(
            "[a-z0-9_.-]+:[a-z0-9_./-]+"
    );
    private static final Pattern PROPERTY_TOKEN = Pattern.compile("[a-z0-9_.-]+");
    private static final List<String> AIR_IDS = List.of(
            "minecraft:air",
            "minecraft:cave_air",
            "minecraft:void_air"
    );

    private final String id;
    private final Map<String, String> properties;

    private BlockStateString(String id, Map<String, String> properties) {
        this.id = id;
        this.properties = Collections.unmodifiableMap(new TreeMap<>(properties));
    }

    static BlockStateString parse(String input) {
        Objects.requireNonNull(input, "blockState");
        String raw = input.trim().toLowerCase(Locale.ROOT);
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("Block state cannot be blank");
        }
        int opening = raw.indexOf('[');
        String id;
        String propertiesSection = null;
        if (opening < 0) {
            id = raw;
        } else {
            if (!raw.endsWith("]") || raw.indexOf('[', opening + 1) >= 0) {
                throw new IllegalArgumentException("Malformed block state: " + input);
            }
            id = raw.substring(0, opening);
            propertiesSection = raw.substring(opening + 1, raw.length() - 1);
        }
        if (id.indexOf(':') < 0) {
            id = "minecraft:" + id;
        }
        if (!BLOCK_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid block ID: " + id);
        }

        Map<String, String> properties = new LinkedHashMap<>();
        if (propertiesSection != null && !propertiesSection.isEmpty()) {
            for (String assignment : propertiesSection.split(",", -1)) {
                int separator = assignment.indexOf('=');
                if (separator <= 0 || separator == assignment.length() - 1) {
                    throw new IllegalArgumentException("Malformed block property: " + assignment);
                }
                String key = assignment.substring(0, separator);
                String value = assignment.substring(separator + 1);
                if (!PROPERTY_TOKEN.matcher(key).matches()
                        || !PROPERTY_TOKEN.matcher(value).matches()) {
                    throw new IllegalArgumentException("Invalid block property: " + assignment);
                }
                if (properties.putIfAbsent(key, value) != null) {
                    throw new IllegalArgumentException("Duplicate block property: " + key);
                }
            }
        }
        return new BlockStateString(id, properties);
    }

    String id() {
        return id;
    }

    Map<String, String> properties() {
        return properties;
    }

    boolean isAir() {
        return AIR_IDS.contains(id);
    }

    boolean isStructureVoid() {
        return id.equals("minecraft:structure_void");
    }

    String canonical() {
        if (properties.isEmpty()) {
            return id;
        }
        List<String> assignments = new ArrayList<>(properties.size());
        properties.forEach((key, value) -> assignments.add(key + "=" + value));
        return id + "[" + String.join(",", assignments) + "]";
    }

    BlockStateString rotateClockwise(int quarterTurns) {
        int turns = Math.floorMod(quarterTurns, 4);
        if (turns == 0 || properties.isEmpty()) {
            return this;
        }
        Map<String, String> rotated = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            String oldKey = entry.getKey();
            String newKey = rotateDirectionalTokens(oldKey, turns);
            String newValue;
            if (oldKey.equals("axis")) {
                newValue = turns % 2 == 1 ? swapHorizontalAxis(entry.getValue()) : entry.getValue();
            } else if (oldKey.equals("rotation")) {
                newValue = rotateSixteenth(entry.getValue(), turns);
            } else {
                newValue = rotateDirectionalTokens(entry.getValue(), turns);
            }
            if (rotated.putIfAbsent(newKey, newValue) != null) {
                throw new IllegalArgumentException(
                        "Rotating block state creates duplicate property '" + newKey + "': " + canonical()
                );
            }
        }
        return new BlockStateString(id, rotated);
    }

    private static String swapHorizontalAxis(String value) {
        return switch (value) {
            case "x" -> "z";
            case "z" -> "x";
            default -> value;
        };
    }

    private static String rotateSixteenth(String value, int quarterTurns) {
        try {
            int rotation = Integer.parseInt(value);
            if (rotation < 0 || rotation > 15) {
                return value;
            }
            return Integer.toString(Math.floorMod(rotation + quarterTurns * 4, 16));
        } catch (NumberFormatException ignored) {
            return value;
        }
    }

    private static String rotateDirectionalTokens(String value, int quarterTurns) {
        String[] tokens = value.split("_", -1);
        for (int index = 0; index < tokens.length; index++) {
            tokens[index] = rotateDirection(tokens[index], quarterTurns);
        }
        String rotated = String.join("_", tokens);
        // Minecraft names straight rail/connection pairs in a fixed order. A 180-degree turn is
        // semantically identical and must not manufacture non-existent values such as south_north.
        return switch (rotated) {
            case "south_north" -> "north_south";
            case "west_east" -> "east_west";
            default -> rotated;
        };
    }

    private static String rotateDirection(String direction, int quarterTurns) {
        int initial = switch (direction) {
            case "north" -> 0;
            case "east" -> 1;
            case "south" -> 2;
            case "west" -> 3;
            default -> -1;
        };
        if (initial < 0) {
            return direction;
        }
        return switch (Math.floorMod(initial + quarterTurns, 4)) {
            case 0 -> "north";
            case 1 -> "east";
            case 2 -> "south";
            default -> "west";
        };
    }
}
