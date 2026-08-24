## Outcome

Provide optional annotation-driven discovery and execution through a correctly registered custom JUnit Platform engine.

## Acceptance criteria

- Engine IDs, discovery selectors, tags, unique IDs, and execution events follow Platform contracts.
- Import caching is scoped safely across engine execution and can be disabled.
- The JUnit Platform Test Kit verifies discovery, filtering, failures, and parallel execution.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.

## Workflow

- Task specification: `.archunitdev/tasks/061-junit-engine.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `integration`
- Type: `feature`
- Depends on: #30, #40, #60
- Blocks: #69
