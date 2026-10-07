package com.sbom.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.sbom.model.Component;
import com.sbom.model.SbomDocument;
import com.sbom.parser.CycloneDxBom.CdxComponent;
import com.sbom.parser.CycloneDxBom.LicenseChoice;
import com.sbom.util.JsonUtil;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Parses CycloneDX JSON (1.4–1.6): binds it to {@link CycloneDxBom}, then maps to the common model. */
@org.springframework.stereotype.Component
public class CycloneDxParser implements SbomParser {

    @Override
    public boolean supports(JsonNode root) {
        return "CycloneDX".equals(root.path("bomFormat").asText());
    }

    @Override
    public SbomDocument parse(JsonNode root, String sourceFile) {
        CycloneDxBom bom = JsonUtil.convert(root, CycloneDxBom.class);

        String name = bom.metadata() != null && bom.metadata().component() != null
                ? bom.metadata().component().name()
                : Path.of(sourceFile).getFileName().toString();
        // serialNumber identifies a document; fall back to the file path so re-ingest stays idempotent.
        String serial = bom.serialNumber() != null
                ? bom.serialNumber()
                : "file:" + Path.of(sourceFile).toAbsolutePath();

        List<Component> components = new ArrayList<>();
        flatten(bom.components(), components);
        return new SbomDocument(name, serial, sourceFile, components);
    }

    /** Nested components are flattened so every package is queryable. */
    private void flatten(List<CdxComponent> cdxComponents, List<Component> out) {
        for (CdxComponent c : cdxComponents) {
            List<String> licenses = c.licenses().stream()
                    .map(LicenseChoice::value)
                    .filter(Objects::nonNull)
                    .toList();
            out.add(new Component(c.name(), c.version(), c.purl(), licenses));
            flatten(c.components(), out);
        }
    }
}
