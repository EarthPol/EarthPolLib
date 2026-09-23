package com.earthpol.earthpollib.config;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public final class ConfigValidators {
    private ConfigValidators() {}

    /** Inclusive bounds. Use bounds of the same type as the configuration node. */
    public static <T extends Comparable<? super T>> Predicate<T> range(T minimum, T maximum) {
        Objects.requireNonNull(minimum, "minimum");
        Objects.requireNonNull(maximum, "maximum");
        if (minimum.compareTo(maximum) > 0) throw new IllegalArgumentException("minimum exceeds maximum");
        return value -> value != null && value.compareTo(minimum) >= 0 && value.compareTo(maximum) <= 0;
    }

    public static <T> Predicate<T> oneOf(Collection<T> allowed) {
        Set<T> values = Set.copyOf(allowed);
        return value -> value != null && values.contains(value);
    }

    public static Predicate<String> nonBlank() {
        return value -> value != null && !value.isBlank();
    }
}
