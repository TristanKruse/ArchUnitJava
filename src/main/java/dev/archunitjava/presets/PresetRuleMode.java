package dev.archunitjava.presets;

/** Ordinary rule shapes available to transparent presets. */
public enum PresetRuleMode {
    EXACTLY_ONE_LAYER,
    MAY_ONLY_ACCESS,
    NO_ACCESS,
    ONLY_ACCESSED_BY,
    ISOLATED,
    PUBLIC_INTERFACE
}
