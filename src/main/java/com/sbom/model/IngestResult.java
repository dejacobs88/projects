package com.sbom.model;

/** What happened when a file was ingested. */
public record IngestResult(SbomDocument document, Outcome outcome) {

    public enum Outcome {
        /** First time this document was seen. */
        CREATED,
        /** Same document (serial number), new content: old copy replaced. */
        REPLACED,
        /** Byte-identical to what's stored (same SHA-256): nothing written. */
        UNCHANGED
    }
}
