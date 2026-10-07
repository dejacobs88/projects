package com.sbom.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** File-system helpers. */
public final class FileUtil {

    private FileUtil() {
    }

    /**
     * Expands paths into the files to process: a directory becomes every {@code .json} file beneath it
     * (recursively, sorted); anything else is passed through as-is, so a missing file still surfaces
     * its own "not found" error downstream. Duplicates (e.g. a directory plus a file inside it) are dropped.
     */
    public static List<Path> expandJsonFiles(List<Path> paths) {
        Set<Path> files = new LinkedHashSet<>();
        for (Path path : paths) {
            if (Files.isDirectory(path)) {
                files.addAll(jsonFilesIn(path));
            } else {
                files.add(path.normalize());
            }
        }
        return List.copyOf(files);
    }

    private static List<Path> jsonFilesIn(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.map(Path::normalize)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".json"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read directory " + dir, e);
        }
    }
}
