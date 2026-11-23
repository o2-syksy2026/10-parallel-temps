package temps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
public class ShowProgressTest {

    @Test
    void barWidthIsFiftyCharacters() {
        assertEquals(50, Main.BAR_WIDTH);
        assertEquals(-10.0, Main.SCALE_MIN);
        assertEquals(30.0, Main.SCALE_MAX);
    }

    @Test
    void zeroReadingsDrawEmptyBars() {
        String frame = Main.showProgress("Las Palmas", 0, 1_500_000, 0, "Helsinki", 0, 1_500_000, 0);

        assertEquals("""
                Las Palmas |--------------------------------------------------|  0.0°C    0   0%
                Helsinki   |--------------------------------------------------|  0.0°C    0   0%""", frame);
        assertFalse(frame.contains("#"));
        assertTrue(frame.lines().allMatch(line -> line.length() == 80));
    }

    @Test
    void zeroCelsiusWithDataSitsOnTheScale() {
        String frame = Main.showProgress("Las Palmas", 10, 100, 0, "Helsinki", 10, 100, 0);

        assertEquals("""
                Las Palmas |#############-------------------------------------|  0.0°C   10  10%
                Helsinki   |#############-------------------------------------|  0.0°C   10  10%""", frame);
        assertEquals(13, count(bar(frame.split("\n")[0]), '#'));
    }

    @Test
    void barLengthFollowsTheRunningAverage() {
        double[] averages = { -10, -5, 0, 2.6, 7.1, 19.3, 20.7, 24.2, 30, 40 };
        for (double average : averages) {
            String frame = Main.showProgress("Las Palmas", 1_500, 3_000, average, "Helsinki", 750, 3_000, average / 2);
            String[] lines = frame.split("\n");
            assertEquals(2, lines.length, frame);
            assertEquals(Main.BAR_WIDTH, bar(lines[0]).length(), lines[0]);
            assertEquals(Main.BAR_WIDTH, bar(lines[1]).length(), lines[1]);
            assertEquals(Main.temperatureBarLength(average), count(bar(lines[0]), '#'), lines[0]);
            assertEquals(Main.temperatureBarLength(average / 2), count(bar(lines[1]), '#'), lines[1]);
            assertTrue(lines[0].length() <= 80, lines[0] + " len " + lines[0].length());
            assertTrue(lines[1].length() <= 80, lines[1] + " len " + lines[1].length());
            assertTrue(lines[0].contains("50 %") || lines[0].contains("50%"), lines[0]);
            assertTrue(lines[1].contains("25 %") || lines[1].contains("25%"), lines[1]);
        }
    }

    @Test
    void settledFrameShowsTemperatureRowAndPercent() {
        String frame = Main.showProgress(
                "Las Palmas", 1_500_000, 1_500_000, 20.7,
                "Helsinki", 1_500_000, 1_500_000, 7.1);

        assertEquals("""
                Las Palmas |######################################------------| 20.7°C 1.5M 100%
                Helsinki   |#####################-----------------------------|  7.1°C 1.5M 100%""", frame);
        assertEquals(38, count(bar(frame.split("\n")[0]), '#'));
        assertEquals(21, count(bar(frame.split("\n")[1]), '#'));
        assertTrue(frame.contains("°C"));
        assertTrue(frame.contains("100%"));
    }

    @Test
    void highAndLowFramesKeepTheLineInsideEightyCharacters() {
        String high = Main.showProgress("Las Palmas", 90_000, 1_500_000, 24.2, "Helsinki", 120_000, 1_500_000, 19.3);
        String low = Main.showProgress("Las Palmas", 240_000, 1_500_000, 20.0, "Helsinki", 300_000, 1_500_000, 2.6);

        assertEquals("""
                Las Palmas |###########################################-------| 24.2°C  90k   6%
                Helsinki   |#####################################-------------| 19.3°C 120k   8%""", high);
        assertEquals("""
                Las Palmas |######################################------------| 20.0°C 240k  16%
                Helsinki   |################----------------------------------|  2.6°C 300k  20%""", low);
        assertTrue(high.lines().allMatch(line -> line.length() == 80));
        assertTrue(low.lines().allMatch(line -> line.length() == 80));
    }

    @Test
    void labelsComeFromTheArguments() {
        String frame = Main.showProgress("Alpha", 1, 2, 3.25, "Beta", 2, 2, 4);

        assertTrue(frame.startsWith("Alpha"), frame);
        assertTrue(frame.contains("\nBeta"), frame);
        assertTrue(frame.contains("3.3°C") || frame.contains("3.3 °C"), frame);
        assertTrue(frame.contains("4.0°C") || frame.contains("4.0 °C"), frame);
        assertTrue(frame.contains("50%"), frame);
        assertTrue(frame.contains("100%"), frame);
        assertEquals(Main.temperatureBarLength(3.25), count(bar(frame.split("\n")[0]), '#'));
        assertEquals(Main.temperatureBarLength(4), count(bar(frame.split("\n")[1]), '#'));
    }

    @Test
    void zeroTotalDoesNotDivideByZero() {
        String frame = Main.showProgress("Las Palmas", 0, 0, 0, "Helsinki", 0, 0, 0);

        assertTrue(frame.contains("0%"), frame);
        assertFalse(frame.contains("#"), frame);
        assertEquals(Main.BAR_WIDTH, bar(frame.split("\n")[0]).length());
    }

    private static String bar(String line) {
        int open = line.indexOf('|');
        int close = line.indexOf('|', open + 1);
        assertTrue(open >= 0 && close > open, line);
        return line.substring(open + 1, close);
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
