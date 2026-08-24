## Outcome

Locate project roots and declared output layouts without executing Maven, Gradle, wrappers, or project code.

## Acceptance criteria

- Explicit roots win and ancestor discovery has bounded deterministic precedence.
- Maven and Gradle metadata is inspected conservatively without running builds.
- Ambiguous multi-project layouts produce diagnostics rather than guessed roots.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/006-project-discovery.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `import`
- Type: `feature`
- Depends on: #1
- Blocks: #7, #25, #29
