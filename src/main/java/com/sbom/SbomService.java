package com.sbom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sbom.model.QueryResult;
import com.sbom.model.SbomDocument;
import com.sbom.parser.CycloneDxParser;
import com.sbom.parser.SbomParser;
import com.sbom.store.SbomRepository;
import com.sbom.store.SqliteSbomRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The one entry point for all SBOM operations. The CLI only talks to this class,
 * so new features (or a REST API) can reuse it without touching parsing or storage.
 *
 * <pre>
 * try (SbomService sboms = SbomService.open("sbom.db")) {
 *     sboms.ingest(Path.of("app.cdx.json"));
 *     sboms.findByComponent("log4j-core", "2.14.1");
 *     sboms.findByLicense("MIT");
 * }
 * </pre>
 */
public class SbomService implements AutoCloseable {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final SbomRepository repository;
    private final List<SbomParser> parsers;

    public SbomService(SbomRepository repository, List<SbomParser> parsers) {
        this.repository = repository;
        this.parsers = parsers;
    }

    /** Default wiring: SQLite file at {@code dbPath}, CycloneDX parser. */
    public static SbomService open(String dbPath) {
        return new SbomService(new SqliteSbomRepository(dbPath), List.of(new CycloneDxParser()));
    }

    /** Parses and stores an SBOM file. Re-ingesting the same document replaces it. */
    public SbomDocument ingest(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("File not found: " + file);
        }
        JsonNode root = JSON.readTree(file.toFile());
        SbomParser parser = parsers.stream()
                .filter(p -> p.supports(root))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported SBOM format: " + file));
        SbomDocument doc = parser.parse(root, file.toString());
        repository.save(doc);
        return doc;
    }

    /** Finds components by name (case-insensitive). {@code version} may be null to match any version. */
    public List<QueryResult> findByComponent(String name, String version) {
        return repository.findByComponent(name, version);
    }

    /** Finds components carrying the given license (case-insensitive exact match). */
    public List<QueryResult> findByLicense(String license) {
        return repository.findByLicense(license);
    }

    @Override
    public void close() {
        repository.close();
    }
}
