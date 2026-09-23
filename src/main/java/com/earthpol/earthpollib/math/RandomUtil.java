package com.earthpol.earthpollib.math;

import java.util.Random;

public class RandomUtil {

    public static float randomFloat(float min, float max) {
        Random r = new Random();
        return r.nextFloat(min,max);
    }

    public static double randomDouble(double min, double max) {
        Random r = new Random();
        return r.nextDouble(min,max);
    }

    public static int randomInt(int min, int max) {
        Random r = new Random();
        return r.nextInt(min,max);
    }

    public static boolean randomBoolean(){
        Random r = new Random();
        return r.nextBoolean();
    }

}
