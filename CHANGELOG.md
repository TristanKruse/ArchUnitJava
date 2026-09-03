# Changelog

All notable changes will be documented here. The project follows Keep a Changelog structure and
uses semantic versioning for public releases.

## [Unreleased]

### Documentation

- identify the published line consistently as a public beta;
- add a feature-oriented user guide, complete CLI configuration reference, support policy, issue
  forms, pull-request checklist, and code of conduct; and
- introduce the Java orange-and-blue AU family logo plus a dedicated GitHub social preview.

## [0.1.0] - 2026-09-01

### Added

- deterministic class-file import and immutable Java architecture models;
- selectors, graph projections, architecture rules, presets, metrics, and reviewed baseline values;
- CLI, JUnit Platform, Maven/Gradle bridge, and multi-format reporting integrations;
- bounded importer/cache/rendering security controls and an adversarial corpus;
- pinned open-source performance baselines and semantic regression snapshots;
- reproducible release-candidate packaging, source/Javadoc artifacts, and a Maven consumer example;
- strict bounded baseline JSON ingestion, spreadsheet-safe CSV, safe/trusted regex separation, and
  complete `package-info.class` graph identities;
- Maven Central-ready coordinates, metadata, signing policy, bundle generation, and tag-gated
  user-managed staging workflow.

### Fixed

- defensive handling for file-system roots whose `Path.getFileName()` is absent;
- explicit cache-output stream ownership and pre-construction validation for execution errors; and
- defensive copies at the class-file parser backend seam.

### Known limitations

- JDK 25 is required to run the library.
- The public API is provisional before 1.0 and is not ArchUnit-compatible.
- The performance corpus is regression evidence, not an absolute scalability claim.
- Unrestricted regex and lossless non-neutralized CSV require explicitly named trusted APIs.

[Unreleased]: https://github.com/TristanKruse/ArchUnitJava/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/TristanKruse/ArchUnitJava/releases/tag/v0.1.0
