## Outcome

Enforce names and locations for selected packages, types, and members.

## Acceptance criteria

- Simple, binary, package, source-file, class-resource, and artifact locations are distinct targets.
- Positive and negative pattern rules return all violations deterministically.
- Anonymous/local/generated subjects require explicit inclusion.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/042-naming-location-rules.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `rules`
- Type: `feature`
- Depends on: #5, #12, #35, #36, #40
- Blocks: nothing
