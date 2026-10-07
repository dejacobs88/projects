package com.sbom.store;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Spring Data generates the implementation; queries are derived from method names. */
public interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {

    Optional<DocumentEntity> findBySerialNumber(String serialNumber);
}
