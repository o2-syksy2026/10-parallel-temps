package temps;

import java.io.IOException;
import java.util.Locale;

/**
 * Draws two ASCII temperature bars while Las Palmas and Helsinki temperatures
 * are averaged on two threads.
 *
 * <p>
 * Only this main thread prints. Each {@link TemperatureWorker} updates its own
 * volatile {@code checked} counter and {@code runningAverage}. This class reads
 * those fields, draws both bars through {@link #showProgress}, and sleeps
 * between frames. The bar length is the running average on a fixed scale, not
 * the share of readings processed. After both workers have been joined, it
 * draws the final frame.
 */
public class Main {

    /** Number of characters in one temperature bar. */
    public static final int BAR_WIDTH = 50;

    /**
     * Cold end of the shared scale, in degrees Celsius. An average this low
     * fills none of the bar.
     */
    public static final double SCALE_MIN = -10.0;

    /**
     * Warm end of the shared scale, in degrees Celsius. An average this high
     * fills the whole bar. The span is 40 degrees over {@link #BAR_WIDTH}
     * characters, so each character is 0.8 °C.
     */
    public static final double SCALE_MAX = 30.0;

    /**
     * Builds one frame with two temperature bars.
     *
     * <p>
     * The bar length is the running average on the scale
     * {@link #SCALE_MIN}…{@link #SCALE_MAX}, not how many readings are done.
     * Until the first reading ({@code checked == 0}) the bar is empty. The row
     * number and the percent processed are printed after the temperature. The
     * main thread is the only caller; workers do not print.
     *
     * <p>
     * Each line is exactly 80 characters when the city name fits in 10
     * characters and the average fits in five:
     *
     * <pre>
     * Las Palmas |--------------------------------------------------|  0.0°C    0   0%
     * Helsinki   |#####################################-------------| 19.3°C 120k   8%
     * </pre>
     *
     * The city name is padded to 10 characters. The bar is {@link #BAR_WIDTH}
     * characters between two {@code |} marks. Row counts from 1000 upward are
     * printed in thousands ({@code 120k}) and from one million upward with one
     * decimal ({@code 1.5M}). The percent is {@code checked * 100 / total}.
     * A total of {@code 0} is 0%. The average uses one decimal and a dot
     * ({@link Locale#ROOT}).
     *
     * @param firstCity     label of the first bar
     * @param firstChecked  readings the first worker has processed
     * @param firstTotal    readings the first worker will process
     * @param firstAverage  running average of the first city, in degrees Celsius
     * @param secondCity    label of the second bar
     * @param secondChecked readings the second worker has processed
     * @param secondTotal   readings the second worker will process
     * @param secondAverage running average of the second city, in degrees Celsius
     * @return the two lines separated by {@code \n}, without a trailing newline
     *         and without terminal control codes
     */
    public static String showProgress(
            String firstCity, int firstChecked, int firstTotal, double firstAverage,
            String secondCity, int secondChecked, int secondTotal, double secondAverage) {
        return line(firstCity, firstChecked, firstTotal, firstAverage)
                + "\n"
                + line(secondCity, secondChecked, secondTotal, secondAverage);
    }

    /**
     * Maps a running average onto the bar.
     *
     * <p>
     * {@link #SCALE_MIN} and below is 0. {@link #SCALE_MAX} and above is
     * {@link #BAR_WIDTH}. Values in between are rounded to the nearest length.
     * This does not know whether any reading has arrived yet; {@link #showProgress}
     * keeps the bar empty when {@code checked} is 0.
     *
     * @param celsius running average in degrees Celsius
     * @return filled length, from 0 to {@link #BAR_WIDTH}
     */
    public static int temperatureBarLength(double celsius) {
        if (celsius <= SCALE_MIN) {
            return 0;
        }
        if (celsius >= SCALE_MAX) {
            return BAR_WIDTH;
        }
        double fraction = (celsius - SCALE_MIN) / (SCALE_MAX - SCALE_MIN);
        int filled = (int) Math.round(fraction * BAR_WIDTH);
        if (filled < 0) {
            return 0;
        }
        if (filled > BAR_WIDTH) {
            return BAR_WIDTH;
        }
        return filled;
    }

