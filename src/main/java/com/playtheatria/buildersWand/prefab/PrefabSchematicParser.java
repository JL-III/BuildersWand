package com.playtheatria.buildersWand.prefab;

import java.io.IOException;
import java.nio.file.Path;

/** Parser seam so catalog tests and future format versions do not depend on WorldEdit. */
@FunctionalInterface
public interface PrefabSchematicParser {

    RawPrefabSchematic parse(Path path) throws IOException, PrefabValidationException;
}
