package com.playtheatria.buildersWand.prefab;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Bounded reader for the Sponge Schematic v3 block subset written by WorldEdit 7.
 *
 * <p>WorldEdit's stored Offset, metadata, biomes, and clipboard origin do not affect the returned
 * coordinate volume. Block entities and entities are counted so the importer can reject them.</p>
 */
public final class SpongeSchematicV3Parser implements PrefabSchematicParser {

    public static final int DEFAULT_MAX_CELLS = 2_000_000;
    public static final long DEFAULT_MAX_COMPRESSED_BYTES = 32L * 1024 * 1024;
    public static final long DEFAULT_MAX_DECOMPRESSED_BYTES = 128L * 1024 * 1024;

    private final int maxCells;
    private final long maxCompressedBytes;
    private final long maxDecompressedBytes;

    public SpongeSchematicV3Parser() {
        this(DEFAULT_MAX_CELLS, DEFAULT_MAX_COMPRESSED_BYTES, DEFAULT_MAX_DECOMPRESSED_BYTES);
    }

    public SpongeSchematicV3Parser(
            int maxCells,
            long maxCompressedBytes,
            long maxDecompressedBytes
    ) {
        if (maxCells <= 0 || maxCompressedBytes <= 0 || maxDecompressedBytes <= 0) {
            throw new IllegalArgumentException("Schematic parser limits must be positive");
        }
        this.maxCells = maxCells;
        this.maxCompressedBytes = maxCompressedBytes;
        this.maxDecompressedBytes = maxDecompressedBytes;
    }

    @Override
    public RawPrefabSchematic parse(Path path) throws IOException, PrefabValidationException {
        long fileSize = Files.size(path);
        if (fileSize <= 0 || fileSize > maxCompressedBytes) {
            throw new PrefabValidationException(
                    path.getFileName() + ": compressed size is outside the allowed range"
            );
        }

        Map<String, Object> root;
        try (InputStream file = new BufferedInputStream(Files.newInputStream(path));
             InputStream gzip = new GZIPInputStream(file);
             InputStream limited = new BoundedInputStream(gzip, maxDecompressedBytes);
             DataInputStream input = new DataInputStream(limited)) {
            root = new NbtReader(input, maxCells).readRootCompound();
        } catch (EOFException exception) {
            throw new PrefabValidationException(path.getFileName() + ": truncated NBT data");
        } catch (IllegalArgumentException exception) {
            throw new PrefabValidationException(path.getFileName() + ": " + exception.getMessage());
        }

        Map<String, Object> schematic = compound(root.get("Schematic"), "Schematic", false);
        if (schematic == null) {
            schematic = root;
        }
        int version = integer(schematic.get("Version"), "Version");
        if (version != 3) {
            throw new PrefabValidationException(
                    path.getFileName() + ": expected Sponge Schematic Version 3, found " + version
            );
        }

        int width = positiveDimension(schematic.get("Width"), "Width");
        int height = positiveDimension(schematic.get("Height"), "Height");
        int length = positiveDimension(schematic.get("Length"), "Length");
        long volume = (long) width * height * length;
        if (volume > maxCells) {
            throw new PrefabValidationException(
                    path.getFileName() + ": schematic has " + volume
                            + " cells; parser limit is " + maxCells
            );
        }

        Map<String, Object> blocks = compound(schematic.get("Blocks"), "Blocks", true);
        Map<String, Object> paletteTag = compound(blocks.get("Palette"), "Blocks.Palette", true);
        List<String> palette = decodePalette(paletteTag);
        Object encoded = blocks.get("Data");
        if (!(encoded instanceof byte[] bytes)) {
            throw new PrefabValidationException(path.getFileName() + ": Blocks.Data must be a byte array");
        }
        int[] paletteIndices = decodeVarInts(bytes, Math.toIntExact(volume), palette.size());
        int blockEntities = listSize(blocks.get("BlockEntities"), "Blocks.BlockEntities");
        int entities = listSize(schematic.get("Entities"), "Entities");

        return new RawPrefabSchematic(
                new PrefabDimensions(width, height, length),
                palette,
                paletteIndices,
                blockEntities,
                entities
        );
    }

