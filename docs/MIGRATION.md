# Adoption and migration workflow

## 1. Establish a trustworthy import

Compile the application normally, then point ArchUnitJava at existing class directories or JARs.
ArchUnitJava does not run that build. Keep `allowIncompleteAnalysis=false`, review duplicate/missing
type and unsupported-bytecode diagnostics, and pin classpath order and the target Java release.

## 2. Start with observable reports

Generate a type or package graph and keep its versioned snapshot in CI artifacts. Review omitted and
truncated counts before treating a visualization as complete. SARIF and JUnit XML keep policy
failures separate from analysis failures; use the stable CLI exit codes rather than parsing prose.

## 3. Add narrow executable policies

Begin with one package or type boundary whose selected origins and targets are easy to inspect. Empty
selections fail by default. Decide self-dependency and external-type behavior explicitly, add a
rationale and tags, and run the same configuration locally and in CI. Expand to layers, slices,
cycles, public interfaces, JPMS, and metrics only after the underlying graph is understood.

## 4. Handle existing debt explicitly

The Java API can freeze current `RuleResult` values into `ReviewedBaseline`, compare a later report,
classify new/moved/resolved findings, enforce expiring scoped suppressions, and render canonical JSON
and a reviewable diff. Baseline updates are proposals; they never mutate files implicitly.

Persist canonical JSON with `BaselineJsonRenderer`, then load it in a later process with
`BaselineJsonReader.read(path, BaselineReadLimits.defaults())`. The reader bounds bytes, nesting,
strings, findings, suppressions, and array values; accepts strict UTF-8 regular files only; and
rejects duplicate or unknown fields, unsupported schemas, invalid Unicode/dates, and fingerprint
conflicts. File selection remains caller-owned policy: pass a path from an approved workspace rather
than discovering baseline paths inside an untrusted target repository.

## 5. Upgrade deliberately

Pin an exact artifact version and review `CHANGELOG.md`, schema versions, semantic performance
snapshots, and compatibility decisions on every upgrade. Regenerate baselines only for explained
semantic changes. Never accept a timing improvement that silently changes model or diagnostic
snapshots.
