package temps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(15)
public class TemperatureWorkerTest {

    @Test
    void workerIsAThreadWithVolatileRunningState() throws NoSuchFieldException {
        assertEquals(Thread.class, TemperatureWorker.class.getSuperclass());

        Field checked = TemperatureWorker.class.getDeclaredField("checked");
        assertEquals(int.class, checked.getType());
        assertTrue(Modifier.isVolatile(checked.getModifiers()), "checked must be volatile");

        Field runningAverage = TemperatureWorker.class.getDeclaredField("runningAverage");
        assertEquals(double.class, runningAverage.getType());
        assertTrue(Modifier.isVolatile(runningAverage.getModifiers()), "runningAverage must be volatile");
    }

    @Test
    void checkedAdvancesOneReadingAtATimeAndTheRunningAverageChanges() throws InterruptedException {
        StepSource source = new StepSource(10, 30, 20);
        TemperatureWorker worker = new TemperatureWorker("Las Palmas", source);
        worker.start();

        source.permits.release();
        awaitChecked(worker, 1);
        assertEquals(10.0, worker.average(), 0.0001);

        source.permits.release();
        awaitChecked(worker, 2);
        double afterTwo = worker.average();
        assertEquals(20.0, afterTwo, 0.0001);
        assertNotEquals(10.0, afterTwo);

        source.permits.release();
        awaitChecked(worker, 3);
        assertEquals(20.0, worker.average(), 0.0001);

        source.permits.release();
        worker.join(2_000);
        assertFalse(worker.isAlive());
        assertEquals(source.size(), worker.checked);
        assertEquals(Thread.State.TERMINATED, worker.getState());
    }

    @Test
    void twoWorkersRunAtTheSameTimeOnDifferentThreads() throws InterruptedException {
        GateSource lasPalmasSource = new GateSource();
        GateSource helsinkiSource = new GateSource();
        TemperatureWorker lasPalmas = new TemperatureWorker("Las Palmas", lasPalmasSource);
        TemperatureWorker helsinki = new TemperatureWorker("Helsinki", helsinkiSource);

        lasPalmas.start();
        helsinki.start();

        assertTrue(lasPalmasSource.entered.await(2, TimeUnit.SECONDS), "Las Palmas worker did not start");
        assertTrue(helsinkiSource.entered.await(2, TimeUnit.SECONDS), "Helsinki worker did not start");
        assertTrue(lasPalmas.isAlive());
        assertTrue(helsinki.isAlive());
        assertNotEquals(lasPalmas.threadId(), helsinki.threadId());
        assertNotEquals(Thread.currentThread().threadId(), lasPalmas.threadId());
        assertNotEquals(Thread.currentThread().threadId(), helsinki.threadId());

        lasPalmasSource.release.countDown();
        helsinkiSource.release.countDown();
        lasPalmas.join(2_000);
        helsinki.join(2_000);
        assertEquals(Thread.State.TERMINATED, lasPalmas.getState());
        assertEquals(Thread.State.TERMINATED, helsinki.getState());
    }

    @Test
    void queueSourceCanBeFedFromOutsideTheWorker() throws InterruptedException {
        QueueTemperatureSource source = new QueueTemperatureSource(3);
        TemperatureWorker worker = new TemperatureWorker("Helsinki", source);
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(captured));
        try {
            worker.start();
            source.publish(4);
            source.publish(6);
            source.publish(11);
            source.close();
            worker.join(2_000);
        } finally {
            System.setOut(original);
        }