    private static List<String> decodePalette(Map<String, Object> paletteTag)
            throws PrefabValidationException {
        if (paletteTag.isEmpty()) {
            throw new PrefabValidationException("Blocks.Palette cannot be empty");
        }
        int highest = -1;
        for (Object value : paletteTag.values()) {
            int index = integer(value, "palette index");
            if (index < 0) {
                throw new PrefabValidationException("Palette indices cannot be negative");
            }
            highest = Math.max(highest, index);
        }
        if (highest >= paletteTag.size()) {
            throw new PrefabValidationException("Palette indices must be contiguous from zero");
        }
        String[] byIndex = new String[highest + 1];
        for (Map.Entry<String, Object> entry : paletteTag.entrySet()) {
            int index = integer(entry.getValue(), "palette index");
            if (byIndex[index] != null) {
                throw new PrefabValidationException("Duplicate palette index " + index);
            }
            try {
                byIndex[index] = BlockStateString.parse(entry.getKey()).canonical();
            } catch (IllegalArgumentException exception) {
                throw new PrefabValidationException("Invalid palette state: " + exception.getMessage());
            }
        }
        if (Arrays.stream(byIndex).anyMatch(value -> value == null)) {
            throw new PrefabValidationException("Palette indices must be contiguous from zero");
        }
        return List.of(byIndex);
    }

    private static int[] decodeVarInts(byte[] bytes, int expected, int paletteSize)
            throws PrefabValidationException {
        int[] decoded = new int[expected];
        int byteIndex = 0;
        for (int cell = 0; cell < expected; cell++) {
            int value = 0;
            int shift = 0;
            while (true) {
                if (byteIndex >= bytes.length) {
                    throw new PrefabValidationException("Blocks.Data ended before all cells were decoded");
                }
                int current = bytes[byteIndex++] & 0xff;
                value |= (current & 0x7f) << shift;
                if ((current & 0x80) == 0) {
                    break;
                }
                shift += 7;
                if (shift >= 35) {
                    throw new PrefabValidationException("Blocks.Data contains an oversized VarInt");
                }
            }
            if (value < 0 || value >= paletteSize) {
                throw new PrefabValidationException("Blocks.Data references palette index " + value);
            }
            decoded[cell] = value;
        }
        if (byteIndex != bytes.length) {
            throw new PrefabValidationException("Blocks.Data contains trailing cell data");
        }
        return decoded;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> compound(Object value, String name, boolean required)
            throws PrefabValidationException {
        if (value == null && !required) {
            return null;
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new PrefabValidationException(name + " must be an NBT compound");
        }
        return (Map<String, Object>) map;
    }

    private static int integer(Object value, String name) throws PrefabValidationException {
        if (!(value instanceof Number number)) {
            throw new PrefabValidationException(name + " must be an integer NBT value");
        }
        long result = number.longValue();
        if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) {
            throw new PrefabValidationException(name + " is outside the integer range");
        }
        return (int) result;
    }

    private static int positiveDimension(Object value, String name)
            throws PrefabValidationException {
        int result = integer(value, name);
        if (result <= 0 || result > 65_535) {
            throw new PrefabValidationException(name + " must be between 1 and 65535");
        }
        return result;
    }

    private static int listSize(Object value, String name) throws PrefabValidationException {
        if (value == null) {
            return 0;
        }
        if (!(value instanceof NbtList list)) {
            throw new PrefabValidationException(name + " must be an NBT list");
        }
        return list.values().size();
    }

    private record NbtList(byte elementType, List<Object> values) {
        private NbtList {
            values = List.copyOf(values);
        }
    }

