package com.sbom;

import com.fasterxml.jackson.databind.JsonNode;
import com.sbom.model.Component;
import com.sbom.model.QueryResult;
import com.sbom.model.SbomDocument;
import com.sbom.parser.SbomParser;
import com.sbom.store.ComponentEntity;
import com.sbom.store.ComponentRepository;
import com.sbom.store.DocumentEntity;
import com.sbom.store.DocumentRepository;
import com.sbom.util.JsonUtil;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The one entry point for all SBOM operations. The CLI only talks to this class,
 * so new features (or a REST API) can reuse it without touching parsing or storage.
 * New queries: declare a method on {@link ComponentRepository}, expose it here.
 */
@Service
public class SbomService {

    private static final Sort RESULT_ORDER = Sort.by("document.name", "name", "version");

    private final DocumentRepository documents;
    private final ComponentRepository components;
    private final List<SbomParser> parsers; // every @Component implementing SbomParser

    public SbomService(DocumentRepository documents, ComponentRepository components, List<SbomParser> parsers) {
        this.documents = documents;
        this.components = components;
        this.parsers = parsers;
    }

    /** Parses and stores an SBOM file. Re-ingesting the same document replaces it. */
    @Transactional
    public SbomDocument ingest(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("File not found: " + file);
        }
        JsonNode root = JsonUtil.readTree(file);
        SbomParser parser = parsers.stream()
                .filter(p -> p.supports(root))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported SBOM format: " + file));
        SbomDocument doc = parser.parse(root, file.toString());

        documents.findBySerialNumber(doc.serialNumber()).ifPresent(existing -> {
            documents.delete(existing);
            documents.flush(); // free the unique serial number before re-inserting
        });
        DocumentEntity entity = new DocumentEntity(doc.name(), doc.serialNumber(), doc.sourceFile());
        for (Component c : doc.components()) {
            if (c.name() != null) {
                entity.addComponent(new ComponentEntity(c.name(), c.version(), c.purl(), c.licenses()));
            }
        }
        documents.save(entity);
        return doc;
    }

    /** Finds components by name (case-insensitive). {@code version} may be null to match any version. */
    @Transactional(readOnly = true)
    public List<QueryResult> findByComponent(String name, String version) {
        List<ComponentEntity> found = version == null
                ? components.findByNameIgnoreCase(name, RESULT_ORDER)
                : components.findByNameIgnoreCaseAndVersion(name, version, RESULT_ORDER);
        return toResults(found);
    }

    /** Finds components carrying the given license (case-insensitive exact match). */
    @Transactional(readOnly = true)
    public List<QueryResult> findByLicense(String license) {
        return toResults(components.findByLicensesIgnoreCase(license, RESULT_ORDER));
    }

    private static List<QueryResult> toResults(List<ComponentEntity> found) {
        return found.stream()
                .map(c -> new QueryResult(
                        c.getDocument().getName(),
                        c.getName(),
                        c.getVersion(),
                        c.getLicenses().isEmpty() ? null : String.join(", ", c.getLicenses())))
                .toList();
    }
}
