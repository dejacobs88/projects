package com.sbom;

import com.sbom.model.DocumentSummary;
import com.sbom.model.IngestResult.Outcome;
import com.sbom.model.QueryResult;
import com.sbom.store.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// In-memory DB; @Transactional rolls back after each test so tests stay independent.
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:test;IGNORECASE=TRUE")
@Transactional
class SbomServiceTest {

    private static final Path PAYMENTS = Path.of("samples/payments-service.cdx.json");

    @Autowired
    private SbomService sboms;

    @Autowired
    private DocumentRepository documents;

    @TempDir
    Path tmp;

    @BeforeEach
    void setUp() throws Exception {
        sboms.ingest(Path.of("samples/payments-service.cdx.json"));
        sboms.ingest(Path.of("samples/web-frontend.cdx.json"));
        sboms.ingest(Path.of("samples/cyclone1_6.json"));
    }

    @Test
    void findsComponentAcrossDocuments() {
        List<QueryResult> results = sboms.findByComponent("log4j-core", null);
        assertEquals(2, results.size());
    }

    @Test
    void filtersComponentByVersion() {
        List<QueryResult> results = sboms.findByComponent("log4j-core", "2.14.1");
        assertEquals(1, results.size());
        assertEquals("payments-service", results.get(0).documentName());
    }

    @Test
    void componentNameIsCaseInsensitive() {
        assertEquals(1, sboms.findByComponent("brotli", null).size());
    }

    @Test
    void findsNestedComponents() {
        assertEquals(1, sboms.findByComponent("snakeyaml", "1.30").size());
    }

    @Test
    void findsByLicense() {
        List<QueryResult> mit = sboms.findByLicense("MIT");
        assertEquals(3, mit.size()); // lodash, react (expression), Brotli
        assertEquals(3, sboms.findByLicense("mit").size()); // case-insensitive
    }

    @Test
    void multiLicenseComponentIsOneRow() {
        List<QueryResult> results = sboms.findByComponent("logback-classic", null);
        assertEquals(1, results.size());
        assertEquals("EPL-1.0, LGPL-2.1-only", results.get(0).licenses());
    }

    @Test
    void reIngestDoesNotDuplicate() throws Exception {
        sboms.ingest(Path.of("samples/payments-service.cdx.json"));
        sboms.ingest(Path.of("samples/cyclone1_6.json")); // no serialNumber: keyed by file path
        assertEquals(2, sboms.findByComponent("log4j-core", null).size());
        assertEquals(1, sboms.findByComponent("Brotli", null).size());
    }

    @Test
    void unknownComponentReturnsEmpty() {
        assertTrue(sboms.findByComponent("does-not-exist", null).isEmpty());
    }

    @Test
    void rejectsMissingFile() {
        assertThrows(IllegalArgumentException.class, () -> sboms.ingest(Path.of("nope.json")));
    }

    @Test
    void listsAllDocumentsSortedByName() {
        List<DocumentSummary> docs = sboms.listDocuments();
        assertEquals(List.of("cyclone1_6.json", "payments-service", "web-frontend"),
                docs.stream().map(DocumentSummary::name).toList());
        assertEquals(5, docs.get(1).componentCount()); // includes the nested snakeyaml
    }

    @Test
    void storesRawJsonAndHash() throws Exception {
        var doc = documents.findBySerialNumber("urn:uuid:3e671687-395b-41f5-a30f-a58921a69b79").orElseThrow();
        assertEquals(Files.readString(PAYMENTS), doc.getRawJson());
        assertEquals(64, doc.getSha256().length());
    }

    @Test
    void identicalReIngestIsUnchanged() {
        assertEquals(Outcome.UNCHANGED, sboms.ingest(PAYMENTS).outcome());
    }

    @Test
    void changedContentReplacesDocument() throws Exception {
        Path edited = tmp.resolve("payments-v2.json");
        Files.writeString(edited, Files.readString(PAYMENTS).replace("2.14.1", "2.17.2"));

        assertEquals(Outcome.REPLACED, sboms.ingest(edited).outcome());
        assertTrue(sboms.findByComponent("log4j-core", "2.14.1").isEmpty());
        assertEquals(1, sboms.findByComponent("log4j-core", "2.17.2").size());
        assertEquals(3, sboms.listDocuments().size());
    }
}
