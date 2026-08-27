package dev.archunitjava.layers;

import dev.archunitjava.graph.StableId;

/** Stable identity of a named layer, including an internal unassigned boundary identity. */
public record LayerId(String name) implements StableId {
    private static final String UNASSIGNED = "<unassigned>";

    public LayerId {
        if (name == null || name.isBlank() || name.indexOf('\0') >= 0
                || name.indexOf('\r') >= 0 || name.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Layer name must be non-blank single-line text");
        }
    }

    public static LayerId named(String name) {
        LayerId result = new LayerId(name);
        if (result.isUnassigned()) {
            throw new IllegalArgumentException("Layer name is reserved: " + name);
        }
        return result;
    }

    public static LayerId unassigned() {
        return new LayerId(UNASSIGNED);
    }

    public boolean isUnassigned() {
        return name.equals(UNASSIGNED);
    }

    @Override
    public String stableKey() {
        return "layer:" + name;
    }
}
