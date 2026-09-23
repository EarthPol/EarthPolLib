package com.earthpol.earthpollib.config;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

// TODO: Eventually move ReloadableConfigNode and its subclasses to confignode package folder.

/**
 * Base class for a single configuration value (scalar).
 *
 * <p>Instances store the YAML path, declared type, default value, and current in-memory value.
 * They are mutable and perform runtime type checks on assignment.</p>
 *
 * <p>List behavior is implemented by {@link ReloadableListNode}; this class intentionally
 * stays scalar-only.</p>
 */
@SuppressWarnings("unused")
public class ReloadableConfigNode<T> {

    private final String ymlPath;
    private final List<String> comment;

    private volatile T data;
    private final T defaultValue;
    private final Class<?> dataType;
    private final List<Rule<T>> rules = new ArrayList<>();
    private boolean restartRequired;

    private record Rule<T>(Predicate<? super T> test, String message) {}

    ReloadableConfigNode(String ymlPath,
                         @NotNull Class<T> dataType,
                         T defaultValue,
                         List<String> comment) {

        this.ymlPath = Objects.requireNonNull(ymlPath, "ymlPath");
        this.comment = List.copyOf(Objects.requireNonNull(comment, "comment"));

        this.dataType = normalizeDataType(Objects.requireNonNull(dataType, "dataType"));

        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        if (!this.dataType.isInstance(this.defaultValue)) {
            throw new IllegalArgumentException(
                    "Default value type mismatch for '" + ymlPath + "': expected " +
                            this.dataType.getSimpleName() + ", got " + this.defaultValue.getClass().getSimpleName()
            );
        }

        this.data = this.defaultValue;
    }

    public void setValue(T value) {
        validateValue(value);
        this.data = value;
    }

    /** Validates without changing the active value. Custom nodes should extend this method. */
    public void validateValue(T value) {
        Objects.requireNonNull(value, "node data");

        if (!dataType.isInstance(value)) {
            throw new IllegalArgumentException(
                    "Wrong type for '" + ymlPath + "': expected " +
                            dataType.getSimpleName() + ", got " + value.getClass().getSimpleName()
            );
        }

        for (Rule<T> rule : rules) {
            if (!rule.test.test(value)) {
                throw new IllegalArgumentException("Invalid value for '" + ymlPath + "': " + rule.message);
            }
        }
    }

    /** Configure pure validation predicates during node construction, before sharing the node. */
    public ReloadableConfigNode<T> validateWith(Predicate<? super T> test, String message) {
        Objects.requireNonNull(test, "test");
        Objects.requireNonNull(message, "message");
        if (!test.test(defaultValue) || !test.test(data)) {
            throw new IllegalArgumentException("Current/default value for '" + ymlPath + "' violates: " + message);
        }
        rules.add(new Rule<>(test, message));
        return this;
    }

    /** Loads on startup, but subsequent file reloads retain the current active value. */
    public ReloadableConfigNode<T> restartRequired() {
        restartRequired = true;
        return this;
    }

    public boolean isRestartRequired() { return restartRequired; }

    // Used only after the handler has staged and validated every candidate value.
    final void applyValidatedValue(T value) {
        this.data = value;
    }

    public T getValue()               { return this.data; }
    public T getDefaultValue()        { return this.defaultValue; }
    public Class<?> getDataType()     { return this.dataType; }
    public String getYmlPath()        { return this.ymlPath; }
    public List<String> getComment()  { return this.comment; }

    private static Class<?> normalizeDataType(Class<?> clazz) {
        if (!clazz.isPrimitive()) return clazz;
        Class<?> wrapper = TypeUtil.getPrimitiveWrapper(clazz);
        if (wrapper == null) throw new IllegalArgumentException("Unsupported primitive: " + clazz);
        return wrapper;
    }

    @Contract("null -> !null")
    private static @NotNull List<String> commentsOf(String... comments) {
        if (comments == null || comments.length == 0) return List.of();
        return List.copyOf(Arrays.asList(comments));
    }

    @Contract("null -> !null")
    private static @NotNull List<String> commentsOf(List<String> comments) {
        if (comments == null || comments.isEmpty()) return List.of();
        return List.copyOf(comments);
    }

    @Contract("_, _, _ -> new")
    public static <T> @NotNull ReloadableConfigNode<T> of(
            String path,
            Class<T> type,
            T defaultValue
    ) {
        return new ReloadableConfigNode<>(path, type, defaultValue, List.of());
    }

    @Contract("_, _, _, _ -> new")
    public static <T> @NotNull ReloadableConfigNode<T> of(
            String path,
            Class<T> type,
            T defaultValue,
            String... comments
    ) {
        return new ReloadableConfigNode<>(path, type, defaultValue, commentsOf(comments));
    }

    @Contract("_, _, _, _ -> new")
    public static <T> @NotNull ReloadableConfigNode<T> of(
            String path,
            Class<T> type,
            T defaultValue,
            List<String> comments
    ) {
        return new ReloadableConfigNode<>(path, type, defaultValue, commentsOf(comments));
    }
}
