## Outcome

Create a generated fixture corpus that locks down Java 8 through 25 class-file constructs and toolchain variations.

## Acceptance criteria

- Fixtures cover javac output, hand-built malformed class files, modules, records, sealed types, annotations, and lambdas.
- Expected models and edges are reviewed deterministic snapshots.
- Corpus generation is separate from product tests so product analysis never runs target builds.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/031-extraction-corpus.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `extract`
- Type: `research`
- Depends on: #14, #16, #17, #19, #20, #21, #22, #23, #24, #26, #28
- Blocks: #67
