## Outcome

Offer first-party Maven and Gradle entry points that consume compiled outputs and the shared CLI/library contracts.

## Acceptance criteria

- Plugins document lifecycle ordering and fail clearly when classes are not compiled.
- Multi-module reactor/project inputs are deterministic and do not recursively invoke builds.
- Plugin configuration maps losslessly to the core options and result formats.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.

## Workflow

- Task specification: `.archunitdev/tasks/063-build-integrations.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `integration`
- Type: `feature`
- Depends on: #60, #62
- Blocks: #69
