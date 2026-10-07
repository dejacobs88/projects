package com.sbom.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * All JSON handling in one place: one shared, preconfigured ObjectMapper and a few one-liners.
 * Jackson stays inside this class; callers work with plain {@code Map<String, Object>} or typed records.
 * Unknown fields are ignored, so typed models only declare the fields we care about.
 */
public final class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private JsonUtil() {
    }

    /**
     * Parses a JSON object into a Map (useful for sniffing the format before binding).
     * Nested objects are Maps, arrays are Lists, numbers are Integer/Long/Double.
     */
    public static Map<String, Object> readMap(String json) {
        try {
            return MAPPER.readValue(json, MAP_TYPE);
        } catch (MismatchedInputException e) {
            throw new IllegalArgumentException("Expected a JSON object at the top level", e);
        } catch (JsonProcessingException e) {
            int line = e.getLocation() == null ? -1 : e.getLocation().getLineNr();
            throw new IllegalArgumentException("Invalid JSON (line " + line + "): " + e.getOriginalMessage(), e);
        }
    }

    /** Reads a file containing a JSON object into a Map. */
    public static Map<String, Object> readMap(Path file) {
        try {
            return readMap(Files.readString(file));
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read " + file + ": " + e.getMessage(), e);
        }
    }

    /** Reads a file straight into a typed object. */
    public static <T> T read(Path file, Class<T> type) {
        return convert(readMap(file), type);
    }

    /** Binds an already-parsed Map to a typed object (e.g. a record mirroring the JSON structure). */
    public static <T> T convert(Map<String, Object> map, Class<T> type) {
        try {
            return MAPPER.convertValue(map, type);
        } catch (IllegalArgumentException e) {
            String detail = e.getMessage();
            if (e.getCause() instanceof JsonMappingException jme) {
                String field = jme.getPath().stream()
                        .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                        .collect(Collectors.joining("."));
                detail = "field '" + field + "': " + jme.getOriginalMessage();
            }
            throw new IllegalArgumentException("JSON does not match " + type.getSimpleName() + ", " + detail, e);
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
