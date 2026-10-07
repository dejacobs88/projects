# sbom-cli

A small Java CLI that ingests CycloneDX 1.6 JSON SBOMs into a local SQLite database (`./sbom.db`) and queries them by component name (optionally filtered by version) or by license. Requires Java 25+ and Maven. The `./sbom-cli` launcher builds the jar on first run (`mvn package`) and then forwards your arguments to it. Names and licenses match case-insensitively. Re-ingesting the same SBOM (same `serialNumber`, or the same file path when there is none) replaces the old copy instead of duplicating it.

The code is layered as **CLI (`Main`) → `SbomService` (facade) → `SbomParser` + `SbomRepository`**. Parsers turn a specific format into a common `SbomDocument` model, so adding SPDX means writing one new `SbomParser`. The repository hides storage, so SQLite can be swapped for Postgres without touching the CLI. `SbomService` is the single entry point that any new feature should build on.

```bash
./sbom-cli ingest samples/*.json
./sbom-cli query --component log4j-core
./sbom-cli query --component log4j-core --version 2.14.1
./sbom-cli query --license MIT

mvn test                      # run the tests
SBOM_DB=/tmp/other.db ./sbom-cli query --license MIT   # use a different database file
```

Example output:
```
DOCUMENT          COMPONENT   VERSION  LICENSES
payments-service  log4j-core  2.14.1   Apache-2.0
web-frontend      log4j-core  2.17.1   Apache-2.0
```
