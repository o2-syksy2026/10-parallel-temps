# Applied exercise: "Parallel temperature bars"

Two threads read temperatures, one for Las Palmas and one for Helsinki. Each thread computes its own city's running average and increments its own counter. The main program is the only one that draws: it reads the workers' volatile state and prints two ASCII temperature bars. The length of a bar is the running average on a shared scale, not the share of readings processed. After the bar the line shows the temperature, the current row and the percent of the series processed.

## Starting the exercise

First fork this repository into your own GitHub repository with GitHub's **Fork** button, and only then clone your fork to your computer:

```sh
git clone https://github.com/<your-github-username>/10-parallel-temps.git
```

Open the cloned project in your code editor.

## Testing

The parts of the exercise have ready-made [JUnit](https://junit.org/) unit tests in the [src/test/java](./src/test/java/) directory. Each part tells which test class grades it. You can run the tests with your code editor's testing tool ([VS Code](https://code.visualstudio.com/docs/java/java-testing), [Eclipse](https://www.vogella.com/tutorials/JUnitEclipse/article.html)) or with [Gradle](https://docs.gradle.org/current/userguide/java_testing.html), for example:

```
./gradlew test --tests ShowProgressTest       # unix
.\gradlew.bat test --tests ShowProgressTest   # windows
```

Add `--info` to the end of the command for more details about failing tests. You do not need to study how the tests are implemented. You may write more tests of your own, but do not change or remove the given tests.

Do not change the names, parameters or return types of the given classes, methods and constructors, because the tests will then not compile. You get the original version of a file back with `git restore <path to file>` or with the **Palauta lähtökoodi** (restore starter code) function of the course system.

## Submitting and points

Each part of the exercise has its own page in the course system (https://jtjuslin.kapsi.fi/java), and its code fields contain the part's starter code. Copy your solution into the code fields or write it there directly. **Testaa ilman palautusta** (test without submitting) runs the part's tests without saving points, and **Lähetä** (submit) submits your solution. Each test class is accepted only if all of its tests pass, and the points are divided according to the accepted test classes.

**Points in two stages**

1. When your solution passes in the course system, you get **half of the points** for the part.
2. Finally, push everything to your public GitHub repository. Then press the **Lähetä tarkistettavaksi** (submit for checking) button on the part's page. When the tests pass, you get **full points** for the part. The repository may remain public.

There is no grading YAML workflow (GitHub Actions) on GitHub, and `git push` does not start any grading. When you press **Lähetä tarkistettavaksi**, the course system's bot fetches your code from your repository and checks it with the same tests. Both stages must be completed before the chapter deadline. You can submit as many times as you need, and your best result is kept.


## Temperatures

[data/las-palmas.csv](./data/las-palmas.csv) contains the daily mean temperatures for Las Palmas and [data/helsinki.csv](./data/helsinki.csv) those for Helsinki, both for 2024 (366 days). Do not modify the files. One row is one reading, in the form `date,temperature`. Comment lines start with `#`. [data/README.md](./data/README.md) records the source, the license and how the long series is built.

The figures are daily means from the Open-Meteo Historical Weather API ([open-meteo.com](https://open-meteo.com/)), based on the ERA5 reanalysis (Copernicus / ECMWF). The requested coordinates are 28.1235 N, 15.4363 W for Las Palmas and 60.1699 N, 24.9384 E for Helsinki. The full-year average is about 21 °C in Las Palmas and about 7 °C in Helsinki. The tests accept a Las Palmas average from 19 to 24 °C and a Helsinki average from 3 to 9 °C. Helsinki is compared with a wider tolerance than Las Palmas.

The program does not draw the year in calendar order. [TemperatureSeries](./src/main/java/temps/TemperatureSeries.java) replays the file 1 500 000 times in this order: the warmest days, then the coldest, then a recovery toward the year's average, and finally every day alternating above and below the mean. The running average therefore starts high, drops, and settles. Helsinki stays on the extremes longer and goes cold twice, so its bar moves more. Las Palmas moves less. Every reading is still a real day from the file.

A full run does a fixed amount of arithmetic per reading so that both threads together take about 15 seconds of wall-clock time. A worker never calls `Thread.sleep`. The tests request the same recipe with a few thousand rows, which skips that arithmetic and keeps the tests fast.

The readings are loaded by the ready-made class [TemperatureData](./src/main/java/temps/TemperatureData.java). The [TemperatureSource](./src/main/java/temps/TemperatureSource.java) interface is the place where a separate feeder program can be plugged in later. [QueueTemperatureSource](./src/main/java/temps/QueueTemperatureSource.java) accepts one reading at a time from another thread: the feeder calls `publish` and finally `close`, and the worker only calls `next`. A delay between readings belongs to the feeder, not to the worker. The tests use a delay of `0`.

## How the bars are drawn

Each worker has a `volatile int checked` and a `volatile double runningAverage`. The worker updates both for every reading: the average first, then the counter. The main thread reads them during the run. The worker prints nothing and does not call `Thread.sleep`.

The main thread starts both threads, and in a loop it:

1. reads both `checked` and `runningAverage`,
2. draws both bars with `showProgress`,
3. calls `Thread.sleep` before the next frame.

The length of a bar is the running average on the scale −10…30 °C. A bar is 50 characters, so one character is 0.8 °C. −10 °C is an empty bar and 30 °C is a full bar. Before the first reading the bar is empty, even though the average is still 0. Once readings exist, 0 °C is drawn at its place on the scale (13 characters). The whole line fits in 80 characters. Row counts are shortened: `120k` is row 120 000 and `1.5M` is row 1 500 000. The percent is the share processed. It is printed after the bar and does not change the bar's length.

When both workers are done, the main thread calls `join()` on each of them and draws the last frame. The last frame is the result the tests check: the bar length matches the final average.

Examples of frames. Before the first reading:

```
Las Palmas |--------------------------------------------------|  0.0°C    0   0%
Helsinki   |--------------------------------------------------|  0.0°C    0   0%
```

The high phase. Las Palmas is about 24 °C and Helsinki about 19 °C:

```
Las Palmas |###########################################-------| 24.2°C  90k   6%
Helsinki   |#####################################-------------| 19.3°C 120k   8%
```

The low phase. Helsinki has fallen near zero, Las Palmas only a little under its yearly average:

```
Las Palmas |######################################------------| 20.0°C 240k  16%
Helsinki   |################----------------------------------|  2.6°C 300k  20%
```

Settling. The bars are not full, because the average is not the top of the scale:

```
Las Palmas |######################################------------| 20.7°C 1.5M 100%
Helsinki   |#####################-----------------------------|  7.1°C 1.5M 100%
```

`#` is where the average sits on the scale and `-` is the rest of the bar. The decimal separator is a dot.

You can run the program in your code editor or with `./gradlew run` (on Windows `.\gradlew.bat run`). A run takes about 15 seconds, and the main thread redraws every 50 milliseconds. The tests call `runChart(0, 0, rowCount)` with a short series.


## Part 1: showProgress *(applying, 30 %)*

Implement the `showProgress` method of the [Main](./src/main/java/temps/Main.java) class. It receives the names of two cities, the counts of processed readings, the totals and the running averages, and returns a two-line string. The method does not print by itself. The lines are separated by `\n`. The string has no trailing newline and no terminal control codes. Each line is at most 80 characters.

One line is built like this:

* the city name is padded to a width of 10, then a space and `|`,
* the bar is `Main.BAR_WIDTH` characters (50), followed by `|`,
* if `checked` is 0, the bar is only `-`,
* otherwise the filled length is `Main.temperatureBarLength(average)` characters of `#` and the rest are `-`,
* the average is printed with one decimal, in a width of five, using `Locale.ROOT`, immediately followed by `°C`,
* the row in a width of four (`0`, `120k`, `1.5M`),
* the percent `checked * 100 / total` in a width of three, immediately followed by `%`.

If `total` is 0, the percent is 0. A more detailed description is in the method's comments.

Graded with the test class [ShowProgressTest](./src/test/java/temps/ShowProgressTest.java). The tests check the empty bar, the place of 0 °C on the scale, a high frame, a low frame, and that the bar length follows the average.


## Part 2: TemperatureWorker *(advanced, 40 %)*

Implement [TemperatureWorker](./src/main/java/temps/TemperatureWorker.java). The class extends `Thread`. The fields `checked` and `runningAverage` are given. Both are `volatile`. `checked` grows by one for every processed reading, and `runningAverage` is the average of the readings seen so far.

`run` reads `TemperatureSource.next()` until the source returns an empty `OptionalDouble`. Each reading is added to the sum. Then `runningAverage` is published, and only after that `checked`. The method must not print or sleep.

`average` returns `runningAverage`. If no reading has been processed, the result is `0`. The main thread may read the fields during the run. After `join()` they no longer change.

Graded with the test class [TemperatureWorkerTest](./src/test/java/temps/TemperatureWorkerTest.java). The tests check that the class is a thread, that both fields are `volatile`, that the counter advances one reading at a time, that the average changes, that two threads run at the same time, and that the running average of the series goes above and below the final average. Helsinki's swing is larger than Las Palmas's.


## Part 3: the main loop *(advanced, 30 %)*

Implement `Main.runChart(redrawDelayMillis, feedDelayMillis, rows)`. It opens both CSV files with `TemperatureData.open(file, feedDelayMillis, rows)`, starts a thread for `Las Palmas` and a thread for `Helsinki`, draws frames with `showProgress` for as long as either thread is alive, and sleeps between frames with `Thread.sleep(redrawDelayMillis)`. In the loop it reads `checked` and `runningAverage`. It then calls `join()` on both and returns the final frame.

The two-argument `runChart` is already provided: it calls the three-argument version with the full row count. Do not move drawing or sleeping into the workers. The parameter `feedDelayMillis` is the delay between readings; the tests pass `0` and a few thousand rows.

Graded with the test class [ParallelTempsTest](./src/test/java/temps/ParallelTempsTest.java). It runs `runChart(0, 0, rowCount)` and checks the final frame: the cities are labelled, the percent is 100, the bar length matches the final average, and the averages fall inside the ranges above. Helsinki's tolerance is wider than Las Palmas's.


## License and authors

This exercise was written by Jukka Juslin on top of the structure of the course's earlier exercise repositories, and it is licensed under the [Creative Commons BY-NC-SA license](https://creativecommons.org/licenses/by-nc-sa/4.0/). The temperatures were retrieved from the Open-Meteo Historical Weather API and are based on the ERA5 reanalysis (Copernicus Climate Change Service / ECMWF). Open-Meteo license: [open-meteo.com/en/license](https://open-meteo.com/en/license). The data note is in [data/README.md](./data/README.md).
