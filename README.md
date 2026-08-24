# ArchUnitJava Codex proving repository

This is a private, clean-room architecture-testing experiment for Java and a
proving ground for the ArchUnitDev2 coding workflow. It is inspired by the
ArchUnitEverything family and by the idea of testing architecture as ordinary
unit tests. It does not copy the upstream ArchUnit implementation and must not
be represented as the official ArchUnit project.

## Goal

Build a Java-native library that:

- imports compiled Java bytecode without loading or executing target classes;
- models packages, types, members, dependencies, and JPMS modules as immutable
  deterministic values;
- exposes readable architecture rules usable from JUnit;
- reports every violation with bytecode and source evidence where available;
- remains useful both issue-by-issue and through a guarded coding campaign.

The repository begins with 69 dependency-ordered issues. Each issue has a
human-readable specification under `docs/issues/` and a machine-readable task
specification under `.archunitdev/tasks/`. An issue is not campaign-ready until
a separate protected executable contract overlay exists. The issue list is the
product plan; generated files are review aids, not authority to weaken tests or
expand scope.

## Technical baseline

- JDK 25
- Java's standard `java.lang.classfile` API (provisional ADR)
- Maven Wrapper
- JUnit 6 for this repository's tests
- no runtime dependencies in the initial core

JDK 25 is an intentional proving-project choice. It gives the extractor the
standard Class-File API introduced in JDK 24, at the cost of not running on
older Java installations. That trade-off is recorded in
`docs/adr/0001-bytecode-backend.md` and must be reassessed before a public
release.

## Pipeline

```text
CLASS FILES / JARS
        |
        v
     EXTRACT  -> immutable Java model + dependency evidence
        |
        v
     PROJECT  -> packages, slices, layers, modules, members
        |
        v
      ASSERT  -> typed violations
        |
        v
      REPORT  -> JUnit, CLI, SARIF, JSON, diagrams
```

Target bytecode is treated as untrusted input. Analysis must never load target
classes, execute build scripts, initialize classes, or invoke target code.

## Repository map

- `src/main/java/dev/archunitjava/` — product code
- `src/test/java/dev/archunitjava/` — executable contracts
- `docs/RESEARCH.md` — Java and sibling-repository comparison
- `docs/BACKLOG.md` — dependency-ordered issue map
- `docs/issues/` — GitHub issue bodies
- `.archunitdev/tasks/` — machine-readable task specifications
- `.archunitdev/contracts/` — operator-authored executable overlays (created
  only when a task is ready to implement)
- `.archunitdev/profile.toml` — guarded build profile
- `scripts/generate_backlog_assets.py` — canonical backlog generator

## Build

```shell
./mvnw verify
```

On Windows:

```powershell
.\mvnw.cmd verify
```

The wrapper and a JDK 25 installation are required. CI is the portability
authority; local Java 8 installations are not sufficient.

## Current status

Repository and backlog scaffolding only. No architecture-analysis capability is
claimed yet.
