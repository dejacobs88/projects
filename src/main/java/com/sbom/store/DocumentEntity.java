package com.sbom.store;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** One ingested SBOM. Deleting it cascades to its components. */
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

    private Instant ingestedAt;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ComponentEntity> components = new ArrayList<>();

    protected DocumentEntity() {
        // for JPA
    }

    public DocumentEntity(String name, String serialNumber, String sourceFile) {
        this.name = name;
        this.serialNumber = serialNumber;
        this.sourceFile = sourceFile;
        this.ingestedAt = Instant.now();
    }

    public void addComponent(ComponentEntity component) {
        component.setDocument(this);
        components.add(component);
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getSerialNumber() { return serialNumber; }
    public String getSourceFile() { return sourceFile; }
    public Instant getIngestedAt() { return ingestedAt; }
    public List<ComponentEntity> getComponents() { return components; }
}
