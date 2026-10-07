package com.sbom.util;

/**
 * Minimal logging contract for the app. Messages are {@link String#format} patterns.
 * Inject it anywhere (it's a Spring bean); swap the implementation without touching callers.
 */
public interface Logger {

    /** Progress details; only shown with -v / --verbose. */
    void info(String format, Object... args);

    /** Something was skipped or degraded, but the command continues. */
    void warn(String format, Object... args);

    /** An operation failed. */
    void error(String format, Object... args);
}
