package com.sbom.parser;

import java.util.List;

/**
 * The subset of the CycloneDX JSON structure we use. Jackson binds JSON onto these records directly;
 * any other fields in the file are ignored. Null lists are normalized to empty.
 */
public record CycloneDxBom(String bomFormat, String serialNumber, Metadata metadata, List<CdxComponent> components) {

    public CycloneDxBom {
        components = components == null ? List.of() : components;
    }

    /** lifecycles (CycloneDX 1.5+) says which stage the SBOM describes: design, pre-build, build, post-build, operations, ... */
    public record Metadata(CdxComponent component, List<Lifecycle> lifecycles) {
        public Metadata {
            lifecycles = lifecycles == null ? List.of() : lifecycles;
        }
    }

    /** Either a standard {"phase": "build"} or a custom {"name": "..."}. */
    public record Lifecycle(String phase, String name) {
        public String value() {
            return phase != null ? phase : name;
        }
    }

    /** Components can nest (e.g. a framework bundling its own libraries). */
    public record CdxComponent(String name, String version, String purl,
                               List<LicenseChoice> licenses, List<CdxComponent> components) {
        public CdxComponent {
            licenses = licenses == null ? List.of() : licenses;
            components = components == null ? List.of() : components;
        }
    }

    /** Either {"license": {"id" | "name"}} or {"expression": "MIT OR Apache-2.0"}. */
    public record LicenseChoice(License license, String expression) {
        public String value() {
            if (expression != null) {
                return expression;
            }
            if (license == null) {
                return null;
            }
            return license.id() != null ? license.id() : license.name();
        }
    }

    public record License(String id, String name) {}
}
