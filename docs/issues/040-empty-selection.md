## Outcome

Prevent misspelled or stale selectors from silently passing any architecture rule.

## Acceptance criteria

- Every terminal uses one shared empty-selection policy.
- Check options can deliberately allow, warn, or fail empty selections.
- Diagnostics show the selector and practical remediation.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/040-empty-selection.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `rules`
- Type: `feature`
- Depends on: #5, #35
- Blocks: #41, #42, #43, #44, #45, #47, #48, #49, #50, #51, #52, #60, #61
