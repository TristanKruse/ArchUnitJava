package dev.archunitjava.metrics;

/** Direction of an inclusive metric threshold. */
public enum MetricComparison {
    AT_MOST,
    AT_LEAST;

    boolean violated(int actualComparedWithLimit) {
        return this == AT_MOST ? actualComparedWithLimit > 0 : actualComparedWithLimit < 0;
    }
}
