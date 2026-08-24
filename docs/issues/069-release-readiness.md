## Outcome

Produce a reviewable release candidate and evidence-based go/no-go decision without publishing credentials.

## Acceptance criteria

- CI covers Windows/Linux, JDK policy, reproducible builds, tests, docs, package checks, and example consumers.
- README and guides state supported bytecode, JPMS behavior, blind spots, threat model, and migration workflow.
- Coordinates, license, API docs, changelog, compatibility ADR, and signing/publishing dry run agree; nothing is published.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.

## Workflow

- Task specification: `.archunitdev/tasks/069-release-readiness.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `release`
- Type: `feature`
- Depends on: #53, #54, #57, #58, #59, #61, #62, #63, #65, #66, #67, #68
- Blocks: nothing
