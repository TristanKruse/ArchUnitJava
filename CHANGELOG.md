# Changelog

All notable changes will be documented here. The project follows Keep a Changelog structure and
intends to use semantic versioning after its first supported public release.

## [Unreleased]

### Added

- deterministic class-file import and immutable Java architecture models;
- selectors, graph projections, architecture rules, presets, metrics, and reviewed baseline values;
- CLI, JUnit Platform, Maven/Gradle bridge, and multi-format reporting integrations;
- bounded importer/cache/rendering security controls and an adversarial corpus;
- pinned open-source performance baselines and semantic regression snapshots;
- reproducible release-candidate packaging, source/Javadoc artifacts, and a Maven consumer example.

### Known limitations

- JDK 25 is required to run the library.
- Persisted baseline JSON cannot yet be ingested.
- Some type-rule and component-metric paths reject imported `package-info.class` declarations.
- Regex evaluation and spreadsheet-formula neutralization need further hardening.

No public version has been released or published.
