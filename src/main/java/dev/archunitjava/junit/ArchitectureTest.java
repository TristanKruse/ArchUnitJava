package dev.archunitjava.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a static, no-argument method that supplies one immutable architecture rule. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ArchitectureTest {
    /** Optional display name; the method name is used when this is blank. */
    String value() default "";

    /** JUnit Platform tags attached to the discovered test descriptor. */
    String[] tags() default {};
}
