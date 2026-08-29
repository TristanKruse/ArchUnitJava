# Adversarial corpus

`corpus.tsv` is the reviewable threat-vector manifest. Executable-looking class files, archives,
serialized objects, paths, and output strings are synthesized inside temporary test directories;
the repository intentionally does not ship an active binary payload.

The corpus tests composition across importer, model, configuration, selector, cache, baseline, and
renderer boundaries. Focused unit tests remain the source of exhaustive behavior for each component.
Adding a new input or output surface requires a vector here or an explicit residual-risk statement in
`docs/THREAT_MODEL.md`.
