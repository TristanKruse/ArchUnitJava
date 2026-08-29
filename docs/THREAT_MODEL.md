# ArchUnitJava importer threat model

## Boundary

ArchUnitJava treats caller-supplied configuration as trusted policy. This includes approved input
roots, classpath order, target Java release, resource limits, import rules, cache directory, and
output destinations. The library validates these values, but it does not try to protect a caller
from policy that deliberately grants broad access.

Everything discovered below those approved roots is untrusted: directory entries, symbolic links,
JAR/ZIP structures and names, manifests, `.archignore`, class bytes and attributes, debug metadata,
annotations, module descriptors, cache files, and cached payloads. Import never loads a target
class, invokes target code, runs a target build, executes an annotation processor, or resolves a
target-controlled output path.

The checked-in adversarial manifest covers static initializers, `invokedynamic` bootstrap handles,
malformed class batches, archive traversal and compression, approved-root escapes, PlantUML
directives, selector syntax, serialized and corrupt cache data, hostile renderer strings, and
baseline value invariants. Payloads are generated in temporary directories so the repository does
not distribute executable fixtures. The integration test verifies that static initialization and
bootstrap counters remain untouched after full model adaptation.

## Enforced limits

`InputEnumerationOptions` bounds the number of inputs, resources, directory depth and entries,
archive bytes and entries, total uncompressed class bytes, per-entry compression ratio, resource
name length, and emitted diagnostics. Nested archives are not traversed at any configured depth;
their presence is diagnosed and ignored. Manifest class-path expansion remains disabled by default
and, when enabled, has separate entry, byte, depth, and containment limits.

`ClassFileReaderOptions` bounds each class resource and parser diagnostics. `ImportOptions` bounds
`.archignore` bytes and lines. The analysis cache bounds payload and envelope bytes, derives entry
names only from SHA-256 keys, rejects symlinks and foreign/corrupt/partial envelopes, validates
digests before returning bytes, and replaces invalid entries under process and file locks.

ZIP-slip names, absolute or parent-relative paths, backslashes, symbolic links, overlong names,
unknown archive sizes, excessive compression ratios, malformed UTF-8 ignore files, and invalid
cache envelopes fail closed with typed bounded diagnostics. Configuration files are parsed as data:
there is no command, environment-variable, object-deserialization, reflection, or classloading
seam.

HTML output escapes target strings, applies explicit size limits, and embeds a restrictive content
security policy. JSON, Mermaid, D2, and DOT quote or escape their data domains and use generated graph
identifiers. PlantUML input is a bounded allowlisted component grammar; includes, directives, macros,
and general PlantUML execution are rejected. Baseline domain values reject unknown schemas,
fingerprint conflicts, and duplicate suppression IDs. There is currently no JSON baseline reader, so
untrusted baseline text has no parsing or activation path.

## Residual risk

Static parsing still consumes CPU and heap below configured limits. A large number of individually
valid class files, expensive class-file structures, filesystem latency, hash computation, and ZIP
implementation behavior can cause denial of service if limits are set too high. SHA-256 collision
risk is accepted. TOCTOU changes to approved files can cause a read to fail or yield a different
content-addressed key; timestamps are never treated as proof of identity. The importer reports
unsupported or malformed constructs but cannot infer dependencies created only through reflection,
native code, runtime code generation, service configuration not represented in analyzed metadata,
or dynamically assembled strings.

The following gaps remain release-significant:

- Java regular-expression selectors are trusted policy and use the JDK regex engine without an
  evaluation budget. Untrusted regex configuration could consume excessive CPU.
- CSV is syntactically escaped but does not neutralize spreadsheet formulas. Consumers must import it
  as data rather than opening untrusted reports directly in a spreadsheet application.
- Baselines can be rendered but not read back. A future bounded JSON reader must reject duplicate
  keys, excessive nesting/size/counts, unknown fields and schemas, invalid Unicode, and conflicting
  fingerprints before baseline migration can be advertised.
- `package-info.class` imports successfully, but some type-rule and component-metric entry points
  currently reject its binary name when constructing graph identities.
- Static analysis cannot observe behavior introduced only at runtime, and resource limits reduce but
  cannot eliminate denial-of-service risk from expensive valid inputs, storage latency, or TOCTOU
  replacement inside caller-approved roots.
