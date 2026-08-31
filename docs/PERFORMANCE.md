# Performance baseline

ArchUnitJava has a reproducible, opt-in performance harness under `benchmarks/`. It analyzes two
pinned JUnit 6.0.3 artifacts independently and as a combined classpath. These are real EPL-2.0
open-source binaries, are already normal test dependencies, and are consumed as archive bytes;
their classes are never loaded or executed by the importer.

## Reference run

The first recorded run used Windows 11 amd64, OpenJDK 25.0.4.1, 8 available processors, and a
4,246,732,800-byte maximum heap. Values below are observations, not release gates:

| Corpus | Input | Classes | Members | Dependency edges | Cold import | Cold cache | Warm cache | Peak observed heap |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `junit-jupiter-api:6.0.3` | 250,715 B | 207 | 1,454 | 2,216 | 1,127 ms | 134 ms | 68 ms | 194 MiB |
| `junit-platform-engine:6.0.3` | 280,233 B | 176 | 1,511 | 2,600 | 306 ms | 149 ms | 47 ms | 194 MiB |
| combined classpath | 530,948 B | 383 | 2,965 | 4,816 | 445 ms | 261 ms | 71 ms | 200 MiB |

The JSON report additionally records rule, dependency-metric, graph-report time and report bytes.
Use multiple warm-up and measurement runs on the same machine for optimization work; a single
sample is intentionally not treated as statistically meaningful.

## Regression policy

Input SHA-256 values and versions are checked before measurement. The benchmark then hashes a
canonical representation of imported types, members, diagnostics, and the rendered graph snapshot.
These semantic hashes are committed in `benchmarks/model.snapshots`. Performance work may change
timing or memory figures, but it must not silently change those hashes. A legitimate semantic change
requires review of both the ordinary extraction corpus and these open-source corpus snapshots.

No absolute time or memory threshold runs in ordinary CI. Host load, filesystem caching, JVM tiered
compilation, garbage collection, and runner sizing make such assertions noisy. A future release may
add statistically evaluated trend checks on dedicated hardware; that is distinct from unit tests.

## Scope and resolved finding

The open-source corpus includes `package-info.class`. Its stable JVM identity now flows through
import, graph construction, type rules, and package composition; the benchmark contains no
package-info exclusion. The resulting semantic snapshot change was reviewed and pinned.

The corpus is still modest and library-shaped rather than a large application. Consequently these
measurements are regression evidence, not a scalability or throughput claim for `0.1.0`.
