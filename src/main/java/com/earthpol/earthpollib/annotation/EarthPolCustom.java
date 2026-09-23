package com.earthpol.earthpollib.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks that this code element has been customized in an EarthPol fork of a plugin.
 * <p>
 * Keep it lightweight for IDE visibility. Because retention is {@code SOURCE}, the
 * annotation is not kept in the compiled bytecode or shaded plugin jars.
 * </p>
 *
 * <h3>Sample usages</h3>
 *
 * <pre>
 * // Minimal, no arguments
 * &#64;EarthPolCustom
 * void foo() { }
 *
 * // Only description
 * &#64;EarthPolCustom("Fix NPE when town is null")
 * void bar() { }
 *
 * // Only tags
 * &#64;EarthPolCustom(tags = {"bugfix", "siege"})
 * void baz() { }
 *
 * // Description + tags
 * &#64;EarthPolCustom(value = "Adjust Folia warmup", tags = {"performance"})
 * void qux() { }
 * </pre>
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({
        ElementType.TYPE,
        ElementType.METHOD,
        ElementType.CONSTRUCTOR,
        ElementType.FIELD,
        ElementType.PARAMETER
})
@SuppressWarnings("unused")
public @interface EarthPolCustom {
    /**
     * Optional description of what the change accomplishes.
     * <p>
     * With a default value, you can omit it and just write {@code @EarthPolCustom}.
     * </p>
     */
    String value() default "";

    /**
     * Optional keywords to aid searching and categorization.
     * <p>
     * Example: {@code {"bugfix", "performance"}}.
     * </p>
     */
    String[] tags() default {};
}
