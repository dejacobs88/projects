package com.sbom.model;

import java.util.List;

/**
 * Format-agnostic view of an SBOM. Every parser produces this; the store only ever sees this.
 * {@code lifecycle} is the SBOM type/stage (e.g. "build", "operations"), or null if the document doesn't say.
 */
public record SbomDocument(String name, String serialNumber, String lifecycle, SbomSource source,
                           List<Component> components) {}
