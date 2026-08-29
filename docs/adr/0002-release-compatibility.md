# ADR 0002: Keep JDK 25 for the first candidate and defer public release

Status: accepted for `0.1.0-SNAPSHOT`

## Context

The class-file backend decision left the public runtime floor provisional. The implementation and
Java 8–25 extraction corpus now provide enough evidence to make a candidate decision. Release
hardening also found that downstream graph identities do not consistently handle
`package-info.class`, and persisted baseline JSON has no bounded reader.

## Decision

Keep JDK 25 as both the build and runtime requirement for the first development candidate. Support
verified `javac` class files from Java 8 through 25 without loading them. Ship an automatic-module
name rather than a library module descriptor. Do not publish `0.1.0` while the release blockers in
`docs/RELEASE.md` remain.

## Consequences

- The candidate stays dependency-light and uses the final standard class-file API.
- Java 17/21 consumers cannot run it yet, even when their analyzed bytecode is supported.
- JPMS consumers get a stable provisional automatic module name, not strong encapsulation.
- A backend or runtime-floor change requires its own corpus, performance, threat-model, and API
  compatibility review.
- Release readiness is evidence-based: packaging success does not override semantic or security
  blockers.
