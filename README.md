# sbom-cli

A small Java CLI that ingests CycloneDX 1.6 JSON SBOMs into a local embedded H2 database stored per user at `~/.sbom-cli/sbom.mv.db`, so every command sees the same data regardless of the current directory and queries them by component name (optionally filtered by version) or by license. Names and licenses match case-insensitively. Each document's original JSON is stored verbatim along with its SHA-256. The raw document is the source of truth, and the component tables are a query index built from it. Re-ingesting the same SBOM (same `serialNumber`, or the same file path when there is none) replaces the old copy if its content changed, and is skipped if it's byte-identical.

The code is layered as **CLI (`Main`) → `SbomService` (facade) → `SbomParser`s + Spring Data JPA repositories**. Parsers turn a specific format into a common `SbomDocument` model, so adding SPDX means writing one new `SbomParser` bean. Persistence uses Spring Data JPA (Hibernate): the schema is generated from the entity classes, and queries are derived from repository method names (e.g. `findByNameAndVersion`), so there is no hand-written SQL. Adding a query means declaring a method on `ComponentRepository`. Moving to Postgres is a driver and URL change. `SbomService` is the single entry point that any new feature should build on.

## Requirements

- Java 25+ (`java -version`, or set `JAVA_HOME`)

That's it. Maven doesn't need to be installed: `./mvnw` (the Maven Wrapper) downloads the right version on first run.

## Build

```bash
./mvnw package
```

This compiles the code, runs the tests, and produces one self-contained runnable jar at `target/sbom-cli.jar`, with all dependencies bundled. Use `./mvnw package -DskipTests` to skip the tests. Re-run it after any code change.

## Usage

`./sbom-cli` is a thin wrapper around `java -jar target/sbom-cli.jar` (it also runs the build if the jar doesn't exist yet). Either form works:

```bash
./sbom-cli ingest samples/*.json                           # one or many files; bad files are reported and skipped
./sbom-cli ingest samples/                                 # directories: every .json inside, recursively
./sbom-cli list                                            # every ingested SBOM with component count
./sbom-cli print legacy-service                            # stored raw JSON (-p / --print); name or serial number
./sbom-cli query --component log4j-core
./sbom-cli query --component log4j-core --version 2.14.1
./sbom-cli query --license MIT
./sbom-cli query -c log4j-core --version 2.14.1            # short forms: -c = --component, -l = --license

java -jar target/sbom-cli.jar query --license MIT          # same thing, without the wrapper
./sbom-cli -v ingest samples/*.json                        # -v / --verbose: log progress to stderr
./sbom-cli --help                                          # -h / --help: usage
SBOM_DB=/tmp/other ./sbom-cli query --license MIT          # use a different database file (/tmp/other.mv.db)
```

## Sample data

`samples/` contains:
- **`sbom-cli.cdx.json` and `legacy-service.cdx.json`**: real SBOMs generated with the official [CycloneDX Maven plugin](https://github.com/CycloneDX/cyclonedx-maven-plugin). One is for this project (Spring Boot 4). The other is for a legacy service on Spring Boot 3.2 with older pinned libraries, including the Log4Shell-vulnerable `log4j-core 2.14.1`. Together they have 131 components, many shared at different versions (`hibernate-core`, `spring-core`, `jackson-databind` and others), and a realistic license mix: Apache-2.0, MIT, BSD-3/4-Clause, EPL-1.0/2.0, LGPL-2.1, MPL-2.0, GPL-2.0-with-classpath-exception, plus non-SPDX names like `EPL 1.0`.
- **Small hand-written SBOMs** covering edge cases: nested components, multiple licenses on one component, a license `expression`, and a file with no `serialNumber` or `metadata.component`.

Example output:
```
DOCUMENT          COMPONENT   VERSION  LICENSES
payments-service  log4j-core  2.14.1   Apache-2.0
web-frontend      log4j-core  2.17.1   Apache-2.0

2 match(es)
```
