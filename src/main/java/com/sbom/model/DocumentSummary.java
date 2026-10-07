package com.sbom.model;

import java.time.Instant;

/** One ingested SBOM, as shown by the list command. */
public record DocumentSummary(String name, String serialNumber, String sourceFile, int componentCount, Instant ingestedAt) {}
