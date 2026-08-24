from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


@dataclass(frozen=True)
class Item:
    number: int
    slug: str
    title: str
    objective: str
    dependencies: tuple[int, ...]
    criteria: tuple[str, ...]
    allowed_paths: tuple[str, ...]
    phase: str
    kind: str = "feature"
    non_goals: tuple[str, ...] = ()
    initially_completed: bool = False
    contract_ready: bool = False

    @property
    def task_id(self) -> str:
        return f"java-{self.number:03d}-{self.slug}"

    @property
    def task_name(self) -> str:
        return f"{self.number:03d}-{self.slug}.json"


def item(
    number: int,
    slug: str,
    title: str,
    objective: str,
    dependencies: tuple[int, ...],
    criteria: tuple[str, ...],
    allowed_paths: tuple[str, ...],
    phase: str,
    *,
    kind: str = "feature",
    non_goals: tuple[str, ...] = (),
    initially_completed: bool = False,
    contract_ready: bool = False,
) -> Item:
    return Item(
        number,
        slug,
        title,
        objective,
        dependencies,
        criteria,
        allowed_paths,
        phase,
        kind,
        non_goals,
        initially_completed,
        contract_ready,
    )


MAIN = ("src/main/java/dev/archunitjava/**", "src/test/java/dev/archunitjava/**")
IMPORT = ("src/main/java/dev/archunitjava/importer/**", "src/test/java/dev/archunitjava/importer/**")
RULES = ("src/main/java/dev/archunitjava/rules/**", "src/test/java/dev/archunitjava/rules/**")
REPORT = ("src/main/java/dev/archunitjava/report/**", "src/test/java/dev/archunitjava/report/**")


