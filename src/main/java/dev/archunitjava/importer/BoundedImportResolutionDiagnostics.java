package dev.archunitjava.importer;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Map;

/** Bounded resolution diagnostic sink. */
final class BoundedImportResolutionDiagnostics extends AbstractList<ImportResolutionDiagnostic> {
    private final int maximum;
    private final ArrayList<ImportResolutionDiagnostic> values = new ArrayList<>();
    private boolean truncated;
    private int omitted;

    BoundedImportResolutionDiagnostics(int maximum) {
        if (maximum < 1) throw new IllegalArgumentException("maximum must be positive");
        this.maximum = maximum;
    }

    @Override
    public ImportResolutionDiagnostic get(int index) {
        return values.get(index);
    }

    @Override
    public int size() {
        return values.size();
    }

    @Override
    public boolean add(ImportResolutionDiagnostic value) {
        java.util.Objects.requireNonNull(value, "value");
        if (!truncated && values.size() < maximum) {
            values.add(value);
            return true;
        }
        if (!truncated) {
            truncated = true;
            omitted = 2;
            values.removeLast();
        } else {
            omitted++;
            values.removeLast();
        }
        values.add(new ImportResolutionDiagnostic(
                ImportResolutionDiagnosticCode.DIAGNOSTIC_LIMIT_REACHED,
                "diagnostics",
                Map.of(
                        "maximum", Integer.toString(maximum),
                        "omitted", Integer.toString(omitted))));
        return true;
    }
}
