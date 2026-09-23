package com.earthpol.earthpollib.math;

import org.jetbrains.annotations.Contract;

/**
 * Math utilities for converting human-readable percentages into numeric forms
 * convenient for multiplicative math or APIs that accept scalar "amounts."
 */
@SuppressWarnings("unused")
public class MathUtil {

    @Contract(pure = true)
    public static double percentToScalar(double percent) {
        return percent / 100.0;
    }

    @Contract(pure = true)
    public static double reductionPercentage(double percent) {
        double truePercent =  percentToScalar(percent);
        return 1 - truePercent;
    }

    @Contract(pure = true)
    public static double increasePercentage(double percent) {
        double truePercent = percentToScalar(percent);
        return 1 + truePercent;
    }

}
