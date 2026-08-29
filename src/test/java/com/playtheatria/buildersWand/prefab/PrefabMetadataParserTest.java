package com.playtheatria.buildersWand.prefab;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrefabMetadataParserTest {

    @TempDir
    Path temporaryDirectory;

    private final PrefabMetadataParser parser = new PrefabMetadataParser();

    @Test
    void parsesDocumentedFlatSidecarAndDefaultsActivationUses() throws Exception {
        Path path = write("starter.yml", """
                # Server-owned prefab metadata
                id: starter_house
                name: "Starter # House"
                version: 3
                schematic: starter_house.schem
                source-rotation: 270
                allow-rotation: false
                clearance: schematic-air
                """);

        PrefabMetadata metadata = parser.parse(path, 25);

        assertEquals("starter_house", metadata.id());
        assertEquals("Starter # House", metadata.name());
        assertEquals(3, metadata.version());
        assertEquals(270, metadata.sourceRotationDegrees());
        assertEquals(3, metadata.sourceQuarterTurns());
        assertEquals(25, metadata.activationUses());
        assertFalse(metadata.allowRotation());
        assertEquals(PrefabClearance.SCHEMATIC_AIR, metadata.clearance());
    }

    @Test
    void rejectsUnknownDuplicateNestedAndTraversalFields() throws IOException {
        assertInvalid("unknown.yml", """
                id: house
                name: House
                version: 1
                schematic: house.schem
                price: 150000
                """);
        assertInvalid("duplicate.yml", """
                id: house
                id: other
                name: House
                version: 1
                schematic: house.schem
                """);
        assertInvalid("nested.yml", """
                id: house
                name: House
                version: 1
                schematic: house.schem
                  unsafe: true
                """);
        assertInvalid("traversal.yml", """
                id: house
                name: House
                version: 1
                schematic: ../house.schem
                """);
    }

    @Test
    void rejectsAmbiguousNumbersBooleansAndRotations() throws IOException {
        assertInvalid("number.yml", base().replace("version: 1", "version: 01"));
        assertInvalid("boolean.yml", base() + "allow-rotation: yes\n");
        assertInvalid("rotation.yml", base() + "source-rotation: 45\n");
        assertInvalid("uses.yml", base() + "activation-uses: -1\n");
    }

    @Test
    void rejectsIdsReservedByPrefabCommands() throws IOException {
        assertInvalid("reserved.yml", base().replace("id: house", "id: confirm"));
    }

    private void assertInvalid(String name, String content) throws IOException {
        Path path = write(name, content);
        assertThrows(PrefabValidationException.class, () -> parser.parse(path, 20));
    }

    private String base() {
        return """
                id: house
                name: House
                version: 1
                schematic: house.schem
                """;
    }

    private Path write(String name, String content) throws IOException {
        Path path = temporaryDirectory.resolve(name);
        java.nio.file.Files.writeString(path, content);
        return path;
    }
}
