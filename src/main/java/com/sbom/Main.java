package com.sbom;

import com.sbom.model.QueryResult;
import com.sbom.model.SbomDocument;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** CLI layer: start Spring, parse args, call SbomService, print results. No business logic lives here. */
@SpringBootApplication
public class Main {

    private static final String USAGE = """
            Usage:
              sbom-cli ingest <sbom-file> [<sbom-file> ...]
              sbom-cli query --component <name> [--version <version>]
              sbom-cli query --license <license>

            Database: ./sbom.mv.db (override the path, without extension, with the SBOM_DB env var)""";

    public static void main(String[] args) {
        if (args.length == 0) {
            exit(USAGE);
        }
        // CLI args are ours, not Spring's, so don't pass them to SpringApplication.
        try (ConfigurableApplicationContext spring = SpringApplication.run(Main.class)) {
            SbomService sboms = spring.getBean(SbomService.class);
            switch (args[0]) {
                case "ingest" -> ingest(sboms, args);
                case "query" -> query(sboms, args);
                default -> exit("Unknown command: " + args[0] + "\n\n" + USAGE);
            }
        } catch (Exception e) {
            exit("Error: " + e.getMessage());
        }
    }

    private static void ingest(SbomService sboms, String[] args) throws Exception {
        if (args.length < 2) {
            exit(USAGE);
        }
        for (int i = 1; i < args.length; i++) {
            SbomDocument doc = sboms.ingest(Path.of(args[i]));
            System.out.printf("Ingested '%s' (%d components) from %s%n",
                    doc.name(), doc.components().size(), args[i]);
        }
    }

    private static void query(SbomService sboms, String[] args) {
        Map<String, String> flags = parseFlags(args);
        String component = flags.get("--component");
        String license = flags.get("--license");

        List<QueryResult> results;
        if (component != null && license == null) {
            results = sboms.findByComponent(component, flags.get("--version"));
        } else if (license != null && component == null && !flags.containsKey("--version")) {
            results = sboms.findByLicense(license);
        } else {
            exit("Specify exactly one of --component or --license.\n\n" + USAGE);
            return;
        }
        print(results);
    }

    /** Parses "--key value" pairs after the subcommand. */
    private static Map<String, String> parseFlags(String[] args) {
        Map<String, String> flags = new HashMap<>();
        for (int i = 1; i < args.length; i += 2) {
            if (!args[i].startsWith("--") || i + 1 >= args.length) {
                exit("Invalid arguments.\n\n" + USAGE);
            }
            flags.put(args[i], args[i + 1]);
        }
        return flags;
    }

    private static void print(List<QueryResult> results) {
        if (results.isEmpty()) {
            System.out.println("No matches.");
            return;
        }
        String[] header = {"DOCUMENT", "COMPONENT", "VERSION", "LICENSES"};
        int[] w = new int[header.length];
        for (int i = 0; i < header.length; i++) {
            w[i] = header[i].length();
        }
        for (QueryResult r : results) {
            String[] row = cells(r);
            for (int i = 0; i < row.length; i++) {
                w[i] = Math.max(w[i], row[i].length());
            }
        }
        String fmt = "%-" + w[0] + "s  %-" + w[1] + "s  %-" + w[2] + "s  %s%n";
        System.out.printf(fmt, (Object[]) header);
        for (QueryResult r : results) {
            System.out.printf(fmt, (Object[]) cells(r));
        }
        System.out.printf("%n%d match(es)%n", results.size());
    }

    private static String[] cells(QueryResult r) {
        return new String[] {
                r.documentName(), r.componentName(), orDash(r.version()), orDash(r.licenses())};
    }

    private static String orDash(String s) {
        return s == null ? "-" : s;
    }

    private static void exit(String message) {
        System.err.println(message);
        System.exit(1);
    }
}
