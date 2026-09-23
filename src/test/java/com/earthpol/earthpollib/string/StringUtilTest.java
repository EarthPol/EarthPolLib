package com.earthpol.earthpollib.string;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StringUtilTest {

    @Test
    void splitByCommaTrimsAndPreservesEmptyFields() {
        assertEquals(List.of("a", "b", "", "c"), StringUtil.splitByComma("  a, b , ,c "));
    }

    @Test
    void splitByCommaReturnsEmptyListForNullInput() {
        assertEquals(List.of(), StringUtil.splitByComma(null));
    }

    @Test
    void parseCharStrictReturnsSingleTrimmedCharacter() {
        assertEquals('X', StringUtil.parseCharStrict(" X "));
    }

    @Test
    void parseCharStrictRejectsMultiCharacterValues() {
        assertThrows(IllegalArgumentException.class, () -> StringUtil.parseCharStrict("OK"));
    }

    @Test
    void trimRejectsNullValues() {
        assertThrows(IllegalArgumentException.class, () -> StringUtil.trim(null));
    }

    @Test
    void stringifyPreservesNull() {
        assertNull(StringUtil.stringify(null));
    }

    @Test
    void stringifyListSkipsNullElements() {
        assertEquals(List.of("1", "true", "x"), StringUtil.stringifyList(List.of(1, true, "x")));
        assertEquals(List.of("a", "b"), StringUtil.stringifyList(Arrays.asList("a", null, "b")));
    }

    @Test
    void stripLegacyColorCodesRemovesSectionCodes() {
        assertEquals("Hello World", StringUtil.stripLegacyColorCodes("§aHello §bWorld"));
    }

    @Test
    void sanitizeNormalizesToLowercaseAndUnderscores() {
        assertEquals("hello_world_", StringUtil.sanitize("Hello World!"));
    }
}
