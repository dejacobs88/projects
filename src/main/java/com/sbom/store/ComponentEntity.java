package com.sbom.store;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

/** One package inside a document. Licenses live in their own table (component_licenses). */
@Entity
@Table(name = "components", indexes = @Index(columnList = "name, version"))
public class ComponentEntity {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "document_id")
    private DocumentEntity document;

    @Column(nullable = false)
    private String name;

    private String version;

    @Column(length = 1024)
    private String purl;

    @ElementCollection
    @CollectionTable(
            name = "component_licenses",
            joinColumns = @JoinColumn(name = "component_id"),
            indexes = @Index(columnList = "license"))
    @Column(name = "license", nullable = false)
    private List<String> licenses = new ArrayList<>();

    protected ComponentEntity() {
        // for JPA
    }

    public ComponentEntity(String name, String version, String purl, List<String> licenses) {
        this.name = name;
        this.version = version;
        this.purl = purl;
        this.licenses = new ArrayList<>(licenses);
    }

    void setDocument(DocumentEntity document) { this.document = document; }

    public Long getId() { return id; }
    public DocumentEntity getDocument() { return document; }
    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getPurl() { return purl; }
    public List<String> getLicenses() { return licenses; }
}
