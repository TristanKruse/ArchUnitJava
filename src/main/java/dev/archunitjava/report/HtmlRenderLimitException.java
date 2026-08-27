package dev.archunitjava.report;

/** Refusal to build an interactive report larger than caller-approved bounds. */
public final class HtmlRenderLimitException extends IllegalArgumentException {
    public HtmlRenderLimitException(String message) {
        super(message);
    }
}
