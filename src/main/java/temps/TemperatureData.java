package temps;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads the bundled daily temperature files and expands them into the series
 * the workers consume.
 *
 * <p>
 * {@link #LAS_PALMAS} and {@link #HELSINKI} are the Open-Meteo 2024 daily means
 * in {@code data/}. {@link #open(Path, long, int)} replays those days in the
 * order documented in {@code data/README.md}. A full-length series also folds
 * extra arithmetic into each reading so the live chart lasts long enough to
 * watch. A shorter series, which is what the tests use, skips that work.
 *
 * <p>
 * The pause belongs to the feeder thread inside this class. A
 * {@link TemperatureWorker} never sleeps: it only calls
 * {@link TemperatureSource#next()}.
 */
public final class TemperatureData {

    /** Daily means for Las Palmas, year 2024. Do not modify the file. */
    public static final Path LAS_PALMAS = Path.of("data/las-palmas.csv");

    /** Daily means for Helsinki, year 2024. Do not modify the file. */
    public static final Path HELSINKI = Path.of("data/helsinki.csv");

    private static final ConcurrentHashMap<Path, TemperatureSeries.Recipe> RECIPES = new ConcurrentHashMap<>();

    private TemperatureData() {
    }

    /**
     * Reads the temperature column from a CSV file.
     *
     * <p>
     * Lines that are empty, start with {@code #}, or are the header are ignored.
     * Each remaining line is {@code date,temperature_c}. This is the seed, not
     * the long chart series.
     *
     * @param csvFile the CSV file to read
     * @return the temperature values in file order, in degrees Celsius
     * @throws IOException if the file cannot be read
     */
    public static double[] readValues(Path csvFile) throws IOException {
        List<Double> values = new ArrayList<>();
        for (String line : Files.readAllLines(csvFile, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("date")) {
                continue;
            }
            String[] columns = trimmed.split(",");
            values.add(Double.parseDouble(columns[1]));
        }
        double[] result = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    /**
     * Expands a city file into the series the chart consumes.
     *
     * @param csvFile the bundled CSV file
     * @param rows    how many readings to produce
     * @return readings in chart order, in degrees Celsius
     * @throws IOException if the file cannot be read
     */
    public static double[] stream(Path csvFile, int rows) throws IOException {
        TemperatureSeries.Recipe recipe = recipe(csvFile);
        double[] values = new double[Math.max(rows, 0)];
        for (int i = 0; i < values.length; i++) {
            values[i] = recipe.celsius(i, values.length);
        }
        return values;
    }

    /**
     * Opens the full live series for one city.
     *
     * @param csvFile         the bundled CSV file
     * @param feedDelayMillis pause between readings. {@code 0} delivers every
     *                        reading as fast as the worker can fold it, without
     *                        an extra feeder thread. A positive value starts a
     *                        feeder thread that publishes one reading at a time.
     * @return a source the city worker can consume
     * @throws IOException if the file cannot be read
     */
    public static TemperatureSource open(Path csvFile, long feedDelayMillis) throws IOException {
        return open(csvFile, feedDelayMillis, TemperatureSeries.LIVE_ROWS);
    }

    /**
     * Opens a series of {@code rows} readings. Fewer than
     * {@link TemperatureSeries#LIVE_ROWS} rows skip the per-reading arithmetic,
     * which is how the tests stay fast.
     *
     * @param csvFile         the bundled CSV file
     * @param feedDelayMillis pause between readings in the feeder
     * @param rows            how many readings the source will produce
     * @return a source the city worker can consume
     * @throws IOException if the file cannot be read
     */
    public static TemperatureSource open(Path csvFile, long feedDelayMillis, int rows) throws IOException {
        TemperatureSeries.Recipe recipe = recipe(csvFile);
        boolean paced = rows >= TemperatureSeries.LIVE_ROWS;
        if (feedDelayMillis <= 0) {
            return new GeneratedSource(recipe, rows, paced);
        }
        QueueTemperatureSource queued = new QueueTemperatureSource(rows);
        Thread feeder = new Thread(() -> feed(queued, recipe, rows, paced, feedDelayMillis), "temperature-feeder");
        feeder.setDaemon(true);
        feeder.start();
        return queued;
    }

    private static TemperatureSeries.Recipe recipe(Path csvFile) throws IOException {
        TemperatureSeries.Recipe cached = RECIPES.get(csvFile);
        if (cached != null) {
            return cached;
        }
        double[] seed = readValues(csvFile);
        TemperatureSeries.Recipe compiled = TemperatureSeries.compile(seed, HELSINKI.equals(csvFile));
        TemperatureSeries.Recipe existing = RECIPES.putIfAbsent(csvFile, compiled);
        return existing == null ? compiled : existing;
    }

    private static void feed(QueueTemperatureSource queued, TemperatureSeries.Recipe recipe, int rows,
            boolean paced, long feedDelayMillis) {
        try {
            for (int i = 0; i < rows; i++) {
                double value = recipe.celsius(i, rows);
                if (paced) {
                    TemperatureSeries.fold(value);
                }
                if (feedDelayMillis > 0) {
                    Thread.sleep(feedDelayMillis);
                }
                queued.publish(value);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            queued.close();
        }
    }

    /**
     * Readings generated from a compiled seed.
     */
    static final class GeneratedSource implements TemperatureSource {

        private final TemperatureSeries.Recipe recipe;
        private final int rows;
        private final boolean paced;
        private int index;

        GeneratedSource(TemperatureSeries.Recipe recipe, int rows, boolean paced) {
            this.recipe = recipe;
            this.rows = rows;
            this.paced = paced;
        }

        @Override
        public synchronized OptionalDouble next() {
            if (index >= rows) {
                return OptionalDouble.empty();
            }
            double value = recipe.celsius(index, rows);
            index++;
            if (paced) {
                TemperatureSeries.fold(value);
            }
            return OptionalDouble.of(value);
        }

        @Override
        public int size() {
            return rows;
        }
    }
}
