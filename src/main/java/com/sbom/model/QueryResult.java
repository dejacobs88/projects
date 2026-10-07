package com.sbom.model;

/** One matching row: which document contains which component, and under what license(s). */
public record QueryResult(String documentName, String componentName, String version, String licenses) {}
