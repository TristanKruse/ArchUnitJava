# ArchUnitJava — Architecture Testing for Java

<div align="center" name="top">

[![CI](https://github.com/TristanKruse/ArchUnitJava/actions/workflows/ci.yml/badge.svg)](https://github.com/TristanKruse/ArchUnitJava/actions/workflows/ci.yml)
[![Documentation](https://github.com/TristanKruse/ArchUnitJava/actions/workflows/docs.yml/badge.svg)](https://tristankruse.github.io/ArchUnitJava/)
[![License: Apache-2.0](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](LICENSE)
[![Java 25](https://img.shields.io/badge/Java-25-E76F00?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/25/)
[![Status: staging ready](https://img.shields.io/badge/status-staging%20ready-2da44e)](docs/RELEASE.md)

</div>

Enforce architecture boundaries against compiled Java code. ArchUnitJava reads class directories
and JARs without loading target classes, builds an immutable dependency model, evaluates
deterministic policies, and produces evidence for JUnit and CI.

> **Release status:** the reviewed `0.1.0-SNAPSHOT` build is **GO for user-managed Maven Central
> staging**. Publishing remains a separate manual decision, and no artifact has been published. See
> the evidence and external publisher prerequisites in the
> [release assessment](docs/RELEASE.md).

_Inspired by the established [ArchUnit](https://www.archunit.org/) project, but independently
implemented and not affiliated with ArchUnit. This is not a drop-in replacement._

[Quickstart](#-five-minute-quickstart) · [Use cases](#-use-cases) ·
[Capabilities](#-capabilities) · [Reports](#-reports) ·
[Example repository](#-independent-example-repository) ·
[Documentation](https://tristankruse.github.io/ArchUnitJava/) ·
[Limitations](#-current-limitations)

## ⚡ Five-minute quickstart

### 1. Install the reviewed candidate

The artifact is not available from a public Maven repository yet. Build and install it locally:

```shell
git clone https://github.com/TristanKruse/ArchUnitJava.git
cd ArchUnitJava
./mvnw -Prelease-candidate install
```

On Windows, use `mvnw.cmd` instead of `mvnw`.

### 2. Add the test dependency

```xml
<dependency>
  <groupId>io.github.tristankruse</groupId>
  <artifactId>archunitjava</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <scope>test</scope>
</dependency>
```

Production application code does not need an ArchUnitJava dependency.

### 3. Define an architecture policy

Create `archunitjava.properties` in the analyzed project:

```properties
schema=archunitjava.cli.v1
inputs=target/classes
rules=api-boundary
emptySelection=fail
allowIncompleteAnalysis=false
resultFormat=json
graphFormat=mermaid
graphDomain=types

rule.api-boundary.domain=types
rule.api-boundary.mode=no
rule.api-boundary.origins=glob:com.example.api.**
rule.api-boundary.targets=glob:com.example.infrastructure.**
rule.api-boundary.self=ignore
rule.api-boundary.external=ignore
rule.api-boundary.displayName=API must not bypass the application layer
rule.api-boundary.rationale=Keep concrete adapters behind application ports
rule.api-boundary.tags=api,boundary
rule.api-boundary.severity=error
```

Rules use qualified Java binary names. CLI patterns are either `exact:` or bounded `glob:` values;
arbitrary regular expressions and executable configuration hooks are intentionally unsupported.

### 4. Run it as a normal JUnit test

```java
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.archunitjava.cli.CliExitCode;
import dev.archunitjava.cli.CliRunner;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ArchitectureTest {
    @Test
    void architecturePoliciesPass() {
        Path root = Path.of("").toAbsolutePath().normalize();
        StringBuilder output = new StringBuilder();
        StringBuilder error = new StringBuilder();

        int exit = new CliRunner().run(new String[] {
                "check",
                "--config", root.resolve("archunitjava.properties").toString(),
                "--root", root.toString(),
                "--result-format", "json"
        }, output, error);

        assertEquals(CliExitCode.SUCCESS.code(), exit, error.toString());
    }
}
```

Run it with the rest of the suite:

```shell
./mvnw test
```

This same workflow is exercised by the
[independent RAG consumer](https://github.com/TristanKruse/ArchUnitJava-TestRepo-RAG) on Linux and
Windows.

## 🧭 How it works

```text
compiled classes and JARs
          │
          ▼
      EXTRACT        Parse bytecode as untrusted data; never load target classes
          │
          ▼
      PROJECT        Build stable type, package, member, slice, layer, and module views
          │
          ▼
       ASSERT        Evaluate immutable architecture rules with explicit completeness
          │
          ▼
       REPORT        Preserve subjects, dependency kinds, bytecode locations, and diagnostics
```

Rule construction performs no I/O. Empty selections fail by default. A policy disagreement,
incomplete analysis, invalid configuration, and internal analysis failure are separate outcomes;
they are not collapsed into one generic exception or exit code.

## 🐹 Use cases

### Protect dependency directions

Express boundaries such as:

- API code must not depend directly on persistence adapters.
- Domain code must remain independent of infrastructure frameworks.
- Application services may depend only on approved domain ports.
- One JPMS module must not read or export another module.

The properties-driven CLI currently exposes dependency policies over types and packages. The Java
API additionally contains rules for members, inheritance, annotations, cycles, slices, layers,
modules, public interfaces, reachability, and exhaustive policies.

### Detect cycles and accidental coupling

The graph model retains concrete origin and target identifiers plus evidence such as the dependency
kind, owning member, bytecode offset, source file, and line number when available. Cycle and slice
rules therefore report why a boundary failed instead of returning only a boolean.

### Run architecture policies in JUnit

`ArchitectureAssertions.assertPasses(...)` translates structured results into test-runner failures.
`ArchitectureTestCases` supports dynamic tests, and the optional JUnit Platform engine discovers
explicit `@ArchitectureTest` methods without scanning or loading unrelated target classes.

### Review architecture in CI

The CLI has four commands:

| Command | Purpose |
| --- | --- |
| `validate-config` | Validate the bounded configuration and approved paths |
| `explain` | Show the normalized rule definitions without analyzing bytecode |
| `check` | Analyze inputs and evaluate all configured policies |
| `graph` | Render a type or package dependency graph |

Stable exit codes distinguish success (`0`), usage errors (`2`), invalid configuration (`3`),
analysis failure (`4`), and policy violations (`5`).

## 🧰 Capabilities

| Area | Implemented surface |
| --- | --- |
| Inputs | Class directories, JARs, classpaths, multi-release JARs, Maven/Gradle project discovery |
| Java model | Types, records, sealed types, nested types, packages, members, generics, annotations, inheritance, JPMS |
| Dependencies | Declarations, signatures, annotations, exceptions, calls, fields, constants, method handles, dynamic constants, lambdas, method references |
| Selectors | Types, packages, members, semantic properties, glob/exact matching, composition |
| Rules | Dependencies, names, locations, inheritance, annotations, member access, cycles, slices, layers, modules, dead types, public interfaces, presets |
| Test integration | Framework-neutral assertions, JUnit Jupiter usage, dynamic cases, JUnit Platform engine |
| Build integration | CLI configuration plus Maven and Gradle invocation bridges |
| Architecture artifacts | Graph snapshots, PlantUML contracts, reviewed baselines, result exports |
| Metrics | Source, cohesion, and dependency metrics with deterministic snapshots |
| Operational controls | Bounded diagnostics, import filters, `.archignore`, cache keys, resource and path limits |

The [generated API reference](https://tristankruse.github.io/ArchUnitJava/api/) lists every public
package and type. The architecture and ownership rules are described in
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## 📊 Reports

Rule results can be rendered as:

- console text;
- canonical JSON;
- SARIF for code-scanning systems; or
- JUnit XML for CI test reporting.

Dependency graphs can be rendered as:

- DOT;
- Mermaid;
- D2;
- CSV;
- canonical JSON; or
- self-contained HTML.

For example, switch a configured graph to Mermaid without changing the policy file:

```java
int exit = new CliRunner().run(new String[] {
        "graph",
        "--config", configuration.toString(),
        "--root", approvedRoot.toString(),
        "--graph-format", "mermaid"
}, output, error);
```

Renderers escape untrusted labels and impose deterministic order and size limits. CSV is
spreadsheet-safe by default. The explicitly named `renderMachineReadable` Java API preserves values
without formula neutralization and must be treated as data rather than opened directly in a
spreadsheet. See the [threat model](docs/THREAT_MODEL.md).

## 🧪 Independent example repository

The
[ArchUnitJava RAG test repository](https://github.com/TristanKruse/ArchUnitJava-TestRepo-RAG)
is a normal, independently versioned Maven consumer. It models a retrieval-augmented-generation
application with API, application, domain, infrastructure, and bootstrap packages.

Its five tests prove:

| Contract | Expected outcome |
| --- | --- |
| RAG application behavior | Pass |
| Domain and application isolation | Pass |
| API directly reaches an infrastructure adapter | Detected as a policy violation |
| Infrastructure imports an API DTO | Detected as a policy violation |
| Mermaid graph and strict configuration | Generated and validated |

The two bad dependencies are deliberate fixture data. Tests assert their structured failures, so
the overall Maven build remains green. Both repositories verify the integration on Ubuntu and
Windows using GitHub's ordinary read-only public checkout; neither workflow stores an access token.

The smaller [`examples/basic`](examples/basic) consumer remains in this repository as a fast,
mandatory release-candidate smoke test.

## 🔐 Security model

ArchUnitJava treats target repositories and bytecode as untrusted data:

- target classes are never loaded, initialized, or reflected over;
- target builds, plugins, and annotation processors are never executed by analysis;
- configuration cannot name executable factories or commands;
- approved roots contain configuration, input, cache, diagram, and output paths;
- archive traversal and decompression have explicit limits;
- parser and resolution diagnostics are bounded; and
- HTML, graph, JSON, XML, and SARIF output escapes target-controlled text.

Static bytecode analysis cannot see dependencies introduced only through reflection, native code,
runtime generation, service lookup, dependency injection configuration, or dynamically assembled
strings. The lower-level `JavaPattern.regex` API accepts a bounded safe subset; unrestricted JDK
regular expressions require the explicitly named `trustedRegex` method and trusted in-process
policy. See [`docs/THREAT_MODEL.md`](docs/THREAT_MODEL.md) for the full boundary and residual risks.

## 🧱 Requirements and compatibility

- JDK 25 is required to build and run the current library.
- Maven 3.9.x is supplied through the checked-in wrapper.
- `javac` bytecode from Java 8 through Java 25 (class-file majors 52–69) is covered by the extraction
  corpus.
- Later bytecode is unsupported until explicitly tested.
- Older or non-`javac` bytecode may parse, but is outside the release claim.
- The library JAR exposes the automatic module name `dev.archunitjava`; it is not yet a fully modular
  JAR.

Read the complete [compatibility contract](docs/COMPATIBILITY.md).

## 🛠️ Build and contribute

Linux and macOS:

```shell
./mvnw verify
```

Windows:

```powershell
.\mvnw.cmd verify
```

CI runs the full suite on Ubuntu and Windows with JDK 25. Separate jobs verify:

- the independent RAG consumer;
- reproducible primary artifacts;
- source and Javadoc JARs;
- package contents and automatic-module metadata;
- a reviewed SpotBugs baseline that rejects new finding categories and mutable representation
  exposure;
- Central bundle construction, signing configuration, and local-only deployment; and
- the generated GitHub Pages/Javadoc site.

Useful technical guides:

- [Architecture](docs/ARCHITECTURE.md)
- [Compatibility](docs/COMPATIBILITY.md)
- [Migration and staged adoption](docs/MIGRATION.md)
- [Performance methodology](docs/PERFORMANCE.md)
- [Release process and readiness](docs/RELEASE.md)
- [Threat model](docs/THREAT_MODEL.md)
- [Research and product decisions](docs/RESEARCH.md)

## ⚖️ Should I use this or ArchUnit?

The original [ArchUnit](https://www.archunit.org/) is mature, widely used, and is generally the
correct choice for production Java architecture testing today.

ArchUnitJava exists to explore a consistent ArchUnitEverything product family, a
bytecode-as-untrusted-data security boundary, explicit completeness, deterministic evidence, and
cross-language architecture-policy concepts. Evaluate this candidate when those goals are relevant
and you are comfortable with an unpublished, provisional pre-1.0 API. Do not migrate a production system on
the assumption of ArchUnit compatibility.

## 📅 Current limitations

- The code is ready for user-managed staging, but no package has been published.
- The CLI intentionally exposes only a subset of the lower-level Java rule surface.
- JDK 25 is currently required at runtime because the importer uses `java.lang.classfile`.
- Dynamic runtime dependencies are invisible to static bytecode analysis.
- API contracts remain provisional before 1.0 and are not compatible with ArchUnit's API.
- Performance data is regression evidence, not an absolute scalability claim.

These limitations are tracked in the [release assessment](docs/RELEASE.md) rather than hidden behind
a stability claim.

## 🌍 ArchUnitEverything family

- [ArchUnitRuby](https://github.com/LukasNiessen/ArchUnitRuby)
- [ArchUnitPython](https://github.com/LukasNiessen/ArchUnitPython)
- [ArchUnitTS](https://github.com/LukasNiessen/ArchUnitTS)

The implementations share a product idea, not source compatibility: architecture decisions should
be executable, evidence-rich, deterministic, and natural to run in each language's normal test
workflow.

## 📄 License

ArchUnitJava is licensed under the [Apache License 2.0](LICENSE).

<div align="right">

[Back to the top](#top)

</div>