ITEMS = (
    item(1, "build-baseline", "Foundation: bootstrap a reproducible JDK 25 Maven build",
         "Establish the smallest cross-platform build that compiles, tests, and packages the initial ArchUnitJava project reproducibly.", (),
         ("The Maven Wrapper is pinned with checksum verification and works on Windows and Linux.", "JDK and Maven versions are enforced and JUnit runs the smoke contract.", "CI verifies the initial project without publishing artifacts."),
         ("pom.xml", "mvnw", "mvnw.cmd", ".mvn/**", ".github/workflows/ci.yml", "src/main/java/dev/archunitjava/ArchUnitJava.java", "src/test/java/dev/archunitjava/ArchUnitJavaTest.java"), "foundation", kind="infrastructure", initially_completed=True),
    item(2, "graph-kernel", "Foundation: deterministic Java dependency-graph kernel",
         "Implement stable identifiers, dependency kinds, evidence, nodes, edges, and an immutable deterministic graph.", (1,),
         ("Package, type, member, module, and location identifiers reject invalid or ambiguous values.", "Parallel edges merge evidence without duplicates and all iteration is stable.", "Graph construction rejects unknown endpoints and preserves isolated nodes."), MAIN, "foundation", contract_ready=True),
    item(3, "patterns", "Foundation: Java-aware glob, regex, and qualified-name patterns",
         "Compile user patterns once and match paths, packages, binary names, and source-style type names with explicit semantics.", (1,),
         ("Path and qualified-name separators have distinct documented glob rules.", "Regex and exact patterns share immutable matcher descriptions.", "Malformed patterns fail as user errors before analysis starts."), MAIN, "foundation"),
    item(4, "execution-contract", "Foundation: errors, options, and checkable execution contract",
         "Define technical versus user errors, immutable check options, and the shared terminal contract used by every rule.", (1,),
         ("Rule failures are values rather than exceptions.", "Technical and user errors retain causes and actionable context.", "Options have deterministic defaults and can evolve without positional parameters."), MAIN, "foundation"),
    item(5, "results", "Foundation: typed violations and deterministic rule results",
         "Represent rule outcomes, subjects, evidence, severity, and diagnostics independently from rendering.", (2, 4),
         ("Violations contain structured data and stable identities, not preformatted prose.", "Equivalent results compare and iterate deterministically.", "Pass, fail, skipped, and incomplete-analysis states cannot be conflated."), MAIN, "foundation"),

    item(6, "project-discovery", "Import: discover Maven, Gradle, and explicit Java project roots",
         "Locate project roots and declared output layouts without executing Maven, Gradle, wrappers, or project code.", (1,),
         ("Explicit roots win and ancestor discovery has bounded deterministic precedence.", "Maven and Gradle metadata is inspected conservatively without running builds.", "Ambiguous multi-project layouts produce diagnostics rather than guessed roots."), IMPORT, "import"),
    item(7, "input-enumeration", "Import: enumerate trusted class directories, JARs, and runtime images",
         "Turn caller-approved locations into a bounded deterministic stream of class-file resources.", (6,),
         ("Directories, JARs, and optional JRT modules retain their origin and precedence.", "Traversal does not follow symlinks or escape approved roots by default.", "Unreadable, missing, duplicate, and unsupported inputs yield typed diagnostics."), IMPORT, "import"),
    item(8, "classfile-reader", "Import: JDK Class-File API reader and parser boundary",
         "Parse untrusted class bytes through `java.lang.classfile` behind a backend-neutral adapter.", (7,),
         ("No target class is loaded, linked, initialized, or reflected upon.", "Lazy parser failures are caught with resource and traversal context.", "Parser-specific model objects never escape the importer boundary."), IMPORT, "import", kind="research"),
    item(9, "type-model", "Model: classes, interfaces, enums, annotations, and modifiers",
         "Build immutable Java type descriptions with binary/source names, kind, ownership, flags, and origin.", (8,),
         ("Top-level type kinds and JVM access flags map without losing unsupported bits.", "Binary names remain canonical while readable names are derived.", "Malformed and unsupported class versions remain explicit diagnostics."), MAIN, "model"),
    item(10, "member-model", "Model: fields, methods, constructors, and static initializers",
         "Represent declared members and code units with stable JVM signatures and ownership.", (9,),
         ("Overloads and constructors have unambiguous identifiers.", "Static initializers and compiler-created members remain representable.", "Member ordering is independent of class-file encounter order."), MAIN, "model"),
    item(11, "jvm-types", "Model: JVM descriptors, primitives, arrays, and method types",
         "Decode field and method descriptors into a lossless immutable JVM type vocabulary.", (8, 9),
         ("Primitive, void, reference, and multidimensional array types round-trip.", "Method parameters and returns retain exact descriptor order.", "Invalid descriptors fail locally without partial model corruption."), MAIN, "model"),
    item(12, "locations", "Model: source files, line numbers, and bytecode locations",
         "Attach honest source and bytecode locations to types, members, and dependency evidence when metadata exists.", (8,),
         ("Missing debug metadata is represented as absent, never fabricated.", "Line tables map offsets deterministically and handle repeated ranges.", "Locations include class resource origin without leaking machine-specific absolute paths."), MAIN, "model"),
    item(13, "inheritance", "Model: inheritance, interfaces, assignability, and hierarchy gaps",
         "Resolve superclass and interface relationships across imported and external type stubs without class loading.", (9, 11),
         ("Direct and transitive hierarchy queries terminate in cycles and missing-class cases.", "Interface extension and class implementation remain distinguishable.", "Assignability reports unknown when evidence is incomplete instead of guessing."), MAIN, "model"),
    item(14, "annotations", "Model: declaration, parameter, type-use, and default annotations",
         "Extract visible and invisible annotations, nested values, defaults, targets, and retention evidence from class files.", (9, 10, 11),
         ("Class, member, parameter, record-component, and type-use locations remain distinguishable.", "Primitive, enum, class, annotation, and array element values are lossless.", "Meta-annotation traversal is bounded and explicit about missing annotation types."), MAIN, "model"),
    item(15, "generic-signatures", "Model: generic signatures, type variables, and wildcards",
         "Parse optional Signature attributes without confusing generic references with erased JVM descriptors.", (9, 10, 11),
         ("Parameterized types, bounds, wildcards, arrays, and nested owners are represented.", "Malformed or absent signatures fall back to erased types with diagnostics where appropriate.", "Rules can later choose erased, generic, or combined dependency evidence."), MAIN, "model", kind="research"),
    item(16, "records-sealed", "Model: records, record components, and sealed hierarchies",
         "Represent Record and PermittedSubclasses attributes as first-class Java semantics.", (9, 10, 13, 14, 15),
         ("Record components retain names, descriptors, signatures, and annotations.", "Permitted subclasses are distinct from observed direct subclasses.", "Malformed or incomplete sealed hierarchies remain queryable with diagnostics."), MAIN, "model"),
    item(17, "nested-types", "Model: nested, local, anonymous types and nestmates",
         "Reconstruct ownership evidence from InnerClasses, EnclosingMethod, NestHost, and NestMembers without inventing source nesting.", (9, 10, 13),
         ("Member, local, anonymous, and top-level types remain distinguishable when attributes permit.", "Nest host/member relationships are modeled separately from lexical ownership.", "Conflicting attributes produce bounded diagnostics and deterministic fallback."), MAIN, "model"),
    item(18, "packages", "Model: packages, package-info annotations, and unnamed packages",
         "Aggregate types into Java packages while preserving package-info metadata and origin boundaries.", (9, 14),
         ("Package identifiers distinguish named and unnamed packages.", "Package annotations and documentation-carrier classes are exposed without ordinary-type noise.", "Split packages across inputs retain every origin for later JPMS policy checks."), MAIN, "model"),

    item(19, "declaration-dependencies", "Extract: declaration and generic-signature dependencies",
         "Emit typed dependency evidence from super types, interfaces, fields, methods, records, annotations, and generic signatures.", (10, 11, 13, 14, 15, 16, 18),
         ("Every edge records its exact class-file source and owning declaration.", "Erased and generic-only dependencies remain distinguishable.", "Self, duplicate, primitive, array, and external-target handling is explicit."), IMPORT + MAIN, "extract"),
    item(20, "code-accesses", "Extract: method calls, constructor calls, and field accesses",
         "Walk Code attributes and emit member-level access evidence with caller, target owner, descriptor, and location.", (10, 11, 12),
         ("Invocation opcodes, constructor calls, field reads, and field writes have distinct kinds.", "Interface, virtual, special, and static dispatch evidence is preserved without pretending to resolve runtime dispatch.", "Malformed code and missing line data do not discard valid surrounding evidence."), IMPORT + MAIN, "extract"),
    item(21, "invokedynamic", "Extract: lambdas, method references, and invokedynamic evidence",
         "Interpret bounded bootstrap-method patterns to expose Java lambda and method-reference dependencies without compiler equivalence claims.", (8, 10, 11, 20),
         ("LambdaMetafactory targets retain implementation handle and functional-interface evidence.", "Unknown bootstrap methods remain generic invokedynamic evidence.", "String concatenation and unrelated dynamic call sites are not mislabeled as lambdas."), IMPORT + MAIN, "extract", kind="research"),
    item(22, "exceptions", "Extract: declared, thrown, caught, and handler dependencies",
         "Model exception-related dependencies from Exceptions attributes and bytecode exception tables.", (10, 11, 12, 20),
         ("Declared throws and caught handler types have distinct evidence kinds.", "Catch-all/finally handlers do not manufacture a Throwable target.", "Instruction-level throw operations are reported honestly without inferring runtime types."), IMPORT + MAIN, "extract"),
    item(23, "constants-handles", "Extract: class literals, method handles, method types, and dynamic constants",
         "Traverse constant-pool and bootstrap references needed for complete static dependency evidence.", (8, 10, 11),
         ("Class literals and descriptor-bearing constants emit bounded typed evidence.", "Method handles preserve reference kind and target member signature.", "Dynamic constants retain bootstrap provenance without executing bootstrap code."), IMPORT + MAIN, "extract"),
    item(24, "jpms-model", "Java modules: module-info, readability, exports, opens, uses, and provides",
         "Parse explicit JPMS descriptors and represent module directives separately from observed class dependencies.", (7, 8, 9, 18),
         ("Requires modifiers, qualified exports/opens, uses, and providers are lossless.", "Explicit, automatic, and unnamed module identities remain distinct.", "No runtime ModuleLayer is created and unresolved directives stay inspectable."), MAIN, "jpms"),
    item(25, "classpath", "Packaging: directory, JAR, classpath, and module-path precedence",
         "Assemble imported resources using explicit deterministic Java lookup precedence without loading classes.", (6, 7, 8),
         ("Each selected class records its winning input and shadowed alternatives.", "Classpath and module-path modes have separate documented rules.", "Manifest Class-Path entries are opt-in, bounded, and contained."), IMPORT, "packaging"),
    item(26, "multi-release-jars", "Packaging: multi-release JAR and target-runtime selection",
         "Select the correct root or META-INF/versions class resource for an explicit target Java release.", (25,),
         ("Only manifests with Multi-Release true enable version selection.", "The highest eligible version wins and ineligible/malformed directories are ignored with diagnostics.", "Versioned module-info handling follows the modular multi-release JAR rules."), IMPORT, "packaging"),
    item(27, "resolution-gaps", "Packaging: duplicate, missing, unsupported, and external classes",
         "Resolve references to imported types or deterministic external stubs while surfacing classpath gaps and conflicts.", (9, 25, 26),
         ("Missing targets never crash graph construction or masquerade as imported types.", "Duplicate definitions preserve precedence and conflict diagnostics.", "Unsupported class versions and damaged archives fail according to caller policy."), IMPORT + MAIN, "packaging"),
    item(28, "compiler-artifacts", "Extract: bridge, synthetic, mandated, and generated artifacts",
         "Preserve compiler-created artifacts as evidence while enabling explicit filtering and source-level presentation.", (9, 10, 20),
         ("Bridge and synthetic flags are retained on types, members, parameters, and accesses.", "Filtering an artifact does not silently remove the only evidence for a dependency.", "Generated-code heuristics are opt-in and never based on one annotation alone."), MAIN, "extract"),
    item(29, "import-options", "Import: scopes, exclusions, and .archignore",
         "Provide immutable import options and repository-relative ignore rules shared by directories and archives.", (3, 6, 7),
         ("Include/exclude precedence and negation are documented and deterministic.", "Ignore files cannot include commands, environment expansion, or paths outside the root.", "Diagnostics explain which rule excluded a resource."), IMPORT + ("src/main/java/dev/archunitjava/pattern/**", "src/test/java/dev/archunitjava/pattern/**"), "import"),
    item(30, "cache", "Import: content-addressed, versioned, and safe analysis cache",
         "Cache imported models by inputs, target release, options, and schema without trusting timestamps alone.", (8, 25, 27, 29),
         ("Cache keys cover parser/library versions and byte content or validated fingerprints.", "Corrupt, foreign, partial, and concurrent entries fail closed and self-heal.", "Cache serialization cannot instantiate arbitrary classes or escape its directory."), IMPORT, "import"),
    item(31, "extraction-corpus", "Import: compiler and bytecode extraction corpus",
         "Create a generated fixture corpus that locks down Java 8 through 25 class-file constructs and toolchain variations.", (14, 16, 17, 19, 20, 21, 22, 23, 24, 26, 28),
         ("Fixtures cover javac output, hand-built malformed class files, modules, records, sealed types, annotations, and lambdas.", "Expected models and edges are reviewed deterministic snapshots.", "Corpus generation is separate from product tests so product analysis never runs target builds."), ("src/test/**", "test-fixtures/**", "scripts/**"), "extract", kind="research"),
    item(32, "input-security", "Security: bound untrusted class files, archives, paths, and diagnostics",
         "Define and enforce resource limits and containment at every importer boundary.", (7, 25, 26, 30),
         ("Archive entry count, compression ratio, bytes, nesting, class size, and diagnostic volume have configurable bounds.", "ZIP slip, symlink, malformed UTF-8, hostile names, and cache poisoning fixtures fail closed.", "The threat model distinguishes trusted configuration from untrusted target artifacts."), IMPORT + ("docs/THREAT_MODEL.md", "src/test/**"), "security", kind="research"),

    item(33, "projection", "Projection: immutable package, type, member, and module views",
         "Relabel and aggregate raw dependency evidence without losing contributing edges or isolated nodes.", (2, 19, 20, 24),
         ("Projection functions are pure values and can filter, relabel, or drop edges.", "Parallel projected edges retain stable complete evidence sets.", "Package, type, member, classpath, and module projections share one deterministic contract."), MAIN, "projection"),
    item(34, "cycle-algorithms", "Projection: Tarjan components and bounded elementary cycles",
         "Implement deterministic strongly connected components and optional representative cycle enumeration.", (2, 33),
         ("Self loops, disconnected nodes, parallel evidence, and stable ordering have fixtures.", "Default diagnostics remain bounded on dense graphs.", "Cycle computation is pure and independent from rule wording."), MAIN, "projection"),
    item(35, "type-package-selectors", "API: immutable type and package selectors",
         "Select imported Java types and packages by name, location, kind, and package patterns.", (3, 9, 18, 33),
         ("Selectors are immutable, reusable, and carry readable descriptions.", "Binary, canonical, simple, package, and path matching are not conflated.", "Selection order is stable and incomplete imports remain diagnosable."), MAIN, "api"),
    item(36, "member-selectors", "API: field, method, constructor, and code-unit selectors",
         "Select members by owner, name, descriptor, parameters, return type, and declaring context.", (10, 35),
         ("Overloaded members are selectable without ambiguous source rendering.", "Constructors and static initializers have explicit vocabulary.", "Member selectors compose with containing type and package selectors."), MAIN, "api"),
    item(37, "semantic-selectors", "API: modifiers, annotations, hierarchy, records, and sealed selectors",
         "Expose Java semantic predicates over types and members while retaining unknown hierarchy states.", (13, 14, 16, 17, 35, 36),
         ("Visibility, static/final/abstract/synthetic, annotation, assignability, record, and sealed predicates compose.", "Direct, meta, inherited, and type-use annotation matching are explicit choices.", "Missing external hierarchy evidence follows caller-selected unknown policy."), MAIN, "api"),
    item(38, "selector-composition", "API: all/any/not composition and consistent exclusions",
         "Compose selectors and exclusions without mutating reusable base scopes or changing semantics between modules.", (3, 35, 36, 37),
         ("Boolean composition has typed grouping and readable deterministic descriptions.", "Exclusions apply consistently to rules, reports, metrics, layers, and slices.", "Empty and universal selectors cannot be confused accidentally."), MAIN, "api"),
    item(39, "rule-metadata", "Rules: immutable names, descriptions, rationale, and severity",
         "Attach `as`, `because`, tags, and severity to every rule as stable result metadata.", (5,),
         ("Metadata changes return new rule values and leave reusable builders untouched.", "Rationale appears in human and machine reports.", "Stable rule identities distinguish semantic changes from presentation changes."), RULES + MAIN, "rules"),
    item(40, "empty-selection", "Rules: fail empty selections by default",
         "Prevent misspelled or stale selectors from silently passing any architecture rule.", (5, 35),
         ("Every terminal uses one shared empty-selection policy.", "Check options can deliberately allow, warn, or fail empty selections.", "Diagnostics show the selector and practical remediation."), RULES + MAIN, "rules"),

    item(41, "dependency-rules", "Rules: forbid, require, and constrain type/package dependencies",
         "Implement positive and negative dependency policies over selected Java types and packages.", (5, 33, 35, 38, 40),
         ("Rules support no, only, any, and required dependencies with explicit self/external policy.", "Every violation carries representative declaration or code evidence.", "Positive and negated moods share one assertion implementation."), RULES + MAIN, "rules"),
    item(42, "naming-location-rules", "Rules: Java naming, package, source, and artifact location conventions",
         "Enforce names and locations for selected packages, types, and members.", (5, 12, 35, 36, 40),
         ("Simple, binary, package, source-file, class-resource, and artifact locations are distinct targets.", "Positive and negative pattern rules return all violations deterministically.", "Anonymous/local/generated subjects require explicit inclusion."), RULES + MAIN, "rules"),
    item(43, "inheritance-rules", "Rules: inheritance, interface, and implementation conventions",
         "Constrain extending, implementing, assignability, and sealed-hierarchy relationships.", (5, 13, 16, 37, 40),
         ("Direct and transitive relationships are explicit.", "Unknown external ancestors cannot silently pass strict rules.", "Violations show the relevant hierarchy path or permitted-subclass evidence."), RULES + MAIN, "rules"),
    item(44, "annotation-rules", "Rules: annotations, meta-annotations, retention, and placement",
         "Require, forbid, or constrain Java annotations on types, members, parameters, packages, and type uses.", (5, 14, 37, 40),
         ("Direct, meta, inherited, visible, and invisible matching modes are explicit.", "Annotation value predicates are typed and deterministic.", "Placement violations retain exact declaration or type-use location."), RULES + MAIN, "rules"),
    item(45, "member-access-rules", "Rules: method, constructor, and field-access boundaries",
         "Constrain member-level calls and accesses between selected callers and targets.", (5, 20, 36, 38, 40),
         ("Call, constructor, read, and write policies can be selected independently.", "Violations identify caller code unit, target signature, opcode kind, and location.", "Static analysis does not claim runtime dispatch resolution."), RULES + MAIN, "rules"),
    item(46, "public-interface", "Rules: visibility and approved public-interface boundaries",
         "Require consumers to cross package or module boundaries only through approved visible API types and members.", (5, 13, 18, 24, 37, 41),
         ("Java visibility, package access, protected access, nestmates, and JPMS exports are not conflated.", "Violations identify the internal target and approved entry point when unambiguous.", "Reflection and runtime `--add-exports` remain documented blind spots."), RULES + MAIN, "rules"),
    item(47, "cycle-rules", "Rules: cycle-free packages and types with bounded evidence",
         "Apply deterministic cycle checks to selected type and package projections.", (5, 34, 35, 40),
         ("Selectors and exclusions affect nodes and edges consistently.", "Diagnostics report stable representative cycles without combinatorial output.", "Type-use and synthetic-edge inclusion can be configured explicitly."), RULES + MAIN, "rules"),
    item(48, "slices", "Slices: grouping, forbidden dependencies, and mutual independence",
         "Group packages or types into named slices and enforce directional or pairwise independence policies.", (5, 33, 38, 40, 47),
         ("Capture patterns and explicit selectors create stable non-overlapping slice memberships by policy.", "Mutual independence evaluates every distinct selected slice pair.", "Violations retain underlying Java type/member evidence."), MAIN, "rules"),
    item(49, "layers", "Layers: named Java architecture layers and access policies",
         "Define reusable named layers and enforce allowed, forbidden, and isolated access relationships.", (5, 33, 38, 40),
         ("Optional and required layers have explicit empty behavior.", "Layer definitions reject ambiguous membership unless policy allows it.", "Only-accessed-by, may-only-access, and no-access policies share evidence semantics."), MAIN, "rules"),
    item(50, "jpms-rules", "Java modules: readability, exports, opens, and service policies",
         "Enforce architectural rules over JPMS descriptors independently from observed class dependencies.", (5, 24, 35, 40),
         ("Rules cover requires/transitive/static, qualified exports/opens, uses, and provides.", "Descriptor policy and bytecode-observed dependency policy can be compared without conflation.", "Automatic and unnamed modules require explicit caller policy."), MAIN, "rules"),
    item(51, "dead-types", "Rules: unreachable packages and types from configured roots",
         "Detect bounded graph regions unreachable from selected entry points without claiming whole-program liveness.", (5, 13, 20, 35, 40),
         ("Roots, external consumers, reflection-sensitive types, and ignored subjects are explicit inputs.", "Unreachable strongly connected regions produce stable bounded findings.", "Public libraries default to conservative behavior."), RULES + MAIN, "rules"),
    item(52, "exhaustive-policies", "Rules: exhaustive architecture coverage and overlap checks",
         "Require every selected subject to belong to an approved package, layer, slice, or module policy exactly as configured.", (5, 35, 38, 40, 41),
         ("Unassigned and multiply assigned subjects are distinct violations.", "Coverage operates on types, packages, or modules with consistent exclusions.", "Generated/external subjects are excluded only by explicit policy."), RULES + MAIN, "rules"),
    item(53, "architecture-presets", "Presets: clean, onion, hexagonal, and provider-SDK boundaries",
         "Offer transparent composable Java presets that expand to ordinary inspectable rules.", (41, 46, 48, 49, 50, 52),
         ("Presets never silently choose project package names.", "Expanded layers and rules can be renamed, explained, excluded, and extended.", "Clean dependency direction and provider-facing SDK boundaries have executable examples."), MAIN, "rules"),
    item(54, "plantuml", "Diagrams: bounded PlantUML component adherence and export",
         "Validate selected slices/layers against a safe documented PlantUML component subset and export the current architecture.", (48, 49),
         ("Components, aliases, arrows, and approved stereotypes parse deterministically.", "Missing and forbidden edges produce Java evidence without executing includes or macros.", "Export escapes target strings and is byte-stable."), MAIN, "rules"),

    item(55, "graph-snapshot", "Reports: immutable graph snapshots and query options",
         "Create stable report snapshots with filtering, grouping, collapse, and evidence drill-down independent from renderers.", (33, 38),
         ("Nodes and edges have stable IDs and snapshots are detached immutable values.", "Package, type, member, layer, slice, artifact, and module collapse retain counts.", "All renderers consume exactly the same queried snapshot."), REPORT + MAIN, "reporting"),
    item(56, "diagram-renderers", "Reports: deterministic DOT and Mermaid renderers",
         "Render graph snapshots as safely escaped DOT and Mermaid diagrams.", (55,),
         ("Hostile names cannot inject syntax, links, HTML, or directives.", "Equivalent snapshots produce byte-identical output across platforms.", "Truncation and aggregation remain visible in output metadata."), REPORT, "reporting"),
    item(57, "data-html-renderers", "Reports: JSON, CSV, D2, and self-contained HTML",
         "Export one versioned graph schema through data, diagram, and bounded interactive HTML formats.", (55,),
         ("JSON and CSV schemas preserve stable IDs, evidence counts, and query metadata.", "D2 and HTML escape every target-controlled string.", "HTML uses no remote scripts and enforces explicit large-graph limits."), ("pom.xml",) + REPORT, "reporting"),
    item(58, "result-exports", "Results: JSON, SARIF, JUnit XML, and console rendering",
         "Render rule results for people and CI while preserving stable identities, severity, rationale, and locations.", (5, 12, 39),
         ("JSON is versioned and deterministic; SARIF locations are repository-contained.", "JUnit XML and console output distinguish violations from analysis errors.", "Renderer escaping and path normalization handle hostile input."), ("pom.xml",) + REPORT + MAIN, "reporting"),
    item(59, "baselines", "Adoption: reviewed baselines, freezing, and expiring suppressions",
         "Support incremental adoption without hiding new or changed violations.", (5, 39, 58),
         ("Stable fingerprints distinguish new, unchanged, moved, resolved, and expired findings.", "Suppressions require rationale and can be bounded by rule, subject, evidence, and expiry.", "Baseline updates are explicit commands that produce reviewable deterministic diffs."), MAIN, "adoption"),
    item(60, "junit-assertions", "JUnit: Jupiter assertions and executable architecture tests",
         "Expose framework-light assertion helpers and idiomatic JUnit Jupiter usage over the shared Checkable contract.", (5, 39, 40, 41),
         ("Passing rules are silent and failing rules throw one assertion failure with all bounded violations.", "Analysis errors remain distinguishable from policy failures.", "Dynamic tests and ordinary `@Test` examples work without global mutable state."), ("pom.xml", "src/main/java/dev/archunitjava/junit/**", "src/test/java/dev/archunitjava/junit/**"), "integration"),
    item(61, "junit-engine", "JUnit Platform: custom TestEngine, discovery, filtering, and cache",
         "Provide optional annotation-driven discovery and execution through a correctly registered custom JUnit Platform engine.", (30, 40, 60),
         ("Engine IDs, discovery selectors, tags, unique IDs, and execution events follow Platform contracts.", "Import caching is scoped safely across engine execution and can be disabled.", "The JUnit Platform Test Kit verifies discovery, filtering, failures, and parallel execution."), ("pom.xml", "src/main/java/dev/archunitjava/junit/**", "src/test/java/dev/archunitjava/junit/**", "src/main/resources/**"), "integration"),
    item(62, "cli-config", "CLI: validated configuration, checks, graphs, and explanations",
         "Run the same library rules from a bounded declarative configuration and stable command-line interface.", (41, 56, 58),
         ("Check, graph, explain, and config-validation commands have documented exit codes.", "Configuration cannot execute commands, instantiate arbitrary classes, or escape approved roots.", "CLI and Java API results are equivalent for the supported rule subset."), ("pom.xml", "src/main/java/dev/archunitjava/cli/**", "src/test/java/dev/archunitjava/cli/**"), "integration"),
    item(63, "build-integrations", "Build tools: Maven and Gradle integration without target-code execution",
         "Offer first-party Maven and Gradle entry points that consume compiled outputs and the shared CLI/library contracts.", (60, 62),
         ("Plugins document lifecycle ordering and fail clearly when classes are not compiled.", "Multi-module reactor/project inputs are deterministic and do not recursively invoke builds.", "Plugin configuration maps losslessly to the core options and result formats."), ("pom.xml", "build-integrations/**", "src/test/**"), "integration"),

    item(64, "source-metrics", "Metrics: Java counts, cohesion, and threshold rules",
         "Calculate deterministic package/type/member/source counts and documented Java class-cohesion metrics.", (9, 10, 12, 33, 35),
         ("Counts define treatment of synthetic members, records, generated classes, blanks, comments, and missing source.", "LCOM variants state formulas and operate only where required member evidence exists.", "Thresholds use typed units and report every violating subject."), MAIN, "metrics", kind="research"),
    item(65, "dependency-metrics", "Metrics: coupling, instability, distance, and cumulative dependencies",
         "Calculate architecture metrics from explicit projections with documented edge and grouping semantics.", (2, 33, 64),
         ("Afferent/efferent coupling, instability, abstractness, distance, CCD, ACD, RACD, and NCCD define empty cases.", "Filters affect subjects and coupling inputs consistently.", "Cycles, disconnected graphs, split packages, and module projections have formula fixtures."), MAIN, "metrics", kind="research"),
    item(66, "dogfood", "Quality: enforce the library's own architecture with itself",
         "Add self-hosted architecture tests once the critical Java rules and JUnit seam exist.", (41, 49, 60),
         ("Core extraction, projection, rules, reporting, and integrations obey documented dependency boundaries.", "Dogfood tests use public APIs and fail through normal JUnit output.", "Bootstrapping does not create hidden exceptions or test-order dependencies."), ("src/test/**", "docs/**"), "quality"),
    item(67, "performance", "Performance: cold, warm, large-classpath, and report benchmarks",
         "Establish reproducible time and memory baselines before optimization across import, cache, rules, metrics, and reports.", (30, 31, 55, 64, 65),
         ("Benchmarks report classes, members, edges, bytes, wall time, allocations or peak memory, and environment.", "Cold and warm cache results use real representative open-source corpora with recorded versions.", "Optimizations must preserve model and diagnostic snapshots."), ("pom.xml", "benchmarks/**", "docs/PERFORMANCE.md", "src/test/**"), "hardening", kind="research"),
    item(68, "adversarial-corpus", "Security: adversarial importer, rule, cache, and renderer corpus",
         "Test the full threat model against hostile bytecode, archives, paths, metadata, selectors, caches, and outputs.", (32, 56, 57, 58, 59),
         ("No target code, bootstrap method, build script, plugin, annotation processor, or static initializer executes.", "Containment, resource budgets, parser failures, output escaping, and baseline parsing fail closed.", "Residual denial-of-service and static-analysis blind spots are documented honestly."), ("src/test/**", "test-fixtures/**", "docs/THREAT_MODEL.md"), "hardening", kind="research"),
    item(69, "release-readiness", "Release: documentation, compatibility decision, CI, and publishing dry run",
         "Produce a reviewable release candidate and evidence-based go/no-go decision without publishing credentials.", (53, 54, 57, 58, 59, 61, 62, 63, 65, 66, 67, 68),
         ("CI covers Windows/Linux, JDK policy, reproducible builds, tests, docs, package checks, and example consumers.", "README and guides state supported bytecode, JPMS behavior, blind spots, threat model, and migration workflow.", "Coordinates, license, API docs, changelog, compatibility ADR, and signing/publishing dry run agree; nothing is published."), ("pom.xml", "mvnw", "mvnw.cmd", ".mvn/**", ".github/**", "README.md", "LICENSE", "CHANGELOG.md", "docs/**", "examples/**"), "release"),
)


