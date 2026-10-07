package com.sbom;

import com.sbom.model.QueryResult;
import com.sbom.model.SbomDocument;
import com.sbom.parser.SbomReader;
import com.sbom.store.ComponentEntity;
import com.sbom.store.ComponentRepository;
import com.sbom.store.DocumentEntity;
import com.sbom.store.DocumentRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final SbomReader reader;
    private final DocumentRepository documents;
    private final ComponentRepository components;

    public SbomService(SbomReader reader, DocumentRepository documents, ComponentRepository components) {
        this.reader = reader;
        this.documents = documents;
        this.components = components;
    }

    /** Parses and stores an SBOM file. Re-ingesting the same document replaces it. */
    @Transactional
    public SbomDocument ingest(Path file) {
        SbomDocument doc = reader.read(file);
        documents.replace(DocumentEntity.from(doc));
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
        return found.stream().map(ComponentEntity::toResult).toList();
    }
}
