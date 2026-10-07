package com.sbom;

import com.sbom.model.QueryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// In-memory DB; @Transactional rolls back after each test so tests stay independent.
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:test")
@Transactional
class SbomServiceTest {

    @Autowired
    private SbomService sboms;

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
}
