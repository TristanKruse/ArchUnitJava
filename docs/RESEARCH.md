# Java design research

## Product framing

Java already has the mature original ArchUnit. ArchUnitJava is an independent
Java member of the ArchUnitEverything family, not a claim that Java lacks an
architecture-testing library. The design aims for broad conceptual coverage
and strong engineering contracts without copying or claiming API compatibility.

## What is genuinely Java-specific

The Java compiler records dependencies across multiple class-file structures,
not in one import list. Correct extraction must cover descriptors, generic
`Signature` attributes, annotations (including invisible, parameter, and
type-use annotations), inheritance, exceptions, bytecode instructions,
bootstrap methods, method handles, dynamic constants, and `invokedynamic`.

The model must also preserve Java constructs with dedicated class-file
attributes: modules, nests, records, and permitted subclasses. Compiler-created
bridge and synthetic methods cannot simply be discarded because they can carry
real call evidence, but they should be distinguishable in selectors and
reports.

Classpath and packaging are product semantics. Directories, ordinary JARs,
modular JARs, automatic modules, duplicate classes, missing dependencies, and
multi-release JAR version selection all affect the graph. The importer must
model precedence deterministically without loading classes or running Maven or
Gradle builds.

## Deliberate scope choices

- Analyze compiled artifacts first; source parsing and compiler-equivalent
  resolution are not initial goals.
- Do not infer arbitrary reflective targets from strings.
- Do not execute builds to discover outputs; accept explicit inputs and inspect
  build metadata conservatively.
- Keep bytecode extraction independent from the public rule grammar.
- Treat JPMS readability/exports/opens as distinct from observed bytecode
  dependencies.
- Make incomplete debug information explicit rather than manufacturing line
  numbers or source paths.

## Primary references

- ArchUnit User Guide: https://www.archunit.org/userguide/html/000_Index.html
- Java SE 25 and JDK 25 specifications:
  https://docs.oracle.com/en/java/javase/25/docs/specs/index.html
- Java Class-File API:
  https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/classfile/package-summary.html
- Java Virtual Machine Specification, class-file format:
  https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html
- JAR File Specification:
  https://docs.oracle.com/en/java/javase/25/docs/specs/jar/jar.html
- Java module descriptor API:
  https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/module/ModuleDescriptor.html
- JUnit User Guide: https://docs.junit.org/current/user-guide/
- Maven Wrapper: https://maven.apache.org/tools/mavenwrapper.html
