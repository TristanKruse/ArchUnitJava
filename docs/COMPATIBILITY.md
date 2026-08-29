# Compatibility policy

## Runtime and build JDK

The `0.1.0-SNAPSHOT` candidate requires JDK 25. The implementation uses the final
`java.lang.classfile` API and the Maven build enforces `[25,26)`. Java 17 or 21 runtime support would
require a different bytecode backend or a multi-release implementation and is not claimed.

## Analyzed bytecode

Checked-in extraction snapshots cover `javac` output for Java 8 through Java 25, class-file major
versions 52 through 69. Records, sealed types, modules, nesting, generics, annotations, compiler
artifacts, method handles, dynamic constants, and `invokedynamic` have dedicated fixtures. Future
class-file versions are unsupported until the parser and corpus are updated. Older class versions,
alternate compilers, post-processors, and obfuscators may work but are outside the verified claim.

Malformed or semantically incomplete inputs produce explicit diagnostics. The library does not load
target classes to fill gaps. Missing external types remain explicit and rule policy decides whether
to ignore, fail, or treat them as non-matching.

## JPMS

ArchUnitJava models `module-info.class`, requires, exports, opens, uses, provides, automatic/unnamed
placement, readability, and observed cross-module dependencies. This is static evidence, not a
replacement for JVM resolution. Runtime layers, command-line `--add-reads`/`--add-exports`, agents,
and reflective access can differ from the analyzed descriptors.

The library artifact contains no `module-info.class`. Its manifest declares
`Automatic-Module-Name: dev.archunitjava`, giving JPMS consumers a stable provisional module name.
JUnit Platform integration is an optional dependency and a service-provider entry is packaged.

## API and ecosystem compatibility

Before 1.0, public API compatibility is provisional and breaking changes may occur between minor
development candidates. Release notes must identify them. ArchUnitJava does not claim compatibility
with the original ArchUnit API or its rule semantics. Configuration and report formats carry their
own schema versions and reject unknown schemas where ingestion exists.

## Decision

The first candidate retains JDK 25 instead of adding a third-party parser solely to lower the
runtime floor. That keeps the dependency surface small and the extraction behavior anchored to the
JDK API. The decision should be reconsidered only with usage data and a separately tested backend;
it is not a promise that a future 1.0 will require JDK 25.
