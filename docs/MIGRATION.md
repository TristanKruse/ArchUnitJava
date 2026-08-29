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

The current candidate has no bounded JSON baseline reader. Consequently a rendered baseline cannot
yet be loaded by the CLI in a later process, and the complete persisted migration workflow is not
release-ready. Do not build an ad-hoc object-deserialization or permissive JSON workaround. The
planned reader must bound bytes, nesting, strings, findings and suppressions; reject duplicate or
unknown fields and schemas; validate fingerprints; and produce typed diagnostics.

## 5. Upgrade deliberately

Pin an exact artifact version and review `CHANGELOG.md`, schema versions, semantic performance
snapshots, and compatibility decisions on every upgrade. Regenerate baselines only for explained
semantic changes. Never accept a timing improvement that silently changes model or diagnostic
snapshots.