    private static final class NbtReader {

        private static final int MAX_DEPTH = 64;
        private static final int MAX_COLLECTION_LENGTH = 16_000_000;

        private final DataInputStream input;
        private final int maxCells;

        private NbtReader(DataInputStream input, int maxCells) {
            this.input = input;
            this.maxCells = maxCells;
        }

        private Map<String, Object> readRootCompound() throws IOException {
            byte type = input.readByte();
            if (type != 10) {
                throw new IllegalArgumentException("NBT root must be a compound");
            }
            input.readUTF(); // Root name is intentionally ignored.
            @SuppressWarnings("unchecked")
            Map<String, Object> root = (Map<String, Object>) readPayload(type, 0);
            return root;
        }

        private Object readPayload(byte type, int depth) throws IOException {
            if (depth > MAX_DEPTH) {
                throw new IllegalArgumentException("NBT nesting exceeds " + MAX_DEPTH + " levels");
            }
            return switch (type) {
                case 1 -> input.readByte();
                case 2 -> input.readShort();
                case 3 -> input.readInt();
                case 4 -> input.readLong();
                case 5 -> input.readFloat();
                case 6 -> input.readDouble();
                case 7 -> readByteArray();
                case 8 -> input.readUTF();
                case 9 -> readList(depth + 1);
                case 10 -> readCompound(depth + 1);
                case 11 -> readIntArray();
                case 12 -> readLongArray();
                default -> throw new IllegalArgumentException("Unsupported NBT tag type " + type);
            };
        }

        private byte[] readByteArray() throws IOException {
            int length = validLength(input.readInt(), "byte array");
            byte[] result = new byte[length];
            input.readFully(result);
            return result;
        }

        private int[] readIntArray() throws IOException {
            int length = validLength(input.readInt(), "int array");
            int[] result = new int[length];
            for (int index = 0; index < length; index++) {
                result[index] = input.readInt();
            }
            return result;
        }

        private long[] readLongArray() throws IOException {
            int length = validLength(input.readInt(), "long array");
            long[] result = new long[length];
            for (int index = 0; index < length; index++) {
                result[index] = input.readLong();
            }
            return result;
        }

        private NbtList readList(int depth) throws IOException {
            byte elementType = input.readByte();
            int length = validLength(input.readInt(), "list");
            if (elementType == 0 && length != 0) {
                throw new IllegalArgumentException("Non-empty NBT list cannot use TAG_End");
            }
            List<Object> values = new ArrayList<>(Math.min(length, maxCells));
            for (int index = 0; index < length; index++) {
                values.add(readPayload(elementType, depth));
            }
            return new NbtList(elementType, values);
        }

        private Map<String, Object> readCompound(int depth) throws IOException {
            Map<String, Object> result = new LinkedHashMap<>();
            while (true) {
                byte type = input.readByte();
                if (type == 0) {
                    return result;
                }
                String name = input.readUTF();
                if (result.putIfAbsent(name, readPayload(type, depth)) != null) {
                    throw new IllegalArgumentException("Duplicate NBT compound key: " + name);
                }
            }
        }

        private static int validLength(int length, String description) {
            if (length < 0 || length > MAX_COLLECTION_LENGTH) {
                throw new IllegalArgumentException("NBT " + description + " length is unsafe: " + length);
            }
            return length;
        }
    }

    private static final class BoundedInputStream extends FilterInputStream {

        private long remaining;

        private BoundedInputStream(InputStream input, long maximumBytes) {
            super(input);
            this.remaining = maximumBytes;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                throw new IOException("Decompressed schematic exceeds configured byte limit");
            }
            int value = super.read();
            if (value >= 0) {
                remaining--;
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            if (remaining <= 0) {
                throw new IOException("Decompressed schematic exceeds configured byte limit");
            }
            int allowed = (int) Math.min(length, remaining);
            int read = super.read(bytes, offset, allowed);
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }
    }
}
