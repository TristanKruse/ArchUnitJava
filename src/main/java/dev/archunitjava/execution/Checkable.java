package dev.archunitjava.execution;

/**
 * Shared terminal contract for architecture rules.
 *
 * <p>The return value represents both passing and failing architecture evaluations. Implementations
 * must not throw merely because an architecture rule found violations; exceptions are reserved for
 * {@link UserError user} and {@link TechnicalError technical} execution errors.
 */
@FunctionalInterface
public interface Checkable<R> {
    R check(CheckOptions options);

    default R check() {
        return check(CheckOptions.defaults());
    }
}
