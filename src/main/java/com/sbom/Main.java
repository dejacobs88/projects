package com.sbom;

import com.sbom.model.DocumentSummary;
import com.sbom.model.QueryResult;
import com.sbom.model.SbomDocument;
import com.sbom.util.TablePrinter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;
import java.time.temporal.ChronoUnit;
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
              sbom-cli list

            Database: ./sbom.mv.db (override the path, without extension, with the SBOM_DB env var)""";

    public static void main(String[] args) {
        if (args.length == 0) {
            exit(USAGE);
        }
        int status;
        // CLI args are ours, not Spring's, so don't pass them to SpringApplication.
        try (ConfigurableApplicationContext spring = SpringApplication.run(Main.class)) {
            SbomService sboms = spring.getBean(SbomService.class);
            status = switch (args[0]) {
                case "ingest" -> ingest(sboms, args);
                case "query" -> query(sboms, args);
                case "list" -> list(sboms);
                default -> fail("Unknown command: " + args[0] + "\n\n" + USAGE);
            };
        } catch (Exception e) {
            status = fail("Error: " + e.getMessage());
        }
        System.exit(status);
    }

    /** Ingests each file independently: one bad file is reported and skipped, the rest still load. */
    private static int ingest(SbomService sboms, String[] args) {
        if (args.length < 2) {
            return fail(USAGE);
        }
        int failed = 0;
        for (int i = 1; i < args.length; i++) {
            try {
                SbomDocument doc = sboms.ingest(Path.of(args[i]));
                System.out.printf("Ingested '%s' (%d components) from %s%n",
                        doc.name(), doc.components().size(), args[i]);
            } catch (Exception e) {
                failed++;
                System.err.printf("Failed %s: %s%n", args[i], e.getMessage());
            }
        }
        int total = args.length - 1;
        if (total > 1) {
            System.out.printf("%nIngested %d of %d file(s)%n", total - failed, total);
        }
        return failed == 0 ? 0 : 1;
    }

    private static int query(SbomService sboms, String[] args) {
        Map<String, String> flags = parseFlags(args);
        String component = flags.get("--component");
        String license = flags.get("--license");

        List<QueryResult> results;
        if (component != null && license == null) {
            results = sboms.findByComponent(component, flags.get("--version"));
        } else if (license != null && component == null && !flags.containsKey("--version")) {
            results = sboms.findByLicense(license);
        } else {
            return fail("Specify exactly one of --component or --license.\n\n" + USAGE);
        }
        printTable(List.of("DOCUMENT", "COMPONENT", "VERSION", "LICENSES"),
                results.stream()
                        .map(r -> List.of(r.documentName(), r.componentName(), str(r.version()), str(r.licenses())))
                        .toList(),
                "match(es)");
        return 0;
    }

    private static int list(SbomService sboms) {
        List<DocumentSummary> docs = sboms.listDocuments();
        printTable(List.of("DOCUMENT", "COMPONENTS", "INGESTED", "SOURCE"),
                docs.stream()
                        .map(d -> List.of(d.name(), String.valueOf(d.componentCount()),
                                str(d.ingestedAt().truncatedTo(ChronoUnit.SECONDS)), str(d.sourceFile())))
                        .toList(),
                "document(s)");
        return 0;
    }

    /** Parses "--key value" pairs after the subcommand. */
    private static Map<String, String> parseFlags(String[] args) {
        Map<String, String> flags = new HashMap<>();
        for (int i = 1; i < args.length; i += 2) {
            if (!args[i].startsWith("--") || i + 1 >= args.length) {
                System.exit(fail("Invalid arguments.\n\n" + USAGE));
            }
            flags.put(args[i], args[i + 1]);
        }
        return flags;
    }

    private static void printTable(List<String> header, List<List<String>> rows, String noun) {
        if (rows.isEmpty()) {
            System.out.println("No results.");
            return;
        }
        TablePrinter.print(header, rows);
        System.out.printf("%n%d %s%n", rows.size(), noun);
    }

    /** List.of rejects nulls, so missing values are rendered as "-" here. */
    private static String str(Object value) {
        return value == null ? "-" : value.toString();
    }

    private static int fail(String message) {
        System.err.println(message);
        return 1;
    }

    private static void exit(String message) {
        System.exit(fail(message));
    }
}
