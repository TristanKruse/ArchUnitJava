## Outcome

Reconstruct ownership evidence from InnerClasses, EnclosingMethod, NestHost, and NestMembers without inventing source nesting.

## Acceptance criteria

- Member, local, anonymous, and top-level types remain distinguishable when attributes permit.
- Nest host/member relationships are modeled separately from lexical ownership.
- Conflicting attributes produce bounded diagnostics and deterministic fallback.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/017-nested-types.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `model`
- Type: `feature`
- Depends on: #9, #10, #13
- Blocks: #31, #37
