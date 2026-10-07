package com.sbom.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.sbom.model.Component;
import com.sbom.model.SbomDocument;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Parses CycloneDX JSON (1.4–1.6). Only the fields we query on are extracted. */
@org.springframework.stereotype.Component
public class CycloneDxParser implements SbomParser {

    @Override
    public boolean supports(JsonNode root) {
        return "CycloneDX".equals(root.path("bomFormat").asText());
    }

    @Override
    public SbomDocument parse(JsonNode root, String sourceFile) {
        String name = text(root.path("metadata").path("component"), "name");
        if (name == null) {
            name = Path.of(sourceFile).getFileName().toString();
        }
        // serialNumber identifies a document; fall back to the file path so re-ingest stays idempotent.
        String serial = text(root, "serialNumber");
        if (serial == null) {
            serial = "file:" + Path.of(sourceFile).toAbsolutePath();
        }

        List<Component> components = new ArrayList<>();
        collect(root.path("components"), components);
        return new SbomDocument(name, serial, sourceFile, components);
    }

    /** Components can nest (assemblies), so walk the tree and flatten. */
    private void collect(JsonNode nodes, List<Component> out) {
        for (JsonNode node : nodes) {
            out.add(new Component(
                    text(node, "name"),
                    text(node, "version"),
                    text(node, "purl"),
                    licenses(node.path("licenses"))));
            collect(node.path("components"), out);
        }
    }

    /** Each entry is either {"license": {"id"|"name": ...}} or {"expression": "..."}. */
    private List<String> licenses(JsonNode nodes) {
        List<String> out = new ArrayList<>();
        for (JsonNode entry : nodes) {
            String value = entry.has("expression")
                    ? text(entry, "expression")
                    : firstNonNull(text(entry.path("license"), "id"), text(entry.path("license"), "name"));
            if (value != null) {
                out.add(value);
            }
        }
        return out;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }
}
