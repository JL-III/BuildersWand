package com.playtheatria.buildersWand.prefab;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/** Canonical semantic hash used to invalidate stale prefab quotes across catalog reloads. */
final class PrefabContentHasher {

    private PrefabContentHasher() {
    }

    static String hash(
            PrefabMetadata metadata,
            PrefabDimensions dimensions,
            List<PrefabCell> cells
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DataOutputStream output = new DataOutputStream(new DigestOutputStream(
                    java.io.OutputStream.nullOutputStream(),
                    digest
            ))) {
                writeString(output, "BuildersWand-Prefab-v1");
                writeString(output, metadata.id());
                writeString(output, metadata.name());
                output.writeInt(metadata.version());
                output.writeInt(metadata.sourceRotationDegrees());
                output.writeLong(metadata.activationUses());
                output.writeBoolean(metadata.allowRotation());
                writeString(output, metadata.clearance().metadataValue());
                output.writeInt(dimensions.width());
                output.writeInt(dimensions.height());
                output.writeInt(dimensions.depth());
                List<PrefabCell> ordered = cells.stream()
                        .sorted(Comparator.comparing(PrefabCell::position))
                        .toList();
                output.writeInt(ordered.size());
                for (PrefabCell cell : ordered) {
                    output.writeInt(cell.position().x());
                    output.writeInt(cell.position().y());
                    output.writeInt(cell.position().z());
                    output.writeByte(cell.kind().ordinal());
                    writeString(output, cell.blockState() == null ? "" : cell.blockState());
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide SHA-256", exception);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not hash in-memory prefab data", exception);
        }
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(encoded.length);
        output.write(encoded);
    }
}
