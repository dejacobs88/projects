package com.sbom.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.sbom.model.SbomDocument;
import com.sbom.util.JsonUtil;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Turns an SBOM file into an {@link SbomDocument}, picking the right parser by sniffing the JSON. */
@Component
public class SbomReader {

    private final List<SbomParser> parsers; // every Spring bean implementing SbomParser

    public SbomReader(List<SbomParser> parsers) {
        this.parsers = parsers;
    }

    public SbomDocument read(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("File not found: " + file);
        }
        JsonNode root = JsonUtil.readTree(file);
        return parsers.stream()
                .filter(p -> p.supports(root))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported SBOM format: " + file))
                .parse(root, file.toString());
    }
}