    /**
     * Runs the full live chart. Both cities process
     * {@link TemperatureSeries#LIVE_ROWS} readings, which takes about fifteen
     * seconds of wall-clock time.
     *
     * @param redrawDelayMillis pause between frames, in milliseconds
     * @param feedDelayMillis   pause between readings handed to the workers, in
     *                          milliseconds. {@code 0} does not add a feeder delay
     * @return the final frame, drawn after both workers have been joined
     * @throws IOException          if a temperature file cannot be read
     * @throws InterruptedException if the sleep or a join is interrupted
     */
    public static String runChart(long redrawDelayMillis, long feedDelayMillis)
            throws IOException, InterruptedException {
        return runChart(redrawDelayMillis, feedDelayMillis, TemperatureSeries.LIVE_ROWS);
    }

    /**
     * Loads both cities, runs one worker thread for each, and returns the final
     * frame.
     *
     * <p>
     * {@code rows} is the series length. The tests pass a few thousand so they
     * stay fast. {@code feedDelayMillis} is the pause between readings in the
     * feeder (not in the worker). {@code redrawDelayMillis} is how long the main
     * thread sleeps between frames.
     *
     * @param redrawDelayMillis pause between frames, in milliseconds
     * @param feedDelayMillis   pause between readings handed to the workers, in
     *                          milliseconds
     * @param rows              readings each worker processes
     * @return the final frame, drawn after both workers have been joined
     * @throws IOException          if a temperature file cannot be read
     * @throws InterruptedException if the sleep or a join is interrupted
     */
    public static String runChart(long redrawDelayMillis, long feedDelayMillis, int rows)
            throws IOException, InterruptedException {
        TemperatureSource lasPalmasSource = TemperatureData.open(TemperatureData.LAS_PALMAS, feedDelayMillis, rows);
        TemperatureSource helsinkiSource = TemperatureData.open(TemperatureData.HELSINKI, feedDelayMillis, rows);

        TemperatureWorker lasPalmas = new TemperatureWorker("Las Palmas", lasPalmasSource);
        TemperatureWorker helsinki = new TemperatureWorker("Helsinki", helsinkiSource);
        lasPalmas.start();
        helsinki.start();

        while (lasPalmas.isAlive() || helsinki.isAlive()) {
            System.out.println(showProgress(
                    lasPalmas.getCity(), lasPalmas.checked, lasPalmasSource.size(), lasPalmas.runningAverage,
                    helsinki.getCity(), helsinki.checked, helsinkiSource.size(), helsinki.runningAverage));
            Thread.sleep(redrawDelayMillis);
        }

        lasPalmas.join();
        helsinki.join();

        String finalFrame = showProgress(
                lasPalmas.getCity(), lasPalmas.checked, lasPalmasSource.size(), lasPalmas.average(),
                helsinki.getCity(), helsinki.checked, helsinkiSource.size(), helsinki.average());
        System.out.println(finalFrame);
        return finalFrame;
    }

    /**
     * Starts the chart. The workers do the arithmetic; this thread only redraws.
     *
     * @param args ignored
     * @throws IOException          if a temperature file cannot be read
     * @throws InterruptedException if the sleep or a join is interrupted
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        runChart(50, 0);
    }

    private static String line(String city, int checked, int total, double average) {
        int filled = checked <= 0 ? 0 : temperatureBarLength(average);
        String bar = "#".repeat(filled) + "-".repeat(BAR_WIDTH - filled);
        return String.format(Locale.ROOT, "%-10s |%s|%5.1f°C %4s %3d%%",
                city, bar, average, rowLabel(checked), percent(checked, total));
    }

    private static String rowLabel(int checked) {
        if (checked >= 1_000_000) {
            return String.format(Locale.ROOT, "%.1fM", checked / 1_000_000.0);
        }
        if (checked >= 1_000) {
            return (checked / 1_000) + "k";
        }
        return Integer.toString(Math.max(checked, 0));
    }

    private static int percent(int checked, int total) {
        if (total <= 0 || checked <= 0) {
            return 0;
        }
        if (checked >= total) {
            return 100;
        }
        return (int) ((long) checked * 100 / total);
    }
}
