# CLI configuration reference

The properties schema is `archunitjava.cli.v1`. It is a strict, UTF-8, non-executable format:
unknown and duplicate keys fail validation, configuration is limited to 65,536 bytes and 1,024
lines, and every resolved file path must remain within the `--root` directory supplied by the
caller.

## Commands

```text
archunitjava <check|graph|explain|validate-config>
  --config <file>
  --root <approved-directory>
  [--result-format console|json|sarif|junit-xml]
  [--graph-format dot|mermaid|json|csv|d2|html]
```

| Command | Behavior |
| --- | --- |
| `validate-config` | Parse and validate the file and every approved path without analyzing bytecode |
| `explain` | Print normalized rules without analyzing bytecode |
| `check` | Import configured inputs and evaluate every rule |
| `graph` | Import configured inputs and render the configured type or package graph |

Command-line format flags override the corresponding configuration value. Other unknown options
are rejected.

## Root keys

| Key | Required | Values and default |
| --- | --- | --- |
| `schema` | yes | Exactly `archunitjava.cli.v1` |
| `inputs` | yes | Comma-separated class directories or JARs, maximum 64, resolved inside the approved root |
| `rules` | yes | Comma-separated unique rule IDs, maximum 256; IDs use 1–64 letters, digits, `.`, `_`, or `-` |
| `emptySelection` | no | `allow`, `warn`, or `fail` (default `fail`) |
| `allowIncompleteAnalysis` | no | `true` or `false` (default `false`) |
| `resultFormat` | no | `console`, `json`, `sarif`, or `junit-xml` (default `console`) |
| `graphFormat` | no | `dot`, `mermaid`, `json`, `csv`, `d2`, or `html` (default `dot`) |
| `graphDomain` | no | `types` or `packages` (default `types`) |

Lists reject empty or duplicate items. Relative paths resolve from the approved root, not from an
ambient process directory.

## Rule keys

For each ID in `rules`, use the prefix `rule.<id>.`.

| Field | Required | Values and behavior |
| --- | --- | --- |
| `domain` | yes | `types` or `packages` |
| `mode` | yes | `no`, `only`, `any`, or `required` |
| `origins` | yes | One `exact:` or `glob:` qualified-name pattern |
| `targets` | yes | One `exact:` or `glob:` qualified-name pattern |
| `self` | no | `include` or `ignore` (default `ignore`) |
| `external` | no | `ignore`, `fail`, `non-matching`, or `treat-as-non-matching` (default `fail`) |
| `displayName` | no | Human-readable name, 1–4,096 characters |
| `rationale` | no | Human-readable reason, 1–4,096 characters |
| `tags` | no | Comma-separated unique tags; each tag uses 1–64 letters, digits, `.`, `_`, or `-` |
| `severity` | no | `info`, `warning`, or `error` (default `error`) |

Rule modes mean:

- `no`: no selected origin may have a matching dependency;
- `only`: every considered dependency from a selected origin must match the target;
- `any`: at least one matching dependency must exist in the selected origin set; and
- `required`: every selected origin must have at least one matching dependency.

`external=fail` is the conservative default. Use `ignore` only when dependencies outside the
imported model are intentionally irrelevant. `non-matching` retains external edges and lets the
rule mode decide whether they violate the policy.

## Patterns

`exact:` matches one qualified binary name. `glob:` supports the bounded project pattern language;
`**` is useful for a package and its descendants. Each expression is limited to 512 characters.
Arbitrary regular expressions and executable factories are not accepted by configuration.

```properties
rule.domain-isolation.origins=glob:com.example.domain.**
rule.domain-isolation.targets=glob:com.example.infrastructure.**
```

For nested classes, use binary names such as `com.example.Outer$Inner` where an exact type identity
is required.

## Complete example

```properties
schema=archunitjava.cli.v1
inputs=target/classes
rules=api-boundary,domain-isolation
emptySelection=fail
allowIncompleteAnalysis=false
resultFormat=json
graphFormat=mermaid
graphDomain=types

rule.api-boundary.domain=types
rule.api-boundary.mode=no
rule.api-boundary.origins=glob:com.example.api.**
rule.api-boundary.targets=glob:com.example.infrastructure.**
rule.api-boundary.self=ignore
rule.api-boundary.external=ignore
rule.api-boundary.displayName=API must not bypass the application layer
rule.api-boundary.rationale=Keep concrete adapters behind application ports
rule.api-boundary.tags=api,boundary
rule.api-boundary.severity=error

rule.domain-isolation.domain=types
rule.domain-isolation.mode=no
rule.domain-isolation.origins=glob:com.example.domain.**
rule.domain-isolation.targets=glob:com.example.infrastructure.**
rule.domain-isolation.external=ignore
rule.domain-isolation.displayName=Domain must remain infrastructure-independent
rule.domain-isolation.severity=error
```

## Exit codes

| Code | Meaning |
| --- | --- |
| `0` | Success |
| `2` | Invalid command-line usage |
| `3` | Invalid configuration |
| `4` | Analysis failed or remained incomplete under the configured policy |
| `5` | Architecture policy violation |

Callers should preserve these distinctions. In particular, do not report an analysis failure as an
ordinary architecture violation.

Continue with the [user guide](USER_GUIDE.md), the
[working example](../examples/basic), or the
[CLI API reference](https://tristankruse.github.io/ArchUnitJava/api/dev.archunitjava/dev/archunitjava/cli/package-summary.html).
