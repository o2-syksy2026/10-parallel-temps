package temps;

/**
 * Turns the bundled daily means into the long series the chart consumes.
 *
 * <p>
 * The files in {@code data/} hold one year of real daily means (366 values).
 * A full run replays values from that seed {@link #LIVE_ROWS} times. The order
 * is not the calendar order: warm days come first, then cold days, then a
 * shorter recovery, and the rest of the series cycles through every day in the
 * file. The running average therefore starts high, drops, and settles near the
 * climate of the file. Helsinki uses a wider swing than Las Palmas.
 *
 * <p>
 * Each live reading also does a fixed amount of arithmetic so both workers
 * together take about fifteen seconds of wall-clock time. Tests ask for fewer
 * rows and skip that work. Workers do not sleep.
 */
public final class TemperatureSeries {

    /** Readings each city processes in {@code ./gradlew run}. */
    public static final int LIVE_ROWS = 1_500_000;

    /**
     * Arithmetic steps folded into one live reading. Tuned so two workers
     * running at the same time finish a {@link #LIVE_ROWS} series in about
     * fifteen seconds of wall-clock time on a normal laptop.
     */
    static final int WORK_PER_READING = 470;

    /** Share of the warmest and coldest days used for the opening swing. */
    private static final double EXTREME_SHARE = 0.12;

    private static volatile double sink;

    private TemperatureSeries() {
    }

    /**
     * Compiles a seed into the bins the stream cycles through.
     *
     * @param seed      daily means from one city file, in file order
     * @param helsinki  {@code true} for the wider Helsinki swing
     * @return a recipe that can answer {@link Recipe#celsius(int, int)}
     */
    public static Recipe compile(double[] seed, boolean helsinki) {
        return new Recipe(seed, helsinki);
    }

    /**
     * Extra arithmetic for one live reading. The result is retained so the
     * runtime cannot delete the loop. This is not a sleep.
     *
     * @param celsius the reading just produced
     */
    static void fold(double celsius) {
        double x = celsius;
        for (int i = 0; i < WORK_PER_READING; i++) {
            x = Math.fma(Math.sin(x), 0.5, Math.cos(x * 0.5));
        }
        sink = x;
    }

    /**
     * The order in which one city's daily means are replayed.
     */
    public static final class Recipe {

        private final double[][] bins;
        private final double[] ends;
        private final double seedMean;
        private final double[] seed;

        private Recipe(double[] seed, boolean helsinki) {
            this.seed = seed.clone();
            double[] sorted = seed.clone();
            java.util.Arrays.sort(sorted);
            int extreme = Math.max(1, (int) Math.round(sorted.length * EXTREME_SHARE));
            double[] warm = java.util.Arrays.copyOfRange(sorted, sorted.length - extreme, sorted.length);
            double[] cold = java.util.Arrays.copyOfRange(sorted, 0, extreme);
            double mean = mean(seed);
            double warmCut = warm[0];
            double[] recover = recover(seed, mean, warmCut, warm);
            double[] cycle = cycle(seed, mean);
            if (helsinki) {
                // High, low, back up, down again, then the whole year on a loop.
                this.bins = new double[][] { warm, cold, recover, cold, recover, cycle };
                this.ends = new double[] { 0.08, 0.20, 0.32, 0.42, 0.55, 1.0 };
            } else {
                // Same shape, shorter extremes, so the bar moves less.
                this.bins = new double[][] { warm, cold, recover, cycle };
                this.ends = new double[] { 0.06, 0.16, 0.28, 1.0 };
            }
            this.seedMean = mean;
        }

        /**
         * Returns the reading at {@code index} in a series of {@code total} rows.
         * Every returned value is one of the seed days.
         *
         * @param index zero-based position in the series
         * @param total length of the series
         * @return temperature in degrees Celsius
         */
        public double celsius(int index, int total) {
            if (total <= 0 || seed.length == 0) {
                return 0;
            }
            double progress = index / (double) total;
            for (int phase = 0; phase < ends.length; phase++) {
                if (progress < ends[phase] || phase == ends.length - 1) {
                    double[] bin = bins[phase];
                    return bin[Math.floorMod(index, bin.length)];
                }
            }
            return seedMean;
        }

        /**
         * Returns the arithmetic mean of the seed file.
         *
         * @return the mean of the daily means, in degrees Celsius
         */
        public double seedMean() {
            return seedMean;
        }

        /**
         * Returns a copy of the seed values in file order.
         *
         * @return the daily means
         */
        public double[] seed() {
            return seed.clone();
        }

        private static double[] recover(double[] seed, double mean, double warmCut, double[] warm) {
            int count = 0;
            for (double value : seed) {
                if (value >= mean && value < warmCut) {
                    count++;
                }
            }
            if (count == 0) {
                return warm.clone();
            }
            double[] values = new double[count];
            int index = 0;
            for (double value : seed) {
                if (value >= mean && value < warmCut) {
                    values[index++] = value;
                }
            }
            return values;
        }

        private static double[] cycle(double[] seed, double mean) {
            double[] above = filter(seed, mean, true);
            double[] below = filter(seed, mean, false);
            java.util.Arrays.sort(above);
            reverse(above);
            java.util.Arrays.sort(below);
            double[] cycle = new double[above.length + below.length];
            int aboveIndex = 0;
            int belowIndex = 0;
            int index = 0;
            while (aboveIndex < above.length || belowIndex < below.length) {
                if (aboveIndex < above.length) {
                    cycle[index++] = above[aboveIndex++];
                }
                if (belowIndex < below.length) {
                    cycle[index++] = below[belowIndex++];
                }
            }
            return cycle;
        }

        private static double[] filter(double[] seed, double mean, boolean atOrAbove) {
            int count = 0;
            for (double value : seed) {
                if ((value >= mean) == atOrAbove) {
                    count++;
                }
            }
            double[] values = new double[count];
            int index = 0;
            for (double value : seed) {
                if ((value >= mean) == atOrAbove) {
                    values[index++] = value;
                }
            }
            return values;
        }

        private static void reverse(double[] values) {
            for (int i = 0, j = values.length - 1; i < j; i++, j--) {
                double swap = values[i];
                values[i] = values[j];
                values[j] = swap;
            }
        }

        private static double mean(double[] values) {
            double sum = 0;
            for (double value : values) {
                sum += value;
            }
            return values.length == 0 ? 0 : sum / values.length;
        }
    }
}
