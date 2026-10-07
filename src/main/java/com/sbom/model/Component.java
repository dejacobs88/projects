package com.sbom.model;

import java.util.List;

/** A single package listed in an SBOM. Licenses are SPDX ids, names, or expressions. */
public record Component(String name, String version, String purl, List<String> licenses) {}
