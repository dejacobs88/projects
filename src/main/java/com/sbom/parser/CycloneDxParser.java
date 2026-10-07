package com.sbom.parser;

import com.sbom.model.Component;
import com.sbom.model.SbomDocument;
import com.sbom.model.SbomSource;
import com.sbom.parser.CycloneDxBom.CdxComponent;
import com.sbom.parser.CycloneDxBom.LicenseChoice;
import com.sbom.util.JsonUtil;
import com.sbom.util.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Parses CycloneDX JSON (1.4–1.6): binds it to {@link CycloneDxBom}, then maps to the common model. */
@org.springframework.stereotype.Component
public class CycloneDxParser implements SbomParser {

    private final Logger log;

    public CycloneDxParser(Logger log) {
        this.log = log;
    }

    @Override
    public boolean supports(Map<String, Object> root) {
        return "CycloneDX".equals(root.get("bomFormat"));
    }

    @Override
    public SbomDocument parse(Map<String, Object> root, SbomSource source) {
        CycloneDxBom bom = JsonUtil.convert(root, CycloneDxBom.class);

        String name;
        if (bom.metadata() != null && bom.metadata().component() != null) {
            name = bom.metadata().component().name();
        } else {
            name = Path.of(source.path()).getFileName().toString();
            log.info("No metadata.component; naming document after the file: %s", name);
        }
        // serialNumber identifies a document; fall back to the file path so re-ingest stays idempotent.
        String serial;
        if (bom.serialNumber() != null) {
            serial = bom.serialNumber();
        } else {
            serial = "file:" + Path.of(source.path()).toAbsolutePath();
            log.info("No serialNumber; identifying document by file path");
        }

        List<Component> components = new ArrayList<>();
        flatten(bom.components(), components);
        log.info("Parsed '%s': %d component(s)", name, components.size());
        return new SbomDocument(name, serial, source, components);
    }

    /** Nested components are flattened so every package is queryable. */
    private void flatten(List<CdxComponent> cdxComponents, List<Component> out) {
        for (CdxComponent c : cdxComponents) {
            if (c.name() == null) {
                log.warn("Skipping component with no name (purl=%s)", c.purl());
            } else {
                List<String> licenses = c.licenses().stream()
                        .map(LicenseChoice::value)
                        .filter(Objects::nonNull)
                        .toList();
                out.add(new Component(c.name(), c.version(), c.purl(), licenses));
            }
            flatten(c.components(), out);
        }
    }
}
