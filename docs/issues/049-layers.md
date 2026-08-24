## Outcome

Define reusable named layers and enforce allowed, forbidden, and isolated access relationships.

## Acceptance criteria

- Optional and required layers have explicit empty behavior.
- Layer definitions reject ambiguous membership unless policy allows it.
- Only-accessed-by, may-only-access, and no-access policies share evidence semantics.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/049-layers.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `rules`
- Type: `feature`
- Depends on: #5, #33, #38, #40
- Blocks: #53, #54, #66
