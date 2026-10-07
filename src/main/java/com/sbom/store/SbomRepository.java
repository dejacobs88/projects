package com.sbom.store;

import com.sbom.model.QueryResult;
import com.sbom.model.SbomDocument;

import java.util.List;

/** Storage abstraction. SQLite today; Postgres (or anything else) is a drop-in replacement. */
public interface SbomRepository extends AutoCloseable {

    /** Saves a document. A document with the same serial number is replaced, not duplicated. */
    void save(SbomDocument document);

    /** Case-insensitive name match; {@code version} is optional (null = any version). */
    List<QueryResult> findByComponent(String name, String version);

    /** Case-insensitive exact match on a license id, name, or expression. */
    List<QueryResult> findByLicense(String license);

    @Override
    void close();
}
