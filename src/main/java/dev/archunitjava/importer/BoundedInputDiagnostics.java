package dev.archunitjava.importer;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Map;

/** Mutable internal sink that never retains more than the configured diagnostic count. */
final class BoundedInputDiagnostics extends AbstractList<InputDiagnostic> {
    private final int maximum;
    private final ArrayList<InputDiagnostic> values = new ArrayList<>();
    private boolean truncated;
    private int omitted;

    BoundedInputDiagnostics(int maximum) {
        if (maximum < 1) throw new IllegalArgumentException("maximum must be positive");
        this.maximum = maximum;
    }

    @Override
    public InputDiagnostic get(int index) {
        return values.get(index);
    }

    @Override
    public int size() {
        return values.size();
    }

    @Override
    public boolean add(InputDiagnostic value) {
        if (!truncated && values.size() < maximum) {
            values.add(java.util.Objects.requireNonNull(value, "value"));
            return true;
        }
        java.util.Objects.requireNonNull(value, "value");
        if (!truncated) {
            truncated = true;
            omitted = 2;
            values.removeLast();
        } else {
            omitted++;
            values.removeLast();
        }
        values.add(summary(omitted));
        return true;
    }

    private InputDiagnostic summary(int count) {
        return new InputDiagnostic(
                InputDiagnosticCode.DIAGNOSTIC_LIMIT_REACHED,
                "diagnostics",
                Map.of(
                        "maximum", Integer.toString(maximum),
                        "omitted", Integer.toString(count)));
    }
}
