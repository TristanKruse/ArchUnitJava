package dev.archunitjava.importer;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Map;

/** Bounded class-file diagnostic sink used before immutable result construction. */
final class BoundedClassFileDiagnostics extends AbstractList<ClassFileDiagnostic> {
    private final int maximum;
    private final ArrayList<ClassFileDiagnostic> values = new ArrayList<>();
    private boolean truncated;
    private int omitted;
    private ClassFileDiagnostic anchor;

    BoundedClassFileDiagnostics(int maximum) {
        if (maximum < 1) throw new IllegalArgumentException("maximum must be positive");
        this.maximum = maximum;
    }

    @Override
    public ClassFileDiagnostic get(int index) {
        return values.get(index);
    }

    @Override
    public int size() {
        return values.size();
    }

    @Override
    public boolean add(ClassFileDiagnostic value) {
        java.util.Objects.requireNonNull(value, "value");
        if (!truncated && values.size() < maximum) {
            values.add(value);
            return true;
        }
        if (!truncated) {
            truncated = true;
            omitted = 2;
            anchor = value;
            values.removeLast();
        } else {
            omitted++;
            values.removeLast();
        }
        values.add(summary());
        return true;
    }

    private ClassFileDiagnostic summary() {
        return new ClassFileDiagnostic(
                ClassFileDiagnosticCode.DIAGNOSTIC_LIMIT_REACHED,
                anchor.resourceName(),
                anchor.origin(),
                anchor.phase(),
                Map.of(
                        "maximum", Integer.toString(maximum),
                        "omitted", Integer.toString(omitted)));
    }
}
