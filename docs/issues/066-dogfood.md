## Outcome

Add self-hosted architecture tests once the critical Java rules and JUnit seam exist.

## Acceptance criteria

- Core extraction, projection, rules, reporting, and integrations obey documented dependency boundaries.
- Dogfood tests use public APIs and fail through normal JUnit output.
- Bootstrapping does not create hidden exceptions or test-order dependencies.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/066-dogfood.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `quality`
- Type: `feature`
- Depends on: #41, #49, #60
- Blocks: #69
