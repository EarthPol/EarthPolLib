package com.earthpol.earthpollib.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigValidatorsTest {
    @Test
    void rangesPreserveLongPrecisionAndRejectInvalidNumbers() {
        var range = ConfigValidators.range(Long.MAX_VALUE - 1, Long.MAX_VALUE);
        assertTrue(range.test(Long.MAX_VALUE));
        assertTrue(range.test(Long.MAX_VALUE - 1));
        assertFalse(range.test(Long.MAX_VALUE - 2));
        assertFalse(ConfigValidators.range(0.0, 1.0).test(Double.NaN));
        assertFalse(ConfigValidators.range(0.0, 1.0).test(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> ConfigValidators.range(10, 1));
    }

    @Test
    void allowedValuesAreCopiedAndInvalidDefaultsCannotInstallARule() {
        List<String> allowed = new ArrayList<>(List.of("safe"));
        var predicate = ConfigValidators.oneOf(allowed);
        allowed.add("other");
        assertFalse(predicate.test("other"));
        assertFalse(ConfigValidators.nonBlank().test("  "));
        var node = ReloadableConfigNode.of("count", Integer.class, 3);
        assertThrows(IllegalArgumentException.class,
                () -> node.validateWith(ConfigValidators.range(5, 10), "must be 5 through 10"));
        node.setValue(4);
        assertEquals(4, node.getValue());
    }
}
