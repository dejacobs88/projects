package com.sbom.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileUtilTest {

    @TempDir
    Path tmp;

    @Test
    void expandsDirectoriesRecursivelyToSortedJsonFiles() throws Exception {
        Files.createDirectories(tmp.resolve("team-b"));
        Files.writeString(tmp.resolve("b.json"), "{}");
        Files.writeString(tmp.resolve("a.JSON"), "{}");
        Files.writeString(tmp.resolve("notes.txt"), "ignored");
        Files.writeString(tmp.resolve("team-b/c.json"), "{}");

        assertEquals(List.of(tmp.resolve("a.JSON"), tmp.resolve("b.json"), tmp.resolve("team-b/c.json")),
                FileUtil.expandJsonFiles(List.of(tmp)));
    }

    @Test
    void passesFilesAndMissingPathsThrough() {
        Path file = Path.of("samples/cyclone1_6.json");
        Path missing = Path.of("nope.json");
        assertEquals(List.of(file, missing), FileUtil.expandJsonFiles(List.of(file, missing)));
    }

    @Test
    void dropsDuplicates() throws Exception {
        Files.writeString(tmp.resolve("a.json"), "{}");
        assertEquals(List.of(tmp.resolve("a.json")),
                FileUtil.expandJsonFiles(List.of(tmp, tmp.resolve("a.json"), tmp.resolve("./a.json"))));
    }

    @Test
    void emptyDirectoryExpandsToNothing() {
        assertTrue(FileUtil.expandJsonFiles(List.of(tmp)).isEmpty());
    }
}
