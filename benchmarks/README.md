# ArchUnitJava performance baselines

This opt-in benchmark exercises the real import, cache, rule, metric, snapshot, and rendering
pipelines against pinned JUnit artifacts. It is a regression baseline, not a leaderboard and not a
unit-test latency gate. Wall-clock and heap figures vary by machine; semantic snapshots and corpus
digests do not.

Run it after `verify` so the benchmark classes and pinned corpus artifacts exist:

```shell
./mvnw --batch-mode --no-transfer-progress verify
./mvnw --batch-mode --no-transfer-progress \
  -Dtest=dev.archunitjava.performance.PerformanceBaselineTest \
  -Darchunitjava.performance=true test
```

PowerShell uses the same properties with `mvnw.cmd`. Maven's normal `~/.m2/repository` is used by
default. Environments that relocate the Maven cache must also pass
`-Darchunitjava.performance.repository=/absolute/repository/path`; the benchmark never searches
unrelated filesystem roots for matching filenames. The report is written to
`target/benchmarks/performance.json` and also printed to standard output. It contains:

- corpus coordinate, version, SHA-256, and input bytes;
- class, member, dependency-edge, report-edge, and report-byte counts;
- cold import, cold cache, warm cache, rule, metric, and report wall times;
- peak observed used heap at stage boundaries; and
- OS, architecture, processors, JVM, Java, and maximum-heap details.

`corpora.lock` is the supply-chain and reproducibility record. `model.snapshots` is the semantic
regression gate. A changed artifact digest is rejected before measurement. A deliberate model or
diagnostic change requires review of the snapshot material and an explicit snapshot update.

## Interpretation and limitations

The benchmark forks no target process and the importer only reads archive entries as data. The
current persistent cache stores an opaque deterministic snapshot payload. Therefore `cacheColdNs`
includes analysis plus the first validated cache write, while `cacheWarmNs` measures validation and
reading of that payload; it does not yet claim a deserialized-model warm-import number.

The heap value is a process-wide high-water observation at stage boundaries, not an allocation
profile. Use JFR or an external profiler for allocation attribution. Times have no pass/fail
threshold because shared CI runners and developer machines are not comparable. Compare repeated
runs on the same pinned environment, and never accept an optimization if `model.snapshots` changes
without an explained semantic change.
