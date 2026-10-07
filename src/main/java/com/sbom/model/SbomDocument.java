package com.sbom.model;

import java.util.List;

/** Format-agnostic view of an SBOM. Every parser produces this; the store only ever sees this. */
public record SbomDocument(String name, String serialNumber, String sourceFile, List<Component> components) {}
