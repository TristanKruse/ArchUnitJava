# ArchUnitJava architecture

ArchUnitJava analyses its own compiled main classes during the test phase. The self-test reads
`target/classes` as class-file data; it does not load those classes or execute application code.

The enforced dependency direction is:

1. `graph` is the identity and dependency kernel and has no dependency on higher packages.
2. `importer` and `model` are one extraction cluster. They may collaborate and use `graph`, but do
   not depend on rules, reporting, or integration entry points.
3. `projection` depends on `graph`, not extraction, rules, reporting, or integrations.
4. `rules` is policy code. It may use extraction, projections, selectors, slices/layers, PlantUML
   input values, execution, and result values, but not reporting or integration entry points.
5. `report` formats graph/result/layer/slice values and does not reach back into extraction,
   projections, metrics, or rules.
6. `cli` and `junit` are integration seams. Core extraction, projection, policy, and reporting
   packages do not depend on them. The Maven/Gradle bridge package may depend internally only on
   `cli`; it receives compiled outputs and never starts another build.

The dogfood suite deliberately ignores dependencies whose targets are outside the imported
ArchUnitJava main classes (for example JDK and JUnit APIs). It does not exempt any internal package
or type. Every boundary is an ordinary public `ArchitectureRule`, adapted to an independent JUnit
dynamic test through `ArchitectureTestCases`, so a violation uses the same structured result and
failure rendering as a consumer's test.
