package com.sbom.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * All JSON handling in one place: one shared, preconfigured ObjectMapper and a few one-liners.
 * Unknown fields are ignored, so typed models only declare the fields we care about.
 */
public final class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private JsonUtil() {
    }

    /** Parses a JSON string into a generic tree (useful for sniffing the format before binding). */
    public static JsonNode readTree(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            int line = e.getLocation() == null ? -1 : e.getLocation().getLineNr();
            throw new IllegalArgumentException("Invalid JSON (line " + line + "): " + e.getOriginalMessage(), e);
        }
    }

    /** Reads a file into a generic JSON tree. */
    public static JsonNode readTree(Path file) {
        try {
            return readTree(Files.readString(file));
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read " + file + ": " + e.getMessage(), e);
        }
    }

    /** Reads a file straight into a typed object. */
    public static <T> T read(Path file, Class<T> type) {
        return convert(readTree(file), type);
    }

    /** Binds an already-parsed JSON tree to a typed object. */
    public static <T> T convert(JsonNode node, Class<T> type) {
        try {
            return MAPPER.treeToValue(node, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON does not match " + type.getSimpleName() + ": " + e.getOriginalMessage(), e);
        }
    }

    /** Pretty-printed JSON for any object (e.g. a future --json output flag). */
    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize " + value.getClass().getSimpleName(), e);
        }
    }
}