def main() -> None:
    by_number = {entry.number: entry for entry in ITEMS}
    expected = set(range(1, len(ITEMS) + 1))
    if len(by_number) != len(ITEMS) or set(by_number) != expected:
        raise SystemExit("backlog numbers must be unique and contiguous")

    blockers: dict[int, list[int]] = {number: [] for number in by_number}
    for entry in ITEMS:
        for dependency in entry.dependencies:
            if dependency not in by_number:
                raise SystemExit(f"#{entry.number} has unknown dependency #{dependency}")
            if dependency >= entry.number:
                raise SystemExit(f"#{entry.number} must depend only on earlier issues")
            blockers[dependency].append(entry.number)

    tasks_directory = ROOT / ".archunitdev" / "tasks"
    issues_directory = ROOT / "docs" / "issues"
    tasks_directory.mkdir(parents=True, exist_ok=True)
    issues_directory.mkdir(parents=True, exist_ok=True)

    github_issues: list[dict[str, object]] = []
    completed_numbers = {entry.number for entry in ITEMS if entry.initially_completed}
    for entry in ITEMS:
        task = _task(entry, by_number)
        (tasks_directory / entry.task_name).write_text(
            json.dumps(task, indent=2) + "\n", encoding="utf-8"
        )
        body = _issue_body(entry, blockers[entry.number])
        (issues_directory / f"{entry.number:03d}-{entry.slug}.md").write_text(
            body, encoding="utf-8"
        )
        github_issues.append(
            {
                "number": entry.number,
                "title": entry.title,
                "body_file": f"docs/issues/{entry.number:03d}-{entry.slug}.md",
                "labels": [
                    f"phase:{entry.phase}",
                    f"type:{entry.kind}",
                    "contract:specified",
                    "contract:executable" if entry.contract_ready or entry.initially_completed else "contract:missing",
                    "workflow:completed" if entry.initially_completed else (
                        "workflow:dependency-ready"
                        if set(entry.dependencies) <= completed_numbers
                        else "workflow:blocked"
                    ),
                ],
                "initially_completed": entry.initially_completed,
            }
        )

    (ROOT / "docs" / "BACKLOG.md").write_text(
        _backlog(by_number), encoding="utf-8"
    )
    (ROOT / ".archunitdev" / "campaign.template.json").write_text(
        json.dumps(_campaign(by_number), indent=2) + "\n", encoding="utf-8"
    )
    (ROOT / ".archunitdev" / "github-issues.json").write_text(
        json.dumps(github_issues, indent=2) + "\n", encoding="utf-8"
    )
    (ROOT / ".archunitdev" / "github-labels.json").write_text(
        json.dumps(_labels(), indent=2) + "\n", encoding="utf-8"
    )


