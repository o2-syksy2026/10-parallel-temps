package temps;

import java.io.IOException;
import java.util.Locale;

/**
 * Draws two ASCII progress bars while Las Palmas and Helsinki temperatures are
 * averaged on two threads.
 *
 * <p>
 * Only this main thread prints. Each {@link TemperatureWorker} updates its own
 * {@code checked} counter. This class reads those counters, draws both bars
 * through {@link #showProgress}, and sleeps between frames. After both workers
 * have been joined, it reads the averages and draws the final frame.
 */
public class Main {

    /** Number of characters in one progress bar. A full bar is 100%. */
    public static final int BAR_WIDTH = 20;

    /**
     * Builds one frame with two progress bars.
     *
     * <p>
     * The bar shows how large a share of that city's readings has been
     * processed, not how warm the city is. The average is printed after the
     * percentage. The main thread is the only caller; workers do not print.
     *
     * <p>
     * Each line looks like this ({@code #} is the processed share, {@code -} is
     * the remainder):
     *
     * <pre>
     * Las Palmas | ##########----------  50 %  19.7 °C
     * Helsinki   | ###-----------------  15 %  -5.9 °C
     * </pre>
     *
     * The city name is padded to 10 characters. The bar is {@link #BAR_WIDTH}
     * characters. The filled length is {@code checked * BAR_WIDTH / total}
     * (integer division), and the percentage is {@code checked * 100 / total}.
     * A total of {@code 0} is drawn as 0%. The average uses one decimal
     * and a dot as the decimal separator ({@link Locale#ROOT}).
     *
     * @param firstCity      label of the first bar
     * @param firstChecked   readings the first worker has processed
     * @param firstTotal     readings the first worker will process
     * @param firstAverage   average of the first city's processed readings, in
     *                       degrees Celsius
     * @param secondCity     label of the second bar
     * @param secondChecked  readings the second worker has processed
     * @param secondTotal    readings the second worker will process
     * @param secondAverage  average of the second city's processed readings, in
     *                       degrees Celsius
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
     * Loads both cities, runs one worker thread for each, and returns the final
     * progress frame.
     *
     * <p>
     * {@code feedDelayMillis} is the pause between readings in the feeder (not
     * in the worker). {@code 0} makes the bundled files available immediately,
     * which is what the tests use. {@code redrawDelayMillis} is how long the
     * main thread sleeps between frames.
     *
     * @param redrawDelayMillis pause between frames, in milliseconds
     * @param feedDelayMillis   pause between readings handed to the workers, in
     *                          milliseconds
     * @return the final frame, drawn after both workers have been joined
     * @throws IOException          if a temperature file cannot be read
     * @throws InterruptedException if the sleep or a join is interrupted
     */
    public static String runChart(long redrawDelayMillis, long feedDelayMillis)
            throws IOException, InterruptedException {
        TemperatureSource lasPalmasSource = TemperatureData.open(TemperatureData.LAS_PALMAS, feedDelayMillis);
        TemperatureSource helsinkiSource = TemperatureData.open(TemperatureData.HELSINKI, feedDelayMillis);

        TemperatureWorker lasPalmas = new TemperatureWorker("Las Palmas", lasPalmasSource);
        TemperatureWorker helsinki = new TemperatureWorker("Helsinki", helsinkiSource);
        lasPalmas.start();
        helsinki.start();

        while (lasPalmas.isAlive() || helsinki.isAlive()) {
            System.out.println(showProgress(
                    lasPalmas.getCity(), lasPalmas.checked, lasPalmasSource.size(), lasPalmas.average(),
                    helsinki.getCity(), helsinki.checked, helsinkiSource.size(), helsinki.average()));
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
     * Starts the chart with a visible pace: readings arrive gradually and the
     * main thread redraws a few times per second.
     *
     * @param args ignored
     * @throws IOException          if a temperature file cannot be read
     * @throws InterruptedException if the sleep or a join is interrupted
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        runChart(40, 15);
    }

    private static String line(String city, int checked, int total, double average) {
        int filled = filledWidth(checked, total);
        String bar = "#".repeat(filled) + "-".repeat(BAR_WIDTH - filled);
        return String.format(Locale.ROOT, "%-10s | %s %3d %% %5.1f °C", city, bar, percent(checked, total), average);
    }

    private static int filledWidth(int checked, int total) {
        if (total <= 0 || checked <= 0) {
            return 0;
        }
        if (checked >= total) {
            return BAR_WIDTH;
        }
        return (int) ((long) checked * BAR_WIDTH / total);
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
