## Outcome

Support incremental adoption without hiding new or changed violations.

## Acceptance criteria

- Stable fingerprints distinguish new, unchanged, moved, resolved, and expired findings.
- Suppressions require rationale and can be bounded by rule, subject, evidence, and expiry.
- Baseline updates are explicit commands that produce reviewable deterministic diffs.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/059-baselines.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `adoption`
- Type: `feature`
- Depends on: #5, #39, #58
- Blocks: #68, #69
