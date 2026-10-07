package com.sbom.parser;

import com.sbom.model.SbomDocument;
import com.sbom.model.SbomSource;

import java.util.Map;

/**
 * Converts one SBOM format into the common {@link SbomDocument} model.
 * To support a new format (e.g. SPDX), implement this as a Spring @Component; SbomReader picks it up automatically.
 */
public interface SbomParser {

    /** True if this parser understands the given JSON (e.g. bomFormat == "CycloneDX"). */
    boolean supports(Map<String, Object> root);

    /** @param source the raw file this JSON came from; carried through so it can be stored */
    SbomDocument parse(Map<String, Object> root, SbomSource source);
}
