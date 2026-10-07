# sbom-cli

A small Java CLI that ingests CycloneDX 1.6 JSON SBOMs into a local embedded H2 database (`./sbom.mv.db`) and queries them by component name (optionally filtered by version) or by license. Names and licenses match case-insensitively. Re-ingesting the same SBOM (same `serialNumber`, or the same file path when there is none) replaces the old copy instead of duplicating it.

The code is layered as **CLI (`Main`) → `SbomService` (facade) → `SbomParser`s + Spring Data JPA repositories**. Parsers turn a specific format into a common `SbomDocument` model, so adding SPDX means writing one new `SbomParser` bean. Persistence uses Spring Data JPA (Hibernate): the schema is generated from the entity classes, and queries are derived from repository method names (e.g. `findByNameIgnoreCaseAndVersion`), so there is no hand-written SQL. Adding a query means declaring a method on `ComponentRepository`. Moving to Postgres is a driver and URL change. `SbomService` is the single entry point that any new feature should build on.

## Requirements

- Java 25+ (`java -version`)
- Maven 3.9+ (`mvn -v`)

## Build

```bash
mvn package
```

This compiles the code, runs the tests, and produces one self-contained runnable jar at `target/sbom-cli.jar`, with all dependencies bundled. Use `mvn package -DskipTests` to skip the tests. Re-run it after any code change.

## Usage

`./sbom-cli` is a thin wrapper around `java -jar target/sbom-cli.jar` (it also runs the build if the jar doesn't exist yet). Either form works:

```bash
./sbom-cli ingest samples/*.json
./sbom-cli query --component log4j-core
./sbom-cli query --component log4j-core --version 2.14.1
./sbom-cli query --license MIT

java -jar target/sbom-cli.jar query --license MIT          # same thing, without the wrapper
SBOM_DB=/tmp/other ./sbom-cli query --license MIT          # use a different database file (/tmp/other.mv.db)
```

Example output:
```
DOCUMENT          COMPONENT   VERSION  LICENSES
payments-service  log4j-core  2.14.1   Apache-2.0
web-frontend      log4j-core  2.17.1   Apache-2.0

2 match(es)
```
