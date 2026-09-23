package com.earthpol.earthpollib.config;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Utility for working with and converting between classes
 */
@SuppressWarnings("unused")
final class TypeUtil {

    private TypeUtil() {}

    private static final Map<Class<?>, Class<?>> primitiveToWrapper =
            Map.ofEntries(
                    Map.entry(int.class, Integer.class),
                    Map.entry(long.class, Long.class),
                    Map.entry(double.class, Double.class),
                    Map.entry(float.class, Float.class),
                    Map.entry(boolean.class, Boolean.class),
                    Map.entry(char.class, Character.class),
                    Map.entry(short.class, Short.class),
                    Map.entry(byte.class, Byte.class)
            );

    static @Nullable Class<?> getPrimitiveWrapper(Class<?> clazz){
        return primitiveToWrapper.getOrDefault(clazz, null);
    }

    /** Returns wrapper for primitive, otherwise the class itself, preserving the generic type. */
    @SuppressWarnings("unchecked")
    static <T> Class<T> normalize(Class<T> c) {
        Class<?> w = primitiveToWrapper.get(c);
        return (Class<T>) (w != null ? w : c);
    }
}
