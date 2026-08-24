## Outcome

Render rule results for people and CI while preserving stable identities, severity, rationale, and locations.

## Acceptance criteria

- JSON is versioned and deterministic; SARIF locations are repository-contained.
- JUnit XML and console output distinguish violations from analysis errors.
- Renderer escaping and path normalization handle hostile input.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.

## Workflow

- Task specification: `.archunitdev/tasks/058-result-exports.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `reporting`
- Type: `feature`
- Depends on: #5, #12, #39
- Blocks: #59, #62, #68, #69