def _task(entry: Item, by_number: dict[int, Item]) -> dict[str, object]:
    allowed = list(entry.allowed_paths)
    protected = [
        ".archunitdev/**",
        "AGENTS.md",
        "src/test/java/dev/archunitjava/contract/**",
    ]
    optional_protected = {
        ".github": ".github/**",
        "docs": "docs/**",
        "README.md": "README.md",
        "pom.xml": "pom.xml",
        "mvnw": "mvnw",
        "mvnw.cmd": "mvnw.cmd",
        ".mvn": ".mvn/**",
    }
    roots = {path.split("/", 1)[0] for path in allowed}
    protected.extend(value for key, value in optional_protected.items() if key not in roots)
    non_goals = entry.non_goals or _default_non_goals(entry)
    return {
        "id": entry.task_id,
        "title": entry.title,
        "objective": entry.objective,
        "acceptance_criteria": list(entry.criteria),
        "non_goals": list(non_goals),
        "dependencies": [by_number[number].task_id for number in entry.dependencies],
        "allowed_paths": allowed,
        "protected_paths": protected,
        "max_repair_rounds": 1,
    }


def _default_non_goals(entry: Item) -> tuple[str, ...]:
    values = [
        "Do not weaken protected contracts, workflow configuration, or unrelated capabilities.",
        "Do not load or execute target classes, builds, plugins, annotation processors, or code.",
        "Do not copy implementation code from the upstream ArchUnit project.",
    ]
    if "pom.xml" not in entry.allowed_paths:
        values.append("Do not add dependencies or change Maven configuration.")
    return tuple(values)


