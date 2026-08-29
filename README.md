# ArchUnitJava

Static architecture analysis and executable architecture policies for compiled Java code.

ArchUnitJava is the Java member of the ArchUnitEverything family, alongside
[ArchUnitRuby](https://github.com/LukasNiessen/ArchUnitRuby),
[ArchUnitPython](https://github.com/LukasNiessen/ArchUnitPython), and
[ArchUnitTS](https://github.com/LukasNiessen/ArchUnitTS). It imports class directories and JARs
without loading target classes, builds an immutable dependency model, evaluates architecture rules,
and renders evidence for tests and CI.

> **Status:** `0.1.0-SNAPSHOT` is a private, reviewable development candidate. The current release
> decision is **no-go**: persisted baseline ingestion and `package-info.class` handling in some
> downstream APIs remain blockers. No artifact has been published. See
> [release readiness](docs/RELEASE.md).

## Capabilities

- class, member, package, slice, layer, classpath, and JPMS module models;
- dependencies from declarations, signatures, annotations, exceptions, bytecode calls, fields,
  constants, method handles, dynamic constants, lambdas, and method references;
- dependency, naming, location, inheritance, annotation, member-access, cycle, public-interface,
  reachability, layer, slice, module, and architecture-preset policies;
- JUnit assertions and a JUnit Platform test engine;
- a bounded properties-driven CLI plus Maven and Gradle invocation bridges;
- DOT, Mermaid, D2, JSON, CSV, HTML, SARIF, JUnit XML, and console reports;
- reviewed baseline values, graph snapshots, source/cohesion/dependency metrics, and deterministic
  performance snapshots.

The pipeline is `EXTRACT -> PROJECT -> ASSERT -> REPORT`. Rules are immutable values and rule
construction performs no I/O. Empty selections fail by default, and incomplete analysis remains
distinct from policy violations.

## Requirements and compatibility

- JDK 25 to build and run the library;
- Maven 3.9.x, supplied by the checked-in wrapper;
- analyzed `javac` bytecode from Java 8 through Java 25 (class-file majors 52–69) is covered by the
  extraction corpus.

Later bytecode is unsupported until tested. Older or non-`javac` bytecode may parse, but is not part
of the release claim. JPMS descriptors are analyzed as data; the library JAR itself currently uses
the automatic module name `dev.archunitjava`. Full details are in
[compatibility](docs/COMPATIBILITY.md).

## Build

Linux and macOS:

```shell
./mvnw verify
```

Windows:

```powershell
.\mvnw.cmd verify
```

CI runs the test suite on Windows and Linux with JDK 25. A separate job verifies reproducible JARs,
source and Javadoc artifacts, package contents, the external Maven example, signing configuration,
and a local-file-only deployment dry run.

## Try the development candidate

The artifact is not in a public repository. Install it locally first:

```shell
./mvnw -Prelease-candidate install
./mvnw -f examples/basic/pom.xml test
```

Then a Maven consumer can use:

```xml
<dependency>
  <groupId>dev.archunitjava</groupId>
  <artifactId>archunitjava</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

The basic example checks compiled application classes through a bounded configuration file:

```properties
schema=archunitjava.cli.v1
inputs=target/classes
rules=service-boundary
rule.service-boundary.domain=types
rule.service-boundary.mode=only
rule.service-boundary.origins=glob:com.example.service.**
rule.service-boundary.targets=glob:com.example.api.**
rule.service-boundary.external=ignore
```

Java callers can load the same configuration with `CliConfigurationLoader`, analyze it with
`CliAnalyzer`, or invoke `CliRunner`. The lower-level importer, selectors, projection plans, rules,
metrics, snapshots, and renderers are public APIs. The CLI supports `check`, `graph`, `explain`, and
`validate-config`; run `ArchUnitJavaCli help` for formats and stable exit codes.

## Security model and blind spots

ArchUnitJava parses target bytes and never executes target builds, plugins, annotation processors,
classes, static initializers, or `invokedynamic` bootstrap methods. Input traversal, parser
diagnostics, caches, diagrams, and HTML output have explicit containment and resource limits.

Static analysis cannot see dependencies introduced only through reflection, native code, runtime
generation, or dynamically assembled strings. Regex selectors are trusted policy without an
evaluation budget, and CSV output is not neutralized for spreadsheet formulas. Read the complete
[threat model](docs/THREAT_MODEL.md) before analyzing untrusted repositories.

## Adoption and reports

Start with a narrow configuration, keep incomplete analysis fatal, review structured evidence, then
expand rules. Baseline domain values support explicit freeze/compare/update operations, but the
current candidate does not yet read persisted baseline JSON; that blocks a complete migration
workflow. See [migration](docs/MIGRATION.md), [performance](docs/PERFORMANCE.md), and the
[architecture](docs/ARCHITECTURE.md).

## Relationship to ArchUnit

The original [ArchUnit](https://www.archunit.org/) is the established Java architecture-testing
library. ArchUnitJava is an independent implementation in the ArchUnitEverything family. It neither
copies ArchUnit source nor claims source, binary, or behavioral drop-in compatibility.

Licensed under the [Apache License 2.0](LICENSE).
