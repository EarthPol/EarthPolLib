package com.earthpol.earthpollib.config;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A minimal interface intended to be implemented by <strong>enums</strong> that declare
 * configuration keys. Each enum constant is associated with exactly one
 * {@link ReloadableConfigNode} instance holding the path, default value, type
 * information, and the current in-memory value.
 *
 * <h2>How to implement</h2>
 * Implementing enums must include a tiny boilerplate field, constructor, and accessor
 * to bind each constant to its node:
 *
 * <pre>{@code
 * public enum MyConfig implements ReloadableConfiguration {
 *     FOO(ReloadableConfigNode.of("foo", Integer.class, 42, "Example comment")),
 *     BAR(ReloadableConfigNode.of("bar", String.class, "hello"));
 *
 *     private final ReloadableConfigNode<?> node;
 *     MyConfig(ReloadableConfigNode<?> node) { this.node = node; }
 *     @Override public ReloadableConfigNode<?> node() { return node; }
 * }
 * }</pre>
 *
 * <h2>Reading &amp; Writing</h2>
 * <ul>
 *   <li>Use {@link #getValue(Class)} for <em>typed</em> reads. This method enforces that the
 *   requested type matches the node's declared type (after primitive-to-wrapper normalization)
 *   and will throw if it does not.</li>
 *   <li>Use {@link #getRaw()} only if you explicitly want the untyped value (as {@code Object}).</li>
 *   <li>Use {@link #setValue(Object)} to update the in-memory value; the node itself enforces
 *   non-null and type safety. Persisting to disk is handled by your configuration handler
 *   (e.g., calling {@code save()} on whatever component manages the YAML file).</li>
 * </ul>
 *
 * <h2>Primitive normalization</h2>
 * When reading typed values via {@link #getValue(Class)}, primitive types are normalized to
 * their wrapper classes (e.g., {@code int.class} → {@code Integer.class}, {@code float.class}
 * → {@code Float.class}). Callers may therefore pass either the primitive class or the wrapper
 * class for the same effect.
 *
 * <h2>Thread-safety</h2>
 * Instances of {@link ReloadableConfigNode} are mutable and not inherently thread-safe.
 * If you access or mutate configuration off the main thread, coordinate appropriately.
 *
 * @since 1.5.0
 */
@SuppressWarnings("unused")
public interface ReloadableConfiguration {

    ReloadableConfigNode<?> node();

    default String getPath() { return node().getYmlPath(); }

    default Class<?> getType() { return node().getDataType(); }

    default Object getRaw() { return node().getValue(); }

    default <T> T getValue(@NotNull Class<T> requested) {
        Class<T> expected = TypeUtil.normalize(requested);
        Class<?> declared = node().getDataType();
        if (declared != expected) {
            throw new ClassCastException("Config key '" + getPath()
                    + "' declared as " + declared.getSimpleName()
                    + " but requested as " + expected.getSimpleName());
        }
        return expected.cast(node().getValue());
    }

    @SuppressWarnings("unchecked")
    default <T> void setValue(T value) {
        ((ReloadableConfigNode<Object>) node()).setValue(value);
    }

    default int getInt() { return getValue(Integer.class); }

    default long getLong() { return getValue(Long.class); }

    default double getDouble() { return getValue(Double.class); }

    default float getFloat() { return getValue(Float.class); }

    default boolean getBool() { return getValue(Boolean.class); }

    default char getChar() { return getValue(Character.class); }

    default byte getByte() { return getValue(Byte.class); }

    default String getString() { return getValue(String.class); }

    default List<?> getList(){
        if (node() instanceof ReloadableListNode<?> listNode) {
            return listNode.getValue();
        }
        throw new ClassCastException("Config key '" + getPath() + "' is not a list");
    }
}
