# ADR 0001: Start with the JDK Class-File API

Status: provisional

## Context

Java architecture analysis can inspect source code, load classes through
reflection, or parse class files. Source analysis does not see the exact linked
JVM artifacts and reflection risks class loading or initialization. Bytecode is
the most honest foundation for deployed dependencies, but the parser choice
sets the minimum supported JDK.

The standard `java.lang.classfile` API has been final since JDK 24. On JDK 25 it
parses class files into lazy immutable models and follows the current class-file
format. ASM would support older runtimes, but would add a core dependency and a
second compatibility calendar.

## Decision

The proving repository starts on JDK 25 and uses `java.lang.classfile`. Target
classes are parsed as bytes and are never loaded. The domain model must not leak
JDK parser objects so a later backend can be substituted.

## Consequences

- The experiment can use the platform parser and carry no runtime parser
  dependency.
- Contributors and users need JDK 25 even when analyzing older class files.
- Malformed input may fail lazily while model accessors are traversed; the
  importer must bound, catch, and contextualize those failures.
- A public release must revisit whether Java 17 or 21 compatibility is more
  valuable than the standard API. This ADR does not prejudge that decision.

