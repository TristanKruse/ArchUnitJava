package dev.archunitjava.result;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

final class ResultValues {
    private ResultValues() {}

    static String requireText(String value, String description) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(description + " must not be blank");
        }
        return value;
    }

    static Map<String, String> sortedTextMap(Map<String, String> values, String description) {
        Objects.requireNonNull(values, description);
        TreeMap<String, String> sorted = new TreeMap<>();
        values.forEach((key, value) -> sorted.put(
                requireText(key, description + " key"), requireText(value, description + " value")));
        return Collections.unmodifiableMap(sorted);
    }

    static <T extends Comparable<? super T>> List<T> sortedDistinct(
            List<T> values, String description) {
        Objects.requireNonNull(values, description);
        TreeSet<T> sorted = new TreeSet<>();
        for (T value : values) sorted.add(Objects.requireNonNull(value, description + " value"));
        return List.copyOf(sorted);
    }

    static <T extends Comparable<? super T>> int compareLists(List<T> left, List<T> right) {
        int sharedSize = Math.min(left.size(), right.size());
        for (int index = 0; index < sharedSize; index++) {
            int result = left.get(index).compareTo(right.get(index));
            if (result != 0) return result;
        }
        return Integer.compare(left.size(), right.size());
    }

    static int compareMaps(Map<String, String> left, Map<String, String> right) {
        var leftIterator = left.entrySet().iterator();
        var rightIterator = right.entrySet().iterator();
        while (leftIterator.hasNext() && rightIterator.hasNext()) {
            var leftEntry = leftIterator.next();
            var rightEntry = rightIterator.next();
            int result = leftEntry.getKey().compareTo(rightEntry.getKey());
            if (result != 0) return result;
            result = leftEntry.getValue().compareTo(rightEntry.getValue());
            if (result != 0) return result;
        }
        return Boolean.compare(leftIterator.hasNext(), rightIterator.hasNext());
    }
}
