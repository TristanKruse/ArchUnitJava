package dev.archunitjava.selector;

/** Whether a selector is known without inspecting candidates or is genuinely conditional. */
public enum SelectorConstant {
    UNIVERSAL,
    EMPTY,
    CONDITIONAL
}
