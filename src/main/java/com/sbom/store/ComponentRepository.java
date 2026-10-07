package com.sbom.store;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Spring Data generates the implementation; queries are derived from method names.
 * To add a query, just declare a method, e.g. {@code findByPurlStartingWith(String prefix, Sort sort)}.
 *
 * Matching is case-insensitive because the columns are (see IGNORECASE in application.properties),
 * so plain equality is used: it hits the indexes, whereas IgnoreCase variants (UPPER(x) = UPPER(?)) can't.
 */
public interface ComponentRepository extends JpaRepository<ComponentEntity, Long> {

    List<ComponentEntity> findByName(String name, Sort sort);

    List<ComponentEntity> findByNameAndVersion(String name, String version, Sort sort);

    /** Inner join so the database can start from the license index (a derived query would LEFT JOIN and scan). */
    @Query("select c from ComponentEntity c join c.licenses l where l = :license")
    List<ComponentEntity> findByLicense(@Param("license") String license, Sort sort);
}
