package com.playtheatria.buildersWand.prefab;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpongeSchematicV3ParserTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void readsWorldEditStyleSpongeV3PaletteDataAndEntityCounts() throws Exception {
        Path schematic = temporaryDirectory.resolve("fixture.schem");
        writeSchematic(
                schematic,
                3,
                2,
                1,
                2,
                Map.of(
                        "minecraft:air", 0,
                        "minecraft:stone", 1,
                        "minecraft:oak_stairs[facing=north,half=bottom]", 2
                ),
                new int[]{1, 0, 2, 1},
                2,
                1
        );

        RawPrefabSchematic parsed = new SpongeSchematicV3Parser().parse(schematic);

        assertEquals(new PrefabDimensions(2, 1, 2), parsed.dimensions());
        assertEquals("minecraft:stone", parsed.blockStateAt(0, 0, 0));
        assertEquals("minecraft:air", parsed.blockStateAt(1, 0, 0));
        assertEquals(
                "minecraft:oak_stairs[facing=north,half=bottom]",
                parsed.blockStateAt(0, 0, 1)
        );
        assertEquals(2, parsed.blockEntityCount());
        assertEquals(1, parsed.entityCount());
    }

    @Test
    void rejectsWrongVersionInvalidPaletteReferenceAndCellLimit() throws Exception {
        Path oldVersion = temporaryDirectory.resolve("v2.schem");
        writeSchematic(
                oldVersion,
                2,
                1,
                1,
                1,
                Map.of("minecraft:stone", 0),
                new int[]{0},
                0,
                0
        );
        assertThrows(
                PrefabValidationException.class,
                () -> new SpongeSchematicV3Parser().parse(oldVersion)
        );

        Path invalidIndex = temporaryDirectory.resolve("index.schem");
        writeSchematic(
                invalidIndex,
                3,
                1,
                1,
                1,
                Map.of("minecraft:stone", 0),
                new int[]{1},
                0,
                0
        );
        assertThrows(
                PrefabValidationException.class,
                () -> new SpongeSchematicV3Parser().parse(invalidIndex)
        );

        Path tooLarge = temporaryDirectory.resolve("large.schem");
        writeSchematic(
                tooLarge,
                3,
                2,
                1,
                1,
                Map.of("minecraft:stone", 0),
                new int[]{0, 0},
                0,
                0
        );
        assertThrows(
                PrefabValidationException.class,
                () -> new SpongeSchematicV3Parser(1, 1_000_000, 1_000_000).parse(tooLarge)
        );
    }

    private static void writeSchematic(
            Path path,
            int version,
            int width,
            int height,
            int length,
            Map<String, Integer> palette,
            int[] cells,
            int blockEntityCount,
            int entityCount
    ) throws IOException {
        try (DataOutputStream output = new DataOutputStream(
                new GZIPOutputStream(Files.newOutputStream(path))
        )) {
            output.writeByte(10);
            output.writeUTF("Schematic");
            intTag(output, "Version", version);
            intTag(output, "DataVersion", 4325);
            shortTag(output, "Width", width);
            shortTag(output, "Height", height);
            shortTag(output, "Length", length);
            intArrayTag(output, "Offset", new int[]{5, 70, -12});

            output.writeByte(10);
            output.writeUTF("Blocks");
            output.writeByte(10);
            output.writeUTF("Palette");
            for (Map.Entry<String, Integer> entry : palette.entrySet()) {
                intTag(output, entry.getKey(), entry.getValue());
            }
            output.writeByte(0);
            byteArrayTag(output, "Data", encodeVarInts(cells));
            emptyCompoundListTag(output, "BlockEntities", blockEntityCount);
            output.writeByte(0);

            emptyCompoundListTag(output, "Entities", entityCount);
            output.writeByte(0);
        }
    }

    private static void intTag(DataOutputStream output, String name, int value) throws IOException {
        output.writeByte(3);
        output.writeUTF(name);
        output.writeInt(value);
    }

    private static void shortTag(DataOutputStream output, String name, int value) throws IOException {
        output.writeByte(2);
        output.writeUTF(name);
        output.writeShort(value);
    }

    private static void intArrayTag(DataOutputStream output, String name, int[] values)
            throws IOException {
        output.writeByte(11);
        output.writeUTF(name);
        output.writeInt(values.length);
        for (int value : values) {
            output.writeInt(value);
        }
    }

    private static void byteArrayTag(DataOutputStream output, String name, byte[] values)
            throws IOException {
        output.writeByte(7);
        output.writeUTF(name);
        output.writeInt(values.length);
        output.write(values);
    }

    private static void emptyCompoundListTag(DataOutputStream output, String name, int size)
            throws IOException {
        output.writeByte(9);
        output.writeUTF(name);
        output.writeByte(10);
        output.writeInt(size);
        for (int index = 0; index < size; index++) {
            output.writeByte(0);
        }
    }

    private static byte[] encodeVarInts(int[] values) {
        List<Byte> bytes = new java.util.ArrayList<>();
        for (int value : values) {
            int remaining = value;
            do {
                int current = remaining & 0x7f;
                remaining >>>= 7;
                if (remaining != 0) {
                    current |= 0x80;
                }
                bytes.add((byte) current);
            } while (remaining != 0);
        }
        byte[] result = new byte[bytes.size()];
        for (int index = 0; index < bytes.size(); index++) {
            result[index] = bytes.get(index);
        }
        return result;
    }
}
