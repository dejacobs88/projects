package com.sbom.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.sbom.model.SbomDocument;

/**
 * Converts one SBOM format into the common {@link SbomDocument} model.
 * To support a new format (e.g. SPDX), implement this as a Spring @Component; SbomReader picks it up automatically.
 */
public interface SbomParser {

    /** True if this parser understands the given JSON (e.g. bomFormat == "CycloneDX"). */
    boolean supports(JsonNode root);

    SbomDocument parse(JsonNode root, String sourceFile);
}
