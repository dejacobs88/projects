package com.sbom.store;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data generates the implementation; queries are derived from method names.
 * To add a query, just declare a method, e.g. {@code findByPurlStartingWith(String prefix, Sort sort)}.
 */
public interface ComponentRepository extends JpaRepository<ComponentEntity, Long> {

    List<ComponentEntity> findByNameIgnoreCase(String name, Sort sort);

    List<ComponentEntity> findByNameIgnoreCaseAndVersion(String name, String version, Sort sort);

    List<ComponentEntity> findByLicensesIgnoreCase(String license, Sort sort);
}
