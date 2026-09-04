# ArchUnitJava user guide

ArchUnitJava `0.1.0` is a **public beta**. It is useful for evaluation and new architecture-test
suites, but its pre-1.0 Java API may change between minor versions. The established ArchUnit
library remains the mature default for production Java systems.

This guide maps each important feature to its normal workflow. The
[five-minute quickstart](../README.md#-five-minute-quickstart) remains the shortest path to a first
passing rule, while the [generated API reference](https://tristankruse.github.io/ArchUnitJava/api/)
documents every public package and type.

## Choose an integration path

| Need | Recommended entry point |
| --- | --- |
| One or more dependency boundaries in an application | Properties configuration plus `CliRunner` in an ordinary JUnit test |
| Custom selectors, structural rules, metrics, layers, slices, modules, or baselines | The lower-level Java API |
| Independent policy cases discovered by JUnit Platform | `@ArchitectureTest` and `ArchitectureTestEngine` |
| Machine-readable CI evidence | CLI result formats or the report renderer API |
| A dependency diagram | The `graph` command or a graph renderer |

The properties interface is intentionally smaller than the Java API. It is non-executable,
strictly validated, and currently supports dependency rules over type and package graphs. Do not
expect every lower-level Java rule to have a properties equivalent.

## Install and run a first rule

Add the Maven Central artifact as a test dependency:

```xml
<dependency>
  <groupId>io.github.tristankruse</groupId>
  <artifactId>archunitjava</artifactId>
  <version>0.1.0</version>
  <scope>test</scope>
</dependency>
```

Gradle users need `mavenCentral()` and
`testImplementation("io.github.tristankruse:archunitjava:0.1.0")`. The complete tested example is
in [`examples/basic`](../examples/basic), and a separate application consumes the public artifact
in the [RAG test repository](https://github.com/TristanKruse/ArchUnitJava-TestRepo-RAG).

Compile the target project before analysis. ArchUnitJava reads `target/classes`, other class
directories, and JARs; it does not compile the application itself. Copy the properties and JUnit
example from the [quickstart](../README.md#-five-minute-quickstart), then run the test suite normally.

## Importing compiled Java

The importer understands class directories, ordinary and multi-release JARs, classpaths, nested
types, records, sealed types, members, generics, annotations, inheritance, method bodies, lambdas,
method references, constants, and JPMS metadata. The current runtime requires JDK 25, while the
tested input corpus covers `javac` bytecode from Java 8 through Java 25.

Important behavior:

- imported code is data: target classes are never loaded or initialized;
- duplicate classes are resolved by explicit, stable classpath order;
- missing external types remain visible as incomplete analysis rather than being guessed;
- import filters and `.archignore` can remove intentional inputs; and
- approved roots and resource limits constrain paths, archives, diagnostics, and caches.

Start with the [`importer` API](https://tristankruse.github.io/ArchUnitJava/api/dev.archunitjava/dev/archunitjava/importer/package-summary.html)
for custom imports. Read the [compatibility contract](COMPATIBILITY.md) before claiming support for
another runtime, compiler, or class-file version.

## Selecting architecture subjects

Selectors operate over immutable imported models. Type, package, member, module, layer, and slice
selectors can be composed without performing I/O. Qualified binary names are the stable identity
for types; source-style names are presentation only.

The properties interface accepts only:

- `exact:com.example.api.Controller`; or
- bounded globs such as `glob:com.example.api.**`.

The Java API additionally provides semantic selectors and a deliberately named trusted-regex path.
Use [`TypeSelector`](https://tristankruse.github.io/ArchUnitJava/api/dev.archunitjava/dev/archunitjava/selector/TypeSelector.html)
and the surrounding `selector` package as the lower-level starting point. Empty selections fail by
default so a renamed or removed package cannot silently disable a policy.

## Rule families

Every rule returns structured `RuleResult` data with subjects, evidence, diagnostics, metadata, and
an explicit passed, failed, skipped, or incomplete status.

| Rule family | What it checks | Java API starting point |
| --- | --- | --- |
| Dependencies | Forbidden, allowed, optional, or required edges between selected types or packages | `DependencyRules`, `DependencyRuleSpec` |
| Naming and location | Qualified-name and bytecode-location conventions | `NamingRules`, `NamingRuleOptions` |
| Inheritance | Supertype and interface relationships | `InheritanceRules` |
| Annotations | Required/forbidden annotations and annotation values on types, members, parameters, or packages | `AnnotationRules` |
| Member access | Calls and field access between selected members or owners | `MemberAccessRules` |
| Cycles | Strongly connected components and bounded cycle evidence | `CycleRules` |
| Layers | Allowed dependencies and membership across named architectural layers | `LayerRules`, `LayerDefinitions` |
| Slices | Package-pattern slices, overlap policy, and slice dependencies | `SliceRules`, `SliceDefinitions` |
| Modules | JPMS readability, exports, opens, services, and module boundaries | `ModuleDescriptorRules` |
| Reachability | Direct or transitive dependency paths | `ReachabilityRules` |
| Coverage and public surface | Unassigned elements, unreachable types, public-interface rules, and exhaustive policies | `CoverageRules`, `ReachabilityRules`, `PublicInterfaceRules` |
| Diagrams and presets | PlantUML adherence and reusable policy sets | `PlantUmlRules`, the `presets` package |

These entry points are all under the
[`rules` API package](https://tristankruse.github.io/ArchUnitJava/api/dev.archunitjava/dev/archunitjava/rules/package-summary.html).
The generated Javadocs list the overloads and option values. Prefer a narrow selector and an
explicit external-dependency policy over broad patterns that happen to pass today.

## Layers, slices, modules, and PlantUML

Layers and slices are projections over the imported dependency graph. Define membership first,
choose explicit overlap/unmatched-element behavior, and then evaluate the corresponding rule.
Membership errors are configuration failures, not ordinary architecture violations.

JPMS rules use the imported `module-info.class` model. They distinguish readability, exports,
opens, uses, and provides relationships rather than reducing modules to packages.

PlantUML support parses a bounded component diagram subset. Use it when the diagram is the reviewed
contract; choose an explicit policy for types or dependencies that do not map to a diagram
component. See the
[`diagram.plantuml` API](https://tristankruse.github.io/ArchUnitJava/api/dev.archunitjava/dev/archunitjava/diagram/plantuml/package-summary.html).

## JUnit integration

Three levels are available:

1. Call `CliRunner` in an ordinary JUnit test, as in `examples/basic`. This is the recommended
   public-beta path because the configuration is small and the test controls approved paths.
2. Evaluate Java API rules and pass results to `ArchitectureAssertions.assertPasses(...)`.
3. Use explicit `@ArchitectureTest` methods with the optional `ArchitectureTestEngine` when separate
   discoverable architecture cases are useful.

Policy violations and analysis failures use different assertion errors. Do not turn incomplete
analysis into a passing test merely to keep CI green; either import the missing inputs or document
and configure the intended completeness policy.

## Reports and dependency graphs

Rule-result formats are console text, canonical JSON, SARIF, and JUnit XML. Graph formats are DOT,
Mermaid, D2, CSV, canonical JSON, and self-contained HTML. All renderers impose deterministic
ordering and size limits and escape target-controlled labels.

Use SARIF when findings should appear in a code-scanning interface, JUnit XML for generic test
reporting, canonical JSON for reviewed automation, and console output for local diagnosis. The CLI
can override configured result or graph formats without changing policy semantics.

CSV is spreadsheet-safe by default. The explicitly named `renderMachineReadable` API preserves
lossless values and must be handled as data rather than opened directly in a spreadsheet. Renderer
entry points are in the
[`report` API](https://tristankruse.github.io/ArchUnitJava/api/dev.archunitjava/dev/archunitjava/report/package-summary.html).

## Baselines and suppressions

Reviewed baselines let a team introduce architecture checks without accepting new debt. Freeze the
current structured findings, compare a later result, and fail on new or changed findings while
retaining review dates and suppression reasons. Baseline JSON is schema-versioned and read through
a bounded strict parser.

Treat a baseline change as an architecture decision: review it in the same pull request as the code
that changes the finding. Start with the
[`baseline` API](https://tristankruse.github.io/ArchUnitJava/api/dev.archunitjava/dev/archunitjava/baseline/package-summary.html)
and the staged workflow in [Migration and adoption](MIGRATION.md).

## Metrics and performance evidence

The metrics API covers source counts, cohesion, component composition, dependency metrics, and
threshold rules. Metric snapshots are deterministic, but they are signals rather than universal
quality targets. Choose limits from the repository's own history and review changes deliberately.

The performance harness protects against regressions on pinned corpora; it is not an absolute
throughput or heap-size claim. See [Performance methodology](PERFORMANCE.md).

## Maven and Gradle projects

The public beta ships a CLI/library contract and invocation bridges, not standalone Maven or Gradle
plugins. The normal supported workflow is to add the library as a test dependency and call it from
JUnit. If wrapping the bridge in a build plugin, run it after `compile` or `test-compile`, declare
compiled output directories explicitly, and never start a nested target build. The exact bridge
contracts are in [`build-integrations/maven`](../build-integrations/maven) and
[`build-integrations/gradle`](../build-integrations/gradle).

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Empty-selection failure | Confirm the selected binary names and that the intended class directory was compiled and imported |
| Incomplete analysis | Add the missing classpath entries or choose an explicit external-dependency policy |
| Invalid configuration | Run `validate-config`; unknown, duplicate, executable, or escaping values are rejected |
| Policy exit code `5` | Read the structured violations; this is an architecture disagreement, not an analysis crash |
| Analysis exit code `4` | Inspect diagnostics and completeness before changing the rule |
| Unsupported bytecode | Use JDK 25 to run and analyze `javac` class-file versions 52 through 69 only |
| A dynamic dependency is missing | Reflection, native code, runtime generation, service configuration, and assembled strings are outside static-bytecode visibility |

The exact configuration keys and CLI behavior are documented in the
[CLI configuration reference](CLI_REFERENCE.md). Security-sensitive limits and residual risks are
documented in the [threat model](THREAT_MODEL.md). For unresolved questions, use
[GitHub Discussions](https://github.com/TristanKruse/ArchUnitJava/discussions) or open an issue as
described in [SUPPORT.md](../SUPPORT.md).
