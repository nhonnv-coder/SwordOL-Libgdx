# Asset pipeline

Java 8-compatible headless tooling. Build:
    
    .tools/apache-maven-3.9.11/bin/mvn.cmd "-Dmaven.repo.local=.tools/m2" -pl asset-pipeline -am package

Commands:

    java -jar asset-pipeline/target/swordol-assets.jar validate assets/source/demo
    java -jar asset-pipeline/target/swordol-assets.jar build assets/source/demo demo.swb
    java -jar asset-pipeline/target/swordol-assets.jar usages assets/source/demo brand.logo
    java -jar asset-pipeline/target/swordol-assets.jar import-legacy EXPORT/manifest.json NEW_PROJECT
    java -jar asset-pipeline/target/swordol-assets.jar migrate PROJECT

A project contains registry.json with schemaVersion 1 and an assets array.
Each asset has id, type, source (slash-separated project-relative regular file),
dependencies (IDs), and provenance (object). Types: image, map, ui, sprite, font,
binary. These tags do not imply decoded content schemas.
IDs allow ASCII letters/digits/dot/underscore/hyphen, maximum 256 characters.
Dependencies must exist and be acyclic. usages lists direct reverse dependencies.

Builds sort assets and ZIP entries, deduplicate blobs at files/SHA256, use fixed
ZIP wall timestamps, and hash bytes actually written. bundle.json contains
schemaVersion 1 and id/type/path/sha256/size/dependencies/provenance per asset.
Publication requires an atomic file move; failures preserve previous output.
Output cannot overwrite registry.json or registered source files.

Limits: 10,000 assets, 4 MiB JSON, 16 MiB per blob, 64 MiB total decoded bundle
including manifest; shared source contents count once. JSON keys must be unique,
nesting is capped; source symlinks, traversal and case-colliding distinct paths
are rejected.

Legacy import validates exporter manifest members against size and SHA256,
then copies to ordinal paths in a new project. PNG members are image; all others
remain binary. Archive ordinal, member and source hashes and original paths
remain in provenance. Original exports are unchanged. Failed imports remove
their staging directory. Existing destination projects are refused.

Migration explicitly converts schema 0 path to schema 1 source. Validation runs
before writing registry.json.schema0.bak using CREATE_NEW; registry.json is then
atomically replaced. Existing backups are never overwritten. If atomic move
fails, the original registry and recovery backup remain. Loading never migrates.
