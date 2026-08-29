package com.playtheatria.buildersWand.prefab;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * Thread-safe catalog whose live generation changes only after every sidecar and schematic passes.
 */
public final class PrefabCatalog {

    private final PrefabMetadataParser metadataParser;
    private final PrefabSchematicParser schematicParser;
    private final PrefabImporter importer;
    private final long defaultActivationUses;
    private final Clock clock;
    private final AtomicReference<PrefabCatalogSnapshot> live = new AtomicReference<>(
            PrefabCatalogSnapshot.empty()
    );

    public PrefabCatalog(
            PrefabSchematicParser schematicParser,
            PrefabImporter importer,
            long defaultActivationUses
    ) {
        this(
                new PrefabMetadataParser(),
                schematicParser,
                importer,
                defaultActivationUses,
                Clock.systemUTC()
        );
    }

    PrefabCatalog(
            PrefabMetadataParser metadataParser,
            PrefabSchematicParser schematicParser,
            PrefabImporter importer,
            long defaultActivationUses,
            Clock clock
    ) {
        this.metadataParser = Objects.requireNonNull(metadataParser, "metadataParser");
        this.schematicParser = Objects.requireNonNull(schematicParser, "schematicParser");
        this.importer = Objects.requireNonNull(importer, "importer");
        if (defaultActivationUses < 0) {
            throw new IllegalArgumentException("Default activation Uses cannot be negative");
        }
        this.defaultActivationUses = defaultActivationUses;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PrefabCatalogSnapshot snapshot() {
        return live.get();
    }

    /**
     * Loads a complete candidate generation. Any issue leaves the prior generation untouched.
     */
    public synchronized PrefabCatalogReloadResult reload(Path directory) {
        PrefabCatalogSnapshot previous = live.get();
        List<String> issues = new ArrayList<>();
        List<Path> metadataFiles = sidecars(directory, issues);
        if (!issues.isEmpty()) {
            return failed(previous, issues);
        }

        Map<String, PrefabDefinition> candidate = new LinkedHashMap<>();
        Path normalizedDirectory;
        try {
            normalizedDirectory = directory.toRealPath();
        } catch (IOException exception) {
            issues.add("Cannot resolve prefab directory: " + exception.getMessage());
            return failed(previous, issues);
        }

        for (Path metadataFile : metadataFiles) {
            try {
                PrefabMetadata metadata = metadataParser.parse(
                        metadataFile,
                        defaultActivationUses
                );
                Path schematic = normalizedDirectory.resolve(metadata.schematic()).normalize();
                if (!schematic.startsWith(normalizedDirectory)
                        || !Files.isRegularFile(schematic, LinkOption.NOFOLLOW_LINKS)) {
                    issues.add(metadataFile.getFileName() + ": schematic is missing or not a regular file: "
                            + metadata.schematic());
                    continue;
                }
                Path realSchematic = schematic.toRealPath();
                if (!realSchematic.getParent().equals(normalizedDirectory)) {
                    issues.add(metadataFile.getFileName()
                            + ": schematic symlinks outside the prefab directory");
                    continue;
                }
                RawPrefabSchematic raw = schematicParser.parse(realSchematic);
                PrefabDefinition definition = importer.importPrefab(metadata, raw);
                PrefabDefinition duplicate = candidate.putIfAbsent(metadata.id(), definition);
                if (duplicate != null) {
                    issues.add(metadataFile.getFileName() + ": duplicate prefab ID '"
                            + metadata.id() + "'");
                }
            } catch (PrefabValidationException exception) {
                issues.addAll(exception.issues());
            } catch (IOException | RuntimeException exception) {
                issues.add(metadataFile.getFileName() + ": " + safeMessage(exception));
            }
        }

        if (!issues.isEmpty()) {
            return failed(previous, issues);
        }
        PrefabCatalogSnapshot replacement = new PrefabCatalogSnapshot(
                previous.generation() + 1,
                Instant.now(clock),
                candidate
        );
        live.set(replacement);
        return new PrefabCatalogReloadResult(
                true,
                previous.generation(),
                replacement,
                List.of()
        );
    }

    private static List<Path> sidecars(Path directory, List<String> issues) {
        if (directory == null || !Files.isDirectory(directory)) {
            issues.add("Prefab catalog directory does not exist: " + directory);
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(PrefabCatalog::isMetadataFile)
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            issues.add("Cannot list prefab catalog directory: " + exception.getMessage());
            return List.of();
        }
    }

    private static boolean isMetadataFile(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private static PrefabCatalogReloadResult failed(
            PrefabCatalogSnapshot previous,
            List<String> issues
    ) {
        return new PrefabCatalogReloadResult(
                false,
                previous.generation(),
                previous,
                issues.stream().distinct().toList()
        );
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