def _issue_body(entry: Item, blocks: list[int]) -> str:
    depends = ", ".join(f"#{number}" for number in entry.dependencies) or "nothing"
    blocked = ", ".join(f"#{number}" for number in blocks) or "nothing"
    criteria = "\n".join(f"- {value}" for value in entry.criteria)
    non_goals = "\n".join(f"- {value}" for value in (entry.non_goals or _default_non_goals(entry)))
    return (
        f"## Outcome\n\n{entry.objective}\n\n"
        f"## Acceptance criteria\n\n{criteria}\n\n"
        f"## Non-goals\n\n{non_goals}\n\n"
        "## Workflow\n\n"
        f"- Task specification: `.archunitdev/tasks/{entry.task_name}`\n"
        f"- Executable contract overlay: "
        f"{f'`.archunitdev/contracts/{entry.number:03d}-{entry.slug}/`' if entry.contract_ready or entry.initially_completed else 'not authored; do not schedule'}\n"
        f"- Phase: `{entry.phase}`\n"
        f"- Type: `{entry.kind}`\n"
        f"- Depends on: {depends}\n"
        f"- Blocks: {blocked}\n"
    )


def _backlog(by_number: dict[int, Item]) -> str:
    lines = [
        "# Dependency-ordered Java product backlog",
        "",
        "The 69 issue numbers are stable identifiers, not a command to execute all work",
        "serially. A task is runnable only when its dependencies, executable contract,",
        "toolchain, and protected-path policy are ready.",
        "",
        "| Issue | Capability | Phase | Depends on |",
        "|---:|---|---|---|",
    ]
    for entry in ITEMS:
        dependencies = ", ".join(f"#{number}" for number in entry.dependencies) or "—"
        lines.append(f"| #{entry.number} | {entry.title} | {entry.phase} | {dependencies} |")
    lines.extend(
        [
            "",
            "## Why 69 issues",
            "",
            "Ruby's completed 48-issue build supplies the reusable graph/rule/report spine.",
            "Java adds independently risky bytecode and packaging semantics: descriptors,",
            "generic signatures, annotations, records, sealed types, nests, synthetic/bridge",
            "members, invokedynamic, method handles, JPMS, classpath precedence, and",
            "multi-release JARs. Python/TypeScript gaps add rationale, ignore files, public",
            "interfaces, exhaustive rules, presets, CLI, baselines, and machine reports.",
            "The final tasks reserve evidence for performance, security, and release rather",
            "than hiding them inside a generic 'polish' issue.",
            "",
        ]
    )
    return "\n".join(lines)


