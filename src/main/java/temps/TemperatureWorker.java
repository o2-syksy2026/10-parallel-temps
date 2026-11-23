package temps;

import java.util.OptionalDouble;

/**
 * Computes the average temperature of one city on its own thread.
 *
 * <p>
 * The worker publishes two volatile fields: {@link #checked} and
 * {@link #runningAverage}. The main thread reads both while this worker is
 * still running and draws from them. The worker does not print and does not
 * sleep. After {@link #join()} the same average is stable.
 */
public class TemperatureWorker extends Thread {

    /**
     * How many readings this worker has processed. The main thread reads this
     * while the worker is running in order to print the row and the percent.
     */
    public volatile int checked;

    /**
     * Average of the readings processed so far, in degrees Celsius. The main
     * thread reads this while the worker is running in order to draw the bar.
     * It stays {@code 0} until the first reading has been folded in.
     */
    public volatile double runningAverage;

    private final String city;
    private final TemperatureSource source;
    private double sum;

    /**
     * Creates a worker for one city.
     *
     * @param city   the city name shown on the bar, for example
     *               {@code "Las Palmas"} or {@code "Helsinki"}
     * @param source the readings this worker consumes
     */
    public TemperatureWorker(String city, TemperatureSource source) {
        super(city);
        this.city = city;
        this.source = source;
    }

    /**
     * Returns the city name given to the constructor.
     *
     * @return the city name
     */
    public String getCity() {
        return city;
    }

    /**
     * Returns the average of the readings processed so far, in degrees Celsius.
     *
     * <p>
     * The result is the volatile {@link #runningAverage}. It is {@code 0} when
     * no reading has been processed. The main thread may read it during the run;
     * after {@link #join()} it no longer changes.
     *
     * @return the arithmetic mean of the processed readings
     */
    public double average() {
        return runningAverage;
    }

    @Override
    public void run() {
        while (true) {
            OptionalDouble reading = source.next();
            if (reading.isEmpty()) {
                break;
            }
            sum += reading.getAsDouble();
            int seen = checked + 1;
            runningAverage = sum / seen;
            checked = seen;
        }
    }
}
