package com.sbom.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Where a document came from: its path, the exact raw content, and a SHA-256 of that content. */
public record SbomSource(String path, String content, String sha256) {

    public static SbomSource of(String path, byte[] bytes) {
        return new SbomSource(path, new String(bytes, StandardCharsets.UTF_8), sha256(bytes));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available in the JDK", e);
        }
    }
}
