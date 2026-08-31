# Contributing

ArchUnitJava is an independent implementation. Do not copy source from ArchUnit or claim API
compatibility. Open an issue before changing public semantics, schemas, the JDK/runtime floor,
coordinates, or the bytecode backend.

## Local build

Use JDK 25 and the checked-in Maven wrapper:

```shell
./mvnw --batch-mode --no-transfer-progress verify
./mvnw --batch-mode --no-transfer-progress -Prelease-candidate verify
```

On Windows, use `mvnw.cmd`. Tests must not load target classes, run target builds, execute annotation
processors, or follow target-controlled output paths. Keep externally observable collections and
rendered output deterministic.

## Pull requests

- Add focused regression tests for every behavioral change.
- Preserve explicit incomplete-analysis and empty-selection behavior.
- Update compatibility, threat-model, migration, and release documentation when their claims move.
- Run the independent RAG consumer for changes to packaging, CLI, JUnit, or build integration.
- Do not add runtime dependencies, network behavior, native code, reflection over target types, or
  publication credentials without a reviewed design and security justification.
- Keep commits scoped and explain residual risk. Passing packaging alone is not release evidence.

The product pipeline and ownership rules are described in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).
