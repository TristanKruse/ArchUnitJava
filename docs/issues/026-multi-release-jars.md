## Outcome

Select the correct root or META-INF/versions class resource for an explicit target Java release.

## Acceptance criteria

- Only manifests with Multi-Release true enable version selection.
- The highest eligible version wins and ineligible/malformed directories are ignored with diagnostics.
- Versioned module-info handling follows the modular multi-release JAR rules.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/026-multi-release-jars.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `packaging`
- Type: `feature`
- Depends on: #25
- Blocks: #27, #31, #32
