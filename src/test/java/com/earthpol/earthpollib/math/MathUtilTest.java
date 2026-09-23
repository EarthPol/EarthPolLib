package com.earthpol.earthpollib.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MathUtilTest {

    @Test
    void percentToScalarConvertsWholePercentages() {
        assertEquals(0.25, MathUtil.percentToScalar(25.0));
    }

    @Test
    void reductionPercentageReturnsRemainingScalar() {
        assertEquals(0.8, MathUtil.reductionPercentage(20.0));
    }

    @Test
    void increasePercentageReturnsBoostedScalar() {
        assertEquals(1.2, MathUtil.increasePercentage(20.0));
    }
}
