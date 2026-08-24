## Outcome

Cache imported models by inputs, target release, options, and schema without trusting timestamps alone.

## Acceptance criteria

- Cache keys cover parser/library versions and byte content or validated fingerprints.
- Corrupt, foreign, partial, and concurrent entries fail closed and self-heal.
- Cache serialization cannot instantiate arbitrary classes or escape its directory.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/030-cache.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `import`
- Type: `feature`
- Depends on: #8, #25, #27, #29
- Blocks: #32, #61, #67
