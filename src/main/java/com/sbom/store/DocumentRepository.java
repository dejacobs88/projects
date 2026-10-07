package com.sbom.store;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Spring Data generates the implementation; queries are derived from method names. */
public interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {

    Optional<DocumentEntity> findBySerialNumber(String serialNumber);

    /** Saves the document, replacing any existing one with the same serial number (call inside a transaction). */
    default DocumentEntity replace(DocumentEntity document) {
        findBySerialNumber(document.getSerialNumber()).ifPresent(existing -> {
            delete(existing);
            flush(); // free the unique serial number before re-inserting
        });
        return save(document);
    }
}
