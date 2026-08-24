## Outcome

Require, forbid, or constrain Java annotations on types, members, parameters, packages, and type uses.

## Acceptance criteria

- Direct, meta, inherited, visible, and invisible matching modes are explicit.
- Annotation value predicates are typed and deterministic.
- Placement violations retain exact declaration or type-use location.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/044-annotation-rules.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `rules`
- Type: `feature`
- Depends on: #5, #14, #37, #40
- Blocks: nothing
