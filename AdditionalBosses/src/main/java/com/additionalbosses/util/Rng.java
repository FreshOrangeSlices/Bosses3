package com.additionalbosses.util;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ToDoubleFunction;

/**
 * Random helpers. Chances are always written as percentages (2.5 = 2.5%).
 */
public final class Rng {

    private Rng() {
    }

    public static ThreadLocalRandom r() {
        return ThreadLocalRandom.current();
    }

    public static boolean chance(double percent) {
        if (percent <= 0) {
            return false;
        }
        if (percent >= 100) {
            return true;
        }
        return r().nextDouble(100.0) < percent;
    }

    public static int between(int min, int max) {
        if (max <= min) {
            return min;
        }
        return r().nextInt(min, max + 1);
    }

    public static double between(double min, double max) {
        if (max <= min) {
            return min;
        }
        return r().nextDouble(min, max);
    }

    public static <T> @Nullable T weighted(Map<T, ? extends Number> weights) {
        return weighted(weights.keySet(), key -> weights.get(key).doubleValue());
    }

    public static <T> @Nullable T weighted(Collection<T> options, ToDoubleFunction<T> weight) {
        double total = 0;
        for (T option : options) {
            total += Math.max(0, weight.applyAsDouble(option));
        }
        if (total <= 0) {
            return null;
        }
        double roll = r().nextDouble(total);
        for (T option : options) {
            double w = Math.max(0, weight.applyAsDouble(option));
            if (roll < w) {
                return option;
            }
            roll -= w;
        }
        return null;
    }
}
