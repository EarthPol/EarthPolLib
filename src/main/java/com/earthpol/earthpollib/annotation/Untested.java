package com.earthpol.earthpollib.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>Indicates that this block of code is untested in any downstream plugins. We can't test everything to exhaustion
 * or else we'd never get anything done around here. Therefore, some of the less critical stuff gets left behind
 * testing-wise until we need to use it.</p>
 * <p>This annotation serves to tell us where those untested blocks of code are located so we can be aware of what
 * might need fixing when we try it out.</p>
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
@SuppressWarnings("unused")
public @interface Untested {
    /**
     * Optional note that will be shown in the warning.
     */
    String value() default "";
}
