package temps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(15)
public class ParallelTempsTest {

    private static final int ROWS = 8_000;

    @Test
    void finalFrameMatchesTheRunningAverageAndCityRanges() throws Exception {
        String frame = Main.runChart(0, 0, ROWS);
        String[] lines = frame.split("\n");

        assertEquals(2, lines.length, frame);
        assertTrue(lines[0].startsWith("Las Palmas |"), lines[0]);
        assertTrue(lines[1].startsWith("Helsinki   |"), lines[1]);
        assertTrue(lines[0].contains("100%"), lines[0]);
        assertTrue(lines[1].contains("100%"), lines[1]);
        assertTrue(lines[0].length() <= 80, lines[0]);
        assertTrue(lines[1].length() <= 80, lines[1]);
        assertEquals(Main.BAR_WIDTH, bar(lines[0]).length());
        assertEquals(Main.BAR_WIDTH, bar(lines[1]).length());

        double lasPalmas = temperature(lines[0]);
        double helsinki = temperature(lines[1]);
        double lasPalmasMean = mean(TemperatureData.stream(TemperatureData.LAS_PALMAS, ROWS));
        double helsinkiMean = mean(TemperatureData.stream(TemperatureData.HELSINKI, ROWS));

        assertEquals(lasPalmasMean, lasPalmas, 0.05, "Las Palmas tolerance is tight");
        assertEquals(helsinkiMean, helsinki, 0.5, "Helsinki tolerance is wider");
        assertTrue(lasPalmas >= 19.0 && lasPalmas <= 24.0, "Las Palmas " + lasPalmas);
        assertTrue(helsinki >= 3.0 && helsinki <= 9.0, "Helsinki " + helsinki);
        assertEquals(Main.temperatureBarLength(lasPalmasMean), count(bar(lines[0]), '#'), lines[0]);
        assertEquals(Main.temperatureBarLength(helsinkiMean), count(bar(lines[1]), '#'), lines[1]);
        assertTrue(count(bar(lines[0]), '#') < Main.BAR_WIDTH, "the bar is the temperature, not 100% progress");
        assertTrue(count(bar(lines[1]), '#') < Main.BAR_WIDTH, "the bar is the temperature, not 100% progress");
        assertTrue(frame.contains("°C"));
    }

    private static String bar(String line) {
        int open = line.indexOf('|');
        int close = line.indexOf('|', open + 1);
        assertTrue(open >= 0 && close > open, line);
        return line.substring(open + 1, close);
    }

    private static double temperature(String line) {
        String marker = "°C";
        int end = line.lastIndexOf(marker);
        assertTrue(end > 0, line);
        String head = line.substring(0, end).trim();
        String number = head.substring(head.lastIndexOf(' ') + 1);
        return Double.parseDouble(number);
    }

    private static double mean(double[] values) {
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    private static int count(String text, char character) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == character) {
                count++;
            }
        }
        return count;
    }
}
