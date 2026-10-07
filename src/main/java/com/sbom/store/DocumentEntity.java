package com.sbom.store;

import com.sbom.model.DocumentSummary;
import com.sbom.model.SbomDocument;
import jakarta.persistence.Basic;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One ingested SBOM. Keeps the exact raw JSON as the source of truth; the components table
 * is a query index derived from it. Deleting a document cascades to its components.
 */
@Entity
@Table(name = "documents")
public class DocumentEntity {

    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String serialNumber;

    private String sourceFile;

    /** SBOM type / lifecycle stage (e.g. "build"); null if not declared. */
    private String lifecycle;

    /** SHA-256 of the raw content: detects byte-identical re-ingests. */
    @Column(length = 64)
    private String sha256;

    /** The original SBOM, verbatim. Lazy so list/query never load it. */
    @Lob
    @Basic(fetch = FetchType.LAZY)
    private String rawJson;

    private Instant ingestedAt;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ComponentEntity> components = new ArrayList<>();

    protected DocumentEntity() {
        // for JPA
    }

    /** Maps the parsed, format-agnostic document onto entities ready to save. */
    public static DocumentEntity from(SbomDocument doc) {
        DocumentEntity entity = new DocumentEntity();
        entity.name = doc.name();
        entity.serialNumber = doc.serialNumber();
        entity.sourceFile = doc.source().path();
        entity.lifecycle = doc.lifecycle();
        entity.sha256 = doc.source().sha256();
        entity.rawJson = doc.source().content();
        entity.ingestedAt = Instant.now();
        doc.components().stream().map(ComponentEntity::from).forEach(entity::addComponent);
        return entity;
    }

    public DocumentSummary toSummary() {
        return new DocumentSummary(name, serialNumber, lifecycle, sourceFile, sha256, components.size(), ingestedAt);
    }

    public void addComponent(ComponentEntity component) {
        component.setDocument(this);
        components.add(component);
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getSerialNumber() { return serialNumber; }
    public String getSourceFile() { return sourceFile; }
    public String getLifecycle() { return lifecycle; }
    public String getSha256() { return sha256; }
    public String getRawJson() { return rawJson; }
    public Instant getIngestedAt() { return ingestedAt; }
    public List<ComponentEntity> getComponents() { return components; }
}