        assertEquals("", captured.toString(), "the worker must not print");
        assertEquals(3, worker.checked);
        assertEquals(7.0, worker.average(), 0.0001);
    }

    @Test
    void bundledAveragesAreInRangeAndHelsinkiHasTheWiderTolerance() throws Exception {
        double[] lasPalmasSeed = TemperatureData.readValues(TemperatureData.LAS_PALMAS);
        double[] helsinkiSeed = TemperatureData.readValues(TemperatureData.HELSINKI);
        assertEquals(366, lasPalmasSeed.length);
        assertEquals(366, helsinkiSeed.length);
        assertTrue(mean(lasPalmasSeed) >= 19.0 && mean(lasPalmasSeed) <= 24.0);
        assertTrue(mean(helsinkiSeed) >= 3.0 && mean(helsinkiSeed) <= 9.0);

        int rows = 12_000;
        double[] lasPalmas = TemperatureData.stream(TemperatureData.LAS_PALMAS, rows);
        double[] helsinki = TemperatureData.stream(TemperatureData.HELSINKI, rows);
        assertAllValuesComeFromTheSeed(lasPalmas, lasPalmasSeed);
        assertAllValuesComeFromTheSeed(helsinki, helsinkiSeed);

        double[] lasPalmasMeans = runningMeans(lasPalmas);
        double[] helsinkiMeans = runningMeans(helsinki);
        double lasPalmasFinal = lasPalmasMeans[rows - 1];
        double helsinkiFinal = helsinkiMeans[rows - 1];
        int lasPalmasHigh = indexOfMax(lasPalmasMeans);
        int lasPalmasLow = indexOfMin(lasPalmasMeans);
        int helsinkiHigh = indexOfMax(helsinkiMeans);
        int helsinkiLow = indexOfMin(helsinkiMeans);

        assertTrue(helsinkiHigh < helsinkiLow, "Helsinki goes high before it goes low");
        assertTrue(helsinkiMeans[helsinkiHigh] > helsinkiFinal + 8, "Helsinki swing is wide");
        assertTrue(helsinkiMeans[helsinkiLow] < helsinkiFinal - 3, "Helsinki drops below its final average");
        assertTrue(risesAbove(helsinkiMeans, helsinkiLow, helsinkiFinal + 0.3),
                "Helsinki crosses back above the final average before it settles");

        assertTrue(lasPalmasHigh < lasPalmasLow, "Las Palmas goes high before it goes low");
        assertTrue(lasPalmasMeans[lasPalmasHigh] > lasPalmasFinal + 1.5);
        assertTrue(lasPalmasMeans[lasPalmasLow] < lasPalmasFinal - 0.4);
        double helsinkiAmp = helsinkiMeans[helsinkiHigh] - helsinkiMeans[helsinkiLow];
        double lasPalmasAmp = lasPalmasMeans[lasPalmasHigh] - lasPalmasMeans[lasPalmasLow];
        assertTrue(lasPalmasAmp * 2 < helsinkiAmp, "Las Palmas swings less than Helsinki");

        TemperatureWorker lasPalmasWorker = new TemperatureWorker("Las Palmas", new MemorySource(lasPalmas));
        TemperatureWorker helsinkiWorker = new TemperatureWorker("Helsinki", new MemorySource(helsinki));
        lasPalmasWorker.start();
        helsinkiWorker.start();
        lasPalmasWorker.join(2_000);
        helsinkiWorker.join(2_000);

        assertEquals(rows, lasPalmasWorker.checked);
        assertEquals(rows, helsinkiWorker.checked);
        assertEquals(lasPalmasFinal, lasPalmasWorker.average(), 0.05, "Las Palmas tolerance is tight");
        assertEquals(helsinkiFinal, helsinkiWorker.average(), 0.5, "Helsinki tolerance is wider");
        assertTrue(lasPalmasWorker.average() >= 19.0 && lasPalmasWorker.average() <= 24.0,
                "Las Palmas average " + lasPalmasWorker.average());
        assertTrue(helsinkiWorker.average() >= 3.0 && helsinkiWorker.average() <= 9.0,
                "Helsinki average " + helsinkiWorker.average());

        TemperatureWorker earlyHelsinki = new TemperatureWorker("Helsinki",
                new MemorySource(copy(helsinki, helsinkiLow + 1)));
        earlyHelsinki.start();
        earlyHelsinki.join(2_000);
        assertEquals(helsinkiLow + 1, earlyHelsinki.checked);
        assertTrue(earlyHelsinki.average() < helsinkiFinal - 3,
                "the running average is still low part-way through the Helsinki series");
    }

    @Test
    void emptySourceLeavesTheAverageAtZero() throws InterruptedException {
        TemperatureWorker worker = new TemperatureWorker("Helsinki", new MemorySource(new double[0]));
        worker.start();
        worker.join(2_000);
        assertEquals(0, worker.checked);
        assertEquals(0.0, worker.average(), 0.0);
    }

    private static void awaitChecked(TemperatureWorker worker, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (worker.checked != expected) {
            if (System.nanoTime() > deadline) {
                fail("checked was " + worker.checked + ", expected " + expected);
            }
            Thread.onSpinWait();
        }
    }

    private static double mean(double[] values) {
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    private static double[] runningMeans(double[] values) {
        double[] means = new double[values.length];
        double sum = 0;
        for (int i = 0; i < values.length; i++) {
            sum += values[i];
            means[i] = sum / (i + 1);
        }
        return means;
    }

    private static int indexOfMax(double[] values) {
        int index = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[index]) {
                index = i;
            }
        }
        return index;
    }

    private static int indexOfMin(double[] values) {
        int index = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] < values[index]) {
                index = i;
            }
        }
        return index;
    }

    private static boolean risesAbove(double[] means, int after, double level) {
        for (int i = after + 1; i < means.length; i++) {
            if (means[i] > level) {
                return true;
            }
        }
        return false;
    }

    private static void assertAllValuesComeFromTheSeed(double[] stream, double[] seed) {
        Set<Double> allowed = new HashSet<>();
        for (double value : seed) {
            allowed.add(value);
        }
        for (double value : stream) {
            assertTrue(allowed.contains(value), "stream value " + value + " is not in the seed file");
        }
    }

    private static double[] copy(double[] values, int length) {
        double[] prefix = new double[length];
        System.arraycopy(values, 0, prefix, 0, length);
        return prefix;
    }

    private static final class MemorySource implements TemperatureSource {
        private final double[] values;
        private int index;

        MemorySource(double[] values) {
            this.values = values;
        }

        @Override
        public synchronized OptionalDouble next() {
            if (index >= values.length) {
                return OptionalDouble.empty();
            }
            return OptionalDouble.of(values[index++]);
        }

        @Override
        public int size() {
            return values.length;
        }
    }

    private static final class StepSource implements TemperatureSource {
        private final double[] values;
        private final Semaphore permits = new Semaphore(0);
        private int index;

        StepSource(double... values) {
            this.values = values;
        }

        @Override
        public OptionalDouble next() {
            try {
                permits.acquire();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return OptionalDouble.empty();
            }
            if (index >= values.length) {
                return OptionalDouble.empty();
            }
            return OptionalDouble.of(values[index++]);
        }

        @Override
        public int size() {
            return values.length;
        }
    }

    private static final class GateSource implements TemperatureSource {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public OptionalDouble next() {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return OptionalDouble.empty();
        }

        @Override
        public int size() {
            return 1;
        }
    }
}
