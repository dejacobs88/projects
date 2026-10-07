package com.sbom.parser;

import com.sbom.model.SbomDocument;
import com.sbom.model.SbomSource;
import com.sbom.util.JsonUtil;
import com.sbom.util.Logger;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Turns an SBOM file into an {@link SbomDocument}, picking the right parser by sniffing the JSON. */
@Component
public class SbomReader {

    private final List<SbomParser> parsers; // every Spring bean implementing SbomParser
    private final Logger log;

    public SbomReader(List<SbomParser> parsers, Logger log) {
        this.parsers = parsers;
        this.log = log;
    }

    public SbomDocument read(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("File not found: " + file);
        }
        SbomSource source = load(file);
        log.info("Read %s (sha256 %s)", file, source.sha256());

        Map<String, Object> root;
        try {
            root = JsonUtil.readMap(source.content());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(file + ": " + e.getMessage(), e);
        }
        SbomParser parser = parsers.stream()
                .filter(p -> p.supports(root))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported SBOM format: " + file));
        log.info("Detected format, using %s", parser.getClass().getSimpleName());
        return parser.parse(root, source);
    }

    private static SbomSource load(Path file) {
        try {
            return SbomSource.of(file.toString(), Files.readAllBytes(file));
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read " + file + ": " + e.getMessage(), e);
        }
    }
}