def _campaign(by_number: dict[int, Item]) -> dict[str, object]:
    completed: set[int] = set()
    remaining = set(by_number)
    order: list[int] = []
    while remaining:
        ready = sorted(number for number in remaining if set(by_number[number].dependencies) <= completed)
        if not ready:
            raise SystemExit("backlog dependency cycle")
        for number in ready:
            order.append(number)
            completed.add(number)
            remaining.remove(number)
    return {
        "schema_version": 1,
        "id": "archunit-java-v1",
        "branch": "campaign/archunit-java-v1",
        "profile": "profile.toml",
        "tasks": [
            {
                "task": f"tasks/{by_number[number].task_name}",
                "issue": number,
                **({"contract": f"contracts/{number:03d}-{by_number[number].slug}"} if by_number[number].contract_ready or by_number[number].initially_completed else {}),
                **({"initially_completed": True} if by_number[number].initially_completed else {}),
            }
            for number in order
        ],
    }


def _labels() -> list[dict[str, str]]:
    phases = sorted({entry.phase for entry in ITEMS})
    kinds = sorted({entry.kind for entry in ITEMS})
    values = [
        {"name": f"phase:{phase}", "color": "1D76DB", "description": f"Backlog phase: {phase}"}
        for phase in phases
    ]
    values.extend(
        {"name": f"type:{kind}", "color": "5319E7", "description": f"Work type: {kind}"}
        for kind in kinds
    )
    values.extend(
        [
            {"name": "contract:specified", "color": "C2E0C6", "description": "Human and machine task specifications are present"},
            {"name": "contract:executable", "color": "0E8A16", "description": "Protected executable contract overlay is present"},
            {"name": "contract:missing", "color": "FBCA04", "description": "Executable contract must be authored before scheduling"},
            {"name": "workflow:dependency-ready", "color": "BFDADC", "description": "Dependencies are complete; executable contract may be prepared"},
            {"name": "workflow:blocked", "color": "D93F0B", "description": "One or more dependency issues are incomplete"},
            {"name": "workflow:completed", "color": "6F42C1", "description": "Task was completed and verified"},
        ]
    )
    return values


if __name__ == "__main__":
    main()
