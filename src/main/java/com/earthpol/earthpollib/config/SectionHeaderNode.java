package com.earthpol.earthpollib.config;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * Marker node used to emit comments above a YAML section (mapping) without storing a value.
 *
 * <p>Use this to document parent keys like {@code "mySection:"}. Builders should create the
 * section and attach comments; loaders should skip parsing this node type.</p>
 *
 * <p>Example:</p>
 * <pre>{@code
 * SECTION_HEADER_mytest(SectionHeaderNode.of("mytest", "This section", "does a thing.")),
 * MYTEST_ENABLED(ReloadableConfigNode.of("mytest.enabled", Boolean.class, true))
 * }</pre>
 */
@SuppressWarnings("unused")
public final class SectionHeaderNode extends ReloadableConfigNode<String> {

    private SectionHeaderNode(String path, List<String> comments) {
        super(path, String.class, "", comments == null ? List.of() : List.copyOf(comments));
    }

    public static @NotNull SectionHeaderNode of(String path, String... comments) {
        List<String> lines = (comments == null || comments.length == 0)
                ? List.of()
                : List.of(comments);
        return new SectionHeaderNode(Objects.requireNonNull(path, "path"), lines);
    }

    public static @NotNull SectionHeaderNode of(String path, List<String> comments) {
        return new SectionHeaderNode(Objects.requireNonNull(path, "path"),
                comments == null ? List.of() : List.copyOf(comments));
    }

}
