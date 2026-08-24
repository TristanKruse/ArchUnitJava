# ArchUnitJava

Architecture testing for Java.

ArchUnitJava is the Java member of the ArchUnitEverything family, alongside
[ArchUnitRuby](https://github.com/LukasNiessen/ArchUnitRuby),
[ArchUnitPython](https://github.com/LukasNiessen/ArchUnitPython), and
[ArchUnitTS](https://github.com/LukasNiessen/ArchUnitTS). It turns compiled Java
code into a dependency model and lets teams express architectural constraints
as ordinary JUnit tests.

> **Status:** under active development. The build and product roadmap are in
> place, but no architecture-analysis API or published artifact is available
> yet.

## Intended use

The public API will make architecture rules read like sentences. The following
illustrates the intended direction; it is not implemented yet:

```java
var rule = projectClasses()
        .inPackage("com.example.api..")
        .shouldNot()
        .dependOnClasses()
        .inPackage("com.example.persistence..");

assertPasses(rule);
```

The library is planned to cover:

- package, class, interface, annotation, record, sealed-type, and member rules;
- dependencies from declarations, generic signatures, bytecode instructions,
  annotations, exceptions, lambdas, and method references;
- cycle, layer, slice, public-interface, inheritance, annotation, and JPMS
  module policies;
- JUnit integration, command-line checks, graph reports, CI result formats,
  baselines, and architecture metrics.

## How it works

```text
CLASS DIRECTORIES / JARS / MODULES
                 |
                 v
              EXTRACT
                 |
                 v
              PROJECT
                 |
                 v
               ASSERT
                 |
                 v
               REPORT
```

ArchUnitJava analyzes compiled class files without loading or executing target
classes. The initial implementation targets JDK 25 and uses the standard
`java.lang.classfile` API. This keeps bytecode parsing on the Java platform,
while the library's own immutable model remains independent of that parser.

Java-specific concerns are product semantics rather than incidental parser
details. In particular, the implementation must account for:

- erased descriptors and optional generic `Signature` attributes;
- visible, invisible, parameter, type-use, package, and default annotations;
- bridge and synthetic members, nested types, anonymous types, and nestmates;
- records and permitted subclasses of sealed types;
- bytecode calls, field access, method handles, dynamic constants, and
  `invokedynamic`;
- classpath and module-path precedence, duplicate classes, external types,
  ordinary JARs, modular JARs, and multi-release JARs;
- JPMS readability, exports, opens, service uses, and service providers.

## Design principles

- The pipeline is `EXTRACT -> PROJECT -> ASSERT -> REPORT`.
- Rules are immutable values; constructing a rule performs no I/O.
- Projection and assertion are deterministic in-memory operations.
- Violations retain structured evidence. Renderers own formatting and escaping.
- Empty selections fail by default so misspelled scopes cannot silently pass.
- Analysis never executes target builds, classes, annotation processors,
  bootstrap methods, or static initializers.
- Unsupported or incomplete semantics are reported explicitly rather than
  guessed.

## Build

Requirements:

- JDK 25
- no separate Maven installation; the Maven Wrapper is included

On Linux or macOS:

```shell
./mvnw verify
```

On Windows:

```powershell
.\mvnw.cmd verify
```

CI verifies the build on Windows and Linux.

## Roadmap

The complete dependency-ordered product backlog is maintained in
[GitHub Issues](https://github.com/TristanKruse/ArchUnitJava/issues). The local
[backlog](docs/BACKLOG.md), [Java research](docs/RESEARCH.md), and
[bytecode-backend decision](docs/adr/0001-bytecode-backend.md) provide the
technical context behind those issues.

## Relationship to ArchUnit

The original [ArchUnit](https://www.archunit.org/) is the established Java
architecture-testing library. ArchUnitJava is an independent implementation in
the ArchUnitEverything family. The project does not copy ArchUnit's source code
or claim drop-in API compatibility.
