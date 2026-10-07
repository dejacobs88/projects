package com.sbom.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.PrintStream;

/**
 * Logs to stderr so stdout stays clean for command output (e.g. piping query results).
 * info is only printed in verbose mode; warn and error are always printed.
 */
@Component
public class ConsoleLogger implements Logger {

    private final boolean verbose;
    private final PrintStream out = System.err;

    public ConsoleLogger(@Value("${sbom.verbose:false}") boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public void info(String format, Object... args) {
        if (verbose) {
            out.println("[INFO]  " + String.format(format, args));
        }
    }

    @Override
    public void warn(String format, Object... args) {
        out.println("[WARN]  " + String.format(format, args));
    }

    @Override
    public void error(String format, Object... args) {
        out.println("[ERROR] " + String.format(format, args));
    }
}
