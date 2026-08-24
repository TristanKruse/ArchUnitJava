## Outcome

Export one versioned graph schema through data, diagram, and bounded interactive HTML formats.

## Acceptance criteria

- JSON and CSV schemas preserve stable IDs, evidence counts, and query metadata.
- D2 and HTML escape every target-controlled string.
- HTML uses no remote scripts and enforces explicit large-graph limits.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.

## Workflow

- Task specification: `.archunitdev/tasks/057-data-html-renderers.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `reporting`
- Type: `feature`
- Depends on: #55
- Blocks: #68, #69
