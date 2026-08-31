# ADR 0002: Keep JDK 25 for the first release

Status: accepted for `0.1.0`

## Context

The class-file backend decision left the public runtime floor provisional. The implementation and
Java 8–25 extraction corpus now provide enough evidence to make a release decision. Release
hardening found and subsequently fixed downstream `package-info.class` graph identities and added a
bounded persisted-baseline reader.

## Decision

Keep JDK 25 as both the build and runtime requirement for the first release candidate. Support
verified `javac` class files from Java 8 through 25 without loading them. Ship an automatic-module
name rather than a library module descriptor. Permit user-managed `0.1.0` staging only after the
source-level release gate in `docs/RELEASE.md` passes; publication remains a separate human action.

## Consequences

- The candidate stays dependency-light and uses the final standard class-file API.
- Java 17/21 consumers cannot run it yet, even when their analyzed bytecode is supported.
- JPMS consumers get a stable provisional automatic module name, not strong encapsulation.
- A backend or runtime-floor change requires its own corpus, performance, threat-model, and API
  compatibility review.
- Release readiness is evidence-based: packaging success does not override semantic or security
  blockers.
