package com.sbom;

import com.sbom.model.DocumentSummary;
import com.sbom.model.IngestResult;
import com.sbom.model.IngestResult.Outcome;
import com.sbom.model.QueryResult;
import com.sbom.model.SbomDocument;
import com.sbom.parser.SbomReader;
import com.sbom.store.ComponentEntity;
import com.sbom.store.ComponentRepository;
import com.sbom.store.DocumentEntity;
import com.sbom.store.DocumentRepository;
import com.sbom.util.Logger;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

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
    private final Logger log;

    public SbomService(SbomReader reader, DocumentRepository documents, ComponentRepository components, Logger log) {
        this.reader = reader;
        this.documents = documents;
        this.components = components;
        this.log = log;
    }

    /** Parses and stores an SBOM file. Same document + new content replaces it; identical content is skipped. */
    @Transactional
    public IngestResult ingest(Path file) {
        SbomDocument doc = reader.read(file);
        if (documents.existsBySerialNumberAndSha256(doc.serialNumber(), doc.source().sha256())) {
            log.info("'%s' is unchanged since last ingest; skipping", doc.name());
            return new IngestResult(doc, Outcome.UNCHANGED);
        }
        Outcome outcome = documents.existsBySerialNumber(doc.serialNumber()) ? Outcome.REPLACED : Outcome.CREATED;
        documents.replace(DocumentEntity.from(doc));
        log.info("Saved '%s' (%s, serial %s)", doc.name(), outcome, doc.serialNumber());
        return new IngestResult(doc, outcome);
    }

    /** Finds components by name (case-insensitive). {@code version} may be null to match any version. */
    @Transactional(readOnly = true)
    public List<QueryResult> findByComponent(String name, String version) {
        return timed("component=" + name + (version == null ? "" : " version=" + version), () ->
                version == null
                        ? components.findByNameIgnoreCase(name, RESULT_ORDER)
                        : components.findByNameIgnoreCaseAndVersion(name, version, RESULT_ORDER));
    }

    /** Finds components carrying the given license (case-insensitive exact match). */
    @Transactional(readOnly = true)
    public List<QueryResult> findByLicense(String license) {
        return timed("license=" + license, () -> components.findByLicensesIgnoreCase(license, RESULT_ORDER));
    }

    /** Lists every ingested SBOM, alphabetically by name. */
    @Transactional(readOnly = true)
    public List<DocumentSummary> listDocuments() {
        return documents.findAll(Sort.by("name")).stream().map(DocumentEntity::toSummary).toList();
    }

    /** Runs a component query, maps it to results, and logs what was asked and how long it took. */
    private List<QueryResult> timed(String description, Supplier<List<ComponentEntity>> query) {
        long start = System.nanoTime();
        List<QueryResult> results = query.get().stream().map(ComponentEntity::toResult).toList();
        log.info("Query [%s]: %d result(s) in %d ms", description, results.size(), (System.nanoTime() - start) / 1_000_000);
        return results;
    }
}
