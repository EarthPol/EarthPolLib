package com.earthpol.earthpollib.string;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Small, allocation-conscious helpers for working with {@link String}s.
 *
 * <p>This class is a utility holder: it is {@code final}, has a private constructor,
 * and exposes only {@code static} methods. All methods are thread-safe and have no
 * external side effects.</p>
 *
 * @since 1.5.0
 */
@SuppressWarnings("unused")
public final class StringUtil {
    private StringUtil() {}

    /**
     * Splits a comma-separated string into tokens.
     *
     * <p><strong>Behavior:</strong></p>
     * <ul>
     *   <li>Splits on literal commas ({@code ","}); there is no CSV quoting/escaping.</li>
     *   <li>Trims leading/trailing ASCII whitespace around each token.</li>
     *   <li>Preserves empty fields (e.g. {@code "a,,b,"} → {@code ["a", "", "b", ""]}).</li>
     *   <li>Returns an <em>unmodifiable</em> list. If {@code input} is {@code null} or empty,
     *       returns {@link List#of()}.</li>
     * </ul>
     *
     * <p><strong>Examples:</strong></p>
     * <pre>{@code
     * splitByComma("  a, b , ,c ")  // → ["a", "b", "", "c"]
     * splitByComma("")              // → []
     * splitByComma(null)            // → []
     * }</pre>
     *
     * <p><strong>Complex CSV:</strong> If you need RFC-compliant CSV handling
     * (embedded commas, quotes, escapes), use a dedicated CSV library instead.</p>
     *
     * @param input comma-separated text; may be {@code null}
     * @return an unmodifiable list of trimmed tokens (never {@code null})
     * @implNote Complexity is O(n) in the length of {@code input}.
     */
    @Contract("null -> !null")
    public static @NotNull List<String> splitByComma(String input) {
        if (input == null || input.isEmpty()) return List.of();
        return Arrays.stream(input.split(",", -1))
                .map(String::trim)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Parses a single character from a string after trimming.
     *
     * <p>Trims the input using {@link #trim(String)} and requires the result to be
     * exactly one UTF-16 {@code char} in length. If the trimmed string is not length 1,
     * an exception is thrown.</p>
     *
     * <p><strong>Note about Unicode:</strong> This method returns a single
     * UTF-16 {@code char}. Supplementary characters (code points &gt; U+FFFF) are
     * represented by a surrogate pair (length 2) and will therefore be rejected.</p>
     *
     * <p><strong>Examples:</strong></p>
     * <pre>{@code
     * parseCharStrict(" X ") // → 'X'
     * parseCharStrict("")    // throws IllegalArgumentException
     * parseCharStrict("OK")  // throws IllegalArgumentException
     * }</pre>
     *
     * @param s the source string; must not be {@code null}
     * @return the single trimmed character
     * @throws IllegalArgumentException if {@code s} is {@code null} or the trimmed value
     *                                  is not exactly one character long
     */
    @Contract(pure = true, value = "null -> fail")
    public static @NotNull Character parseCharStrict(String s) {
        if (s == null) throw new IllegalArgumentException("Null string.");
        String t = trim(s);
        if (t.length() != 1) {
            throw new IllegalArgumentException("Expected single character, got: \"" + s + "\"");
        }
        return t.charAt(0);
    }

    /**
     * Null-checking wrapper around {@link String#trim()}.
     *
     * <p>Unlike {@code String.valueOf}, this method does <em>not</em> accept {@code null}:
     * it throws an exception, which helps catch bugs earlier when a string is required.</p>
     *
     * @param s the string to trim; must not be {@code null}
     * @return {@code s.trim()}
     * @throws IllegalArgumentException if {@code s} is {@code null}
     */
    @Contract("null -> fail")
    public static @NotNull String trim(String s) {
        if (s == null) throw new IllegalArgumentException("Null string");
        return s.trim();
    }

    /**
     * Null-safe {@code toString} that returns {@code null} for {@code null} input.
     *
     * <p>Semantics are similar to {@link String#valueOf(Object)} except for how
     * {@code null} is handled:</p>
     * <ul>
     *   <li>{@code String.valueOf(null)} → the literal string {@code "null"}</li>
     *   <li>{@code stringify(null)} → {@code null}</li>
     * </ul>
     *
     * <p>This is useful when converting YAML nodes or other raw values where
     * distinguishing “missing” ({@code null}) from the literal text {@code "null"}
     * matters.</p>
     *
     * @param raw the value to stringify; may be {@code null}
     * @return {@code null} if {@code raw} is {@code null}, otherwise {@code String.valueOf(raw)}
     */
    public static @Nullable String stringify(Object raw) {
        return (raw == null) ? null : String.valueOf(raw);
    }

    /**
     * Converts an arbitrary list into a list of strings, skipping {@code null} elements.
     *
     * <p>Order is preserved. The returned list is unmodifiable. Each non-null element is
     * converted via {@link String#valueOf(Object)} (so arrays will use their default
     * {@code toString} form, not a deep representation).</p>
     *
     * <p><strong>Examples:</strong></p>
     * <pre>{@code
     * stringifyList(List.of(1, true, "x"))   // → ["1", "true", "x"]
     * stringifyList(List.of("a", null, "b")) // → ["a", "b"]
     * stringifyList(null)                    // → []
     * }</pre>
     *
     * @param raw a list whose elements may be of any type (or {@code null})
     * @return an unmodifiable list of stringified elements (never {@code null})
     */
    public static @NotNull List<String> stringifyList(List<?> raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        List<String> out = new ArrayList<>(raw.size());
        for (Object o : raw) {
            if (o != null) out.add(String.valueOf(o));
        }
        return List.copyOf(out);
    }

    @Contract(pure = true)
    public static @NotNull String stripLegacyColorCodes(@NotNull String in) {
        // Remove all occurrences of § and the char after it (works for standard & hex styles)
        return in.replaceAll("§.", "");
    }

    public static @NotNull String sanitize(@NotNull String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    }

}
