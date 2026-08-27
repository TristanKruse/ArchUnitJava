package dev.archunitjava.slices;

/** Invalid or ambiguous slice membership under the requested policies. */
public final class SliceMembershipException extends IllegalArgumentException {
    public SliceMembershipException(String message) {
        super(message);
    }
}
