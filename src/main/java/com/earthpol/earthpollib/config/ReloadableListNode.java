package com.earthpol.earthpollib.config;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * List-specific configuration node.
 *
 * <p>Stores an ordered list of elements of {@code T} and validates element types on assignment.
 * Values are defensively copied to avoid external mutation.</p>
 */
public class ReloadableListNode<T> extends ReloadableConfigNode<List<T>> {

    private final Class<T> elementType;

    private ReloadableListNode(String ymlPath,
                               @NotNull Class<T> elementType,
                               List<T> defaultValue,
                               List<String> comment) {
        super(ymlPath, listClass(), defaultValue, comment);
        this.elementType = Objects.requireNonNull(elementType, "elementType");
        validateElements(defaultValue);
    }

    public static <T> @NotNull ReloadableListNode<T> ofList(
            String path,
            Class<T> elementType,
            List<T> defaultValue
    ) {
        return ofList(path, elementType, defaultValue, List.of());
    }

    public static <T> @NotNull ReloadableListNode<T> ofList(
            String path,
            Class<T> elementType,
            List<T> defaultValue,
            String... comments
    ) {
        List<String> lines = (comments == null || comments.length == 0)
                ? List.of()
                : List.copyOf(Arrays.asList(comments));
        return ofList(path, elementType, defaultValue, lines);
    }

    public static <T> @NotNull ReloadableListNode<T> ofList(
            String path,
            Class<T> elementType,
            List<T> defaultValue,
            List<String> comments
    ) {
        List<T> safeDefault = defaultValue == null ? List.of() : List.copyOf(defaultValue);
        List<String> safeComments = comments == null ? List.of() : List.copyOf(comments);
        return new ReloadableListNode<>(
                Objects.requireNonNull(path, "path"),
                elementType,
                safeDefault,
                safeComments
        );
    }

    public Class<T> getElementType() {
        return elementType;
    }

    @Override
    public void setValue(List<T> value) {
        Objects.requireNonNull(value, "list node data");
        super.setValue(List.copyOf(value));
    }

    @Override
    public void validateValue(List<T> value) {
        super.validateValue(value);
        validateElements(value);
        if (value.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Null element in '" + getYmlPath() + "'");
        }
    }

    private void validateElements(List<T> value) {
        for (T el : value) {
            if (el != null && !elementType.isInstance(el)) {
                throw new IllegalArgumentException(
                        "Wrong element type for '" + getYmlPath() + "': expected " +
                                elementType.getSimpleName() + ", got " + el.getClass().getSimpleName()
                );
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<List<T>> listClass() {
        return (Class<List<T>>) (Class<?>) List.class;
    }

}
