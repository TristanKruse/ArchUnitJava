# Dependency-ordered Java product backlog

The 69 issue numbers are stable identifiers, not a command to execute all work
serially. A task is runnable only when its dependencies, executable contract,
toolchain, and protected-path policy are ready.

| Issue | Capability | Phase | Depends on |
|---:|---|---|---|
| #1 | Foundation: bootstrap a reproducible JDK 25 Maven build | foundation | — |
| #2 | Foundation: deterministic Java dependency-graph kernel | foundation | #1 |
| #3 | Foundation: Java-aware glob, regex, and qualified-name patterns | foundation | #1 |
| #4 | Foundation: errors, options, and checkable execution contract | foundation | #1 |
| #5 | Foundation: typed violations and deterministic rule results | foundation | #2, #4 |
| #6 | Import: discover Maven, Gradle, and explicit Java project roots | import | #1 |
| #7 | Import: enumerate trusted class directories, JARs, and runtime images | import | #6 |
| #8 | Import: JDK Class-File API reader and parser boundary | import | #7 |
| #9 | Model: classes, interfaces, enums, annotations, and modifiers | model | #8 |
| #10 | Model: fields, methods, constructors, and static initializers | model | #9 |
| #11 | Model: JVM descriptors, primitives, arrays, and method types | model | #8, #9 |
| #12 | Model: source files, line numbers, and bytecode locations | model | #8 |
| #13 | Model: inheritance, interfaces, assignability, and hierarchy gaps | model | #9, #11 |
| #14 | Model: declaration, parameter, type-use, and default annotations | model | #9, #10, #11 |
| #15 | Model: generic signatures, type variables, and wildcards | model | #9, #10, #11 |
| #16 | Model: records, record components, and sealed hierarchies | model | #9, #10, #13, #14, #15 |
| #17 | Model: nested, local, anonymous types and nestmates | model | #9, #10, #13 |
| #18 | Model: packages, package-info annotations, and unnamed packages | model | #9, #14 |
| #19 | Extract: declaration and generic-signature dependencies | extract | #10, #11, #13, #14, #15, #16, #18 |
| #20 | Extract: method calls, constructor calls, and field accesses | extract | #10, #11, #12 |
| #21 | Extract: lambdas, method references, and invokedynamic evidence | extract | #8, #10, #11, #20 |
| #22 | Extract: declared, thrown, caught, and handler dependencies | extract | #10, #11, #12, #20 |
| #23 | Extract: class literals, method handles, method types, and dynamic constants | extract | #8, #10, #11 |
| #24 | Java modules: module-info, readability, exports, opens, uses, and provides | jpms | #7, #8, #9, #18 |
| #25 | Packaging: directory, JAR, classpath, and module-path precedence | packaging | #6, #7, #8 |
| #26 | Packaging: multi-release JAR and target-runtime selection | packaging | #25 |
| #27 | Packaging: duplicate, missing, unsupported, and external classes | packaging | #9, #25, #26 |
| #28 | Extract: bridge, synthetic, mandated, and generated artifacts | extract | #9, #10, #20 |
| #29 | Import: scopes, exclusions, and .archignore | import | #3, #6, #7 |
| #30 | Import: content-addressed, versioned, and safe analysis cache | import | #8, #25, #27, #29 |
| #31 | Import: compiler and bytecode extraction corpus | extract | #14, #16, #17, #19, #20, #21, #22, #23, #24, #26, #28 |
| #32 | Security: bound untrusted class files, archives, paths, and diagnostics | security | #7, #25, #26, #30 |
| #33 | Projection: immutable package, type, member, and module views | projection | #2, #19, #20, #24 |
| #34 | Projection: Tarjan components and bounded elementary cycles | projection | #2, #33 |
| #35 | API: immutable type and package selectors | api | #3, #9, #18, #33 |
| #36 | API: field, method, constructor, and code-unit selectors | api | #10, #35 |
| #37 | API: modifiers, annotations, hierarchy, records, and sealed selectors | api | #13, #14, #16, #17, #35, #36 |
| #38 | API: all/any/not composition and consistent exclusions | api | #3, #35, #36, #37 |
| #39 | Rules: immutable names, descriptions, rationale, and severity | rules | #5 |
| #40 | Rules: fail empty selections by default | rules | #5, #35 |
| #41 | Rules: forbid, require, and constrain type/package dependencies | rules | #5, #33, #35, #38, #40 |
| #42 | Rules: Java naming, package, source, and artifact location conventions | rules | #5, #12, #35, #36, #40 |
| #43 | Rules: inheritance, interface, and implementation conventions | rules | #5, #13, #16, #37, #40 |
| #44 | Rules: annotations, meta-annotations, retention, and placement | rules | #5, #14, #37, #40 |
| #45 | Rules: method, constructor, and field-access boundaries | rules | #5, #20, #36, #38, #40 |
| #46 | Rules: visibility and approved public-interface boundaries | rules | #5, #13, #18, #24, #37, #41 |
| #47 | Rules: cycle-free packages and types with bounded evidence | rules | #5, #34, #35, #40 |
| #48 | Slices: grouping, forbidden dependencies, and mutual independence | rules | #5, #33, #38, #40, #47 |
| #49 | Layers: named Java architecture layers and access policies | rules | #5, #33, #38, #40 |
| #50 | Java modules: readability, exports, opens, and service policies | rules | #5, #24, #35, #40 |
| #51 | Rules: unreachable packages and types from configured roots | rules | #5, #13, #20, #35, #40 |
| #52 | Rules: exhaustive architecture coverage and overlap checks | rules | #5, #35, #38, #40, #41 |
| #53 | Presets: clean, onion, hexagonal, and provider-SDK boundaries | rules | #41, #46, #48, #49, #50, #52 |
| #54 | Diagrams: bounded PlantUML component adherence and export | rules | #48, #49 |
| #55 | Reports: immutable graph snapshots and query options | reporting | #33, #38 |
| #56 | Reports: deterministic DOT and Mermaid renderers | reporting | #55 |
| #57 | Reports: JSON, CSV, D2, and self-contained HTML | reporting | #55 |
| #58 | Results: JSON, SARIF, JUnit XML, and console rendering | reporting | #5, #12, #39 |
| #59 | Adoption: reviewed baselines, freezing, and expiring suppressions | adoption | #5, #39, #58 |
| #60 | JUnit: Jupiter assertions and executable architecture tests | integration | #5, #39, #40, #41 |
| #61 | JUnit Platform: custom TestEngine, discovery, filtering, and cache | integration | #30, #40, #60 |
| #62 | CLI: validated configuration, checks, graphs, and explanations | integration | #41, #56, #58 |
| #63 | Build tools: Maven and Gradle integration without target-code execution | integration | #60, #62 |
| #64 | Metrics: Java counts, cohesion, and threshold rules | metrics | #9, #10, #12, #33, #35 |
| #65 | Metrics: coupling, instability, distance, and cumulative dependencies | metrics | #2, #33, #64 |
| #66 | Quality: enforce the library's own architecture with itself | quality | #41, #49, #60 |
| #67 | Performance: cold, warm, large-classpath, and report benchmarks | hardening | #30, #31, #55, #64, #65 |
| #68 | Security: adversarial importer, rule, cache, and renderer corpus | hardening | #32, #56, #57, #58, #59 |
| #69 | Release: documentation, compatibility decision, CI, and publishing dry run | release | #53, #54, #57, #58, #59, #61, #62, #63, #65, #66, #67, #68 |

## Why 69 issues

Ruby's completed 48-issue build supplies the reusable graph/rule/report spine.
Java adds independently risky bytecode and packaging semantics: descriptors,
generic signatures, annotations, records, sealed types, nests, synthetic/bridge
members, invokedynamic, method handles, JPMS, classpath precedence, and
multi-release JARs. Python/TypeScript gaps add rationale, ignore files, public
interfaces, exhaustive rules, presets, CLI, baselines, and machine reports.
The final tasks reserve evidence for performance, security, and release rather
than hiding them inside a generic 'polish' issue.
