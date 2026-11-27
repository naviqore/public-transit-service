package org.naviqore.benchmark.timing;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The ratio of the times of a candidate and a baseline, measured in rounds in two load orders: the geometric mean of
 * the ratios of the rounds of each order, and of the two orders, so that what the load order does to one version
 * cancels out. Its 95 % confidence interval comes from a bootstrap: the rounds of each order resampled with
 * replacement, many times.
 *
 * @param estimate the ratio candidate / baseline; below 1 the candidate is faster
 * @param lower    the lower bound of the 95 % confidence interval
 * @param upper    the upper bound of the 95 % confidence interval
 */
public record Ratio(double estimate, double lower, double upper) {

    private static final int RESAMPLES = 10_000;
    private static final double CONFIDENCE = 0.95;
    private static final long SEED = 42;

    /**
     * The ratio of the rounds of two load orders, each round given by its ratio candidate / baseline. An order without
     * rounds leaves the other alone.
     */
    public static Ratio of(List<Double> firstOrder, List<Double> secondOrder) {
        double[] first = logarithms(firstOrder);
        double[] second = logarithms(secondOrder);
        if (first.length == 0 && second.length == 0) {
            throw new IllegalArgumentException("No rounds");
        }

        Random random = new Random(SEED);
        double[] estimates = new double[RESAMPLES];
        for (int i = 0; i < RESAMPLES; i++) {
            estimates[i] = combine(resampledMean(first, random), resampledMean(second, random));
        }
        Arrays.sort(estimates);
        int tail = (int) Math.round(RESAMPLES * (1 - CONFIDENCE) / 2);

        return new Ratio(Math.exp(combine(mean(first), mean(second))), Math.exp(estimates[tail]),
                Math.exp(estimates[RESAMPLES - 1 - tail]));
    }

    private static double[] logarithms(List<Double> ratios) {
        return ratios.stream().mapToDouble(Math::log).toArray();
    }

    private static double mean(double[] values) {
        return values.length == 0 ? Double.NaN : Arrays.stream(values).average().orElseThrow();
    }

    private static double resampledMean(double[] values, Random random) {
        if (values.length == 0) {
            return Double.NaN;
        }
        double sum = 0;
        for (int i = 0; i < values.length; i++) {
            sum += values[random.nextInt(values.length)];
        }

        return sum / values.length;
    }

    // the mean of both orders, or the one order there is
    private static double combine(double first, double second) {
        if (Double.isNaN(first)) {
            return second;
        }
        if (Double.isNaN(second)) {
            return first;
        }

        return (first + second) / 2;
    }

    /**
     * Whether the interval excludes 1: the versions differ beyond what the rounds vary.
     */
    public boolean isSignificant() {
        return upper < 1 || lower > 1;
    }

    /**
     * {@code faster} or {@code slower} if the ratio is significant, {@code ~} if it is not.
     */
    public String verdict() {
        if (!isSignificant()) {
            return "~";
        }

        return upper < 1 ? "faster" : "slower";
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%.3f [%.3f, %.3f] %s", estimate, lower, upper, verdict());
    }

}
