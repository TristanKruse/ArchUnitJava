## Outcome

Group packages or types into named slices and enforce directional or pairwise independence policies.

## Acceptance criteria

- Capture patterns and explicit selectors create stable non-overlapping slice memberships by policy.
- Mutual independence evaluates every distinct selected slice pair.
- Violations retain underlying Java type/member evidence.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/048-slices.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `rules`
- Type: `feature`
- Depends on: #5, #33, #38, #40, #47
- Blocks: #53, #54
