package dev.archunitjava.selector;

/** Raised only when a selector's explicit unknown policy is {@link UnknownHierarchyPolicy#FAIL}. */
public final class IncompleteSelectionException extends IllegalStateException {
    private final SelectionDiagnostic diagnostic;

    public IncompleteSelectionException(SelectionDiagnostic diagnostic) {
        super(diagnostic.code() + " for " + diagnostic.subject() + ": " + diagnostic.detail());
        this.diagnostic = diagnostic;
    }

    public SelectionDiagnostic diagnostic() {
        return diagnostic;
    }
}
