package com.earthpol.earthpollib.math;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomUtilTest {

    @RepeatedTest(20)
    void randomFloatStaysWithinRequestedBounds() {
        float value = RandomUtil.randomFloat(1.5f, 3.5f);
        assertTrue(value >= 1.5f);
        assertTrue(value < 3.5f);
    }

    @RepeatedTest(20)
    void randomDoubleStaysWithinRequestedBounds() {
        double value = RandomUtil.randomDouble(1.5, 3.5);
        assertTrue(value >= 1.5);
        assertTrue(value < 3.5);
    }

    @RepeatedTest(20)
    void randomIntStaysWithinRequestedBounds() {
        int value = RandomUtil.randomInt(2, 7);
        assertTrue(value >= 2);
        assertTrue(value < 7);
    }

    @Test
    void randomBooleanReturnsBooleanValues() {
        boolean first = RandomUtil.randomBoolean();
        boolean second = RandomUtil.randomBoolean();

        assertTrue(first || !first);
        assertFalse(second && !second);
    }
}
