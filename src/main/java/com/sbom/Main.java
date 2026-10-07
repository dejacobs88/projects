package com.sbom;

import com.sbom.model.DocumentSummary;
import com.sbom.model.IngestResult;
import com.sbom.model.QueryResult;
import com.sbom.model.SbomDocument;
import com.sbom.util.FileUtil;
import com.sbom.util.Logger;
import com.sbom.util.TablePrinter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** CLI layer: start Spring, parse args, call SbomService, print results. No business logic lives here. */
@SpringBootApplication
public class Main {

    private static final String USAGE = """
            Usage:
              sbom-cli ingest <file-or-directory> [...]    (directories: every .json inside, recursively)
              sbom-cli query -c|--component <name> [--version <version>]
              sbom-cli query -l|--license <license>
              sbom-cli list
              sbom-cli print <document-name-or-serial>     (also -p / --print; prints the stored raw JSON)

            Options:
              -v, --verbose   log progress details (to stderr)
              -h, --help      show this help

            Database: ~/.sbom-cli/sbom.mv.db (override the path, without extension, with the SBOM_DB env var)""";

    public static void main(String[] rawArgs) {
        // -v / --verbose may appear anywhere; strip it, then hand it to Spring as a property.
        List<String> argList = new ArrayList<>(List.of(rawArgs));
        boolean verbose = argList.removeIf(a -> a.equals("-v") || a.equals("--verbose"));
        String[] args = argList.toArray(String[]::new);
        // Help is answered before Spring starts, so it's instant and needs no database.
        if (argList.contains("-h") || argList.contains("--help") || (args.length > 0 && args[0].equals("help"))) {
            System.out.println(USAGE);
            System.exit(0);
        }
        if (args.length == 0) {
            exit(USAGE);
        }
        SpringApplication app = new SpringApplication(Main.class);
        app.setDefaultProperties(Map.of("sbom.verbose", String.valueOf(verbose)));

        int status;
        // CLI args are ours, not Spring's, so don't pass them to app.run().
        try (ConfigurableApplicationContext spring = app.run()) {
            SbomService sboms = spring.getBean(SbomService.class);
            Logger log = spring.getBean(Logger.class);
            status = switch (args[0]) {
                case "ingest" -> ingest(sboms, log, args);
                case "query" -> query(sboms, args);
                case "list" -> list(sboms);
                case "print", "-p", "--print" -> print(sboms, args);
                default -> fail("Unknown command: " + args[0] + "\n\n" + USAGE);
            };
        } catch (Exception e) {
            if (verbose) {
                e.printStackTrace();
            }
            status = fail("Error: " + e.getMessage());
        }
        System.exit(status);
    }

    /** Ingests each file independently: one bad file is reported and skipped, the rest still load. */
    private static int ingest(SbomService sboms, Logger log, String[] args) {
        if (args.length < 2) {
            return fail(USAGE);
        }
        List<Path> requested = Arrays.stream(args, 1, args.length).map(Path::of).toList();
        List<Path> files = FileUtil.expandJsonFiles(requested);
        if (files.isEmpty()) {
            log.warn("No .json files found in %s", requested);
            return 1;
        }
        log.info("Ingesting %d file(s)", files.size());

        int failed = 0;
        for (Path file : files) {
            try {
                IngestResult result = sboms.ingest(file);
                SbomDocument doc = result.document();
                String verb = switch (result.outcome()) {
                    case CREATED -> "Ingested";
                    case REPLACED -> "Replaced";
                    case UNCHANGED -> "Unchanged";
                };
                System.out.printf("%-9s '%s' (%d components) from %s%n",
                        verb, doc.name(), doc.components().size(), file);
            } catch (Exception e) {
                failed++;
                log.error("Failed %s: %s", file, e.getMessage());
            }
        }
        if (files.size() > 1) {
            System.out.printf("%n%d of %d file(s) succeeded%n", files.size() - failed, files.size());
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

    /** Prints a stored SBOM verbatim to stdout, so it can be piped (e.g. to jq) or saved to a file. */
    private static int print(SbomService sboms, String[] args) {
        if (args.length != 2) {
            return fail("Usage: sbom-cli print <document-name-or-serial>");
        }
        System.out.print(sboms.rawDocument(args[1]));
        return 0;
    }

    private static int list(SbomService sboms) {
        List<DocumentSummary> docs = sboms.listDocuments();
        printTable(List.of("DOCUMENT", "COMPONENTS", "SHA256", "INGESTED", "SOURCE"),
                docs.stream()
                        .map(d -> List.of(d.name(), String.valueOf(d.componentCount()),
                                d.sha256() == null ? "-" : d.sha256().substring(0, 12),
                                str(d.ingestedAt().truncatedTo(ChronoUnit.SECONDS)), str(d.sourceFile())))
                        .toList(),
                "document(s)");
        return 0;
    }

    /** Short flags and the long flag each one stands for. (-v is taken by --verbose.) */
    private static final Map<String, String> SHORT_FLAGS = Map.of(
            "-c", "--component",
            "-l", "--license");

    /** Parses "--key value" (or "-k value") pairs after the subcommand; short flags are stored under their long name. */
    private static Map<String, String> parseFlags(String[] args) {
        Map<String, String> flags = new HashMap<>();
        for (int i = 1; i < args.length; i += 2) {
            String flag = SHORT_FLAGS.getOrDefault(args[i], args[i]);
            if (!flag.startsWith("--") || i + 1 >= args.length) {
                System.exit(fail("Invalid arguments.\n\n" + USAGE));
            }
            flags.put(flag, args[i + 1]);
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
