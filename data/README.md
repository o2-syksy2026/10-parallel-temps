# Temperature seed data

`las-palmas.csv` and `helsinki.csv` are the daily mean temperatures at 2 m for the year 2024, one row per day (`date,temperature_c`). The program does not draw these 366 days in calendar order. It loads them as a seed and replays values from the file into a series of 1 500 000 readings (`TemperatureSeries.LIVE_ROWS`).

## Where the numbers come from

Open-Meteo Historical Weather API ([open-meteo.com](https://open-meteo.com/)), based on the ERA5 reanalysis (Copernicus Climate Data Store / ECMWF).

| City | Requested coordinates | Time zone |
| --- | --- | --- |
| Las Palmas | 28.1235 N, 15.4363 W | Atlantic/Canary |
| Helsinki | 60.1699 N, 24.9384 E | Europe/Helsinki |

The same note is in the header of each CSV file. The file means are about 20.7 °C for Las Palmas and about 7.1 °C for Helsinki.

License: [open-meteo.com/en/license](https://open-meteo.com/en/license). ERA5 is produced by Copernicus / ECMWF ([cds.climate.copernicus.eu](https://cds.climate.copernicus.eu/)).

## How the 1 500 000-row stream is derived

`TemperatureSeries` sorts the seed and builds four bins:

* **warm** — the warmest 12% of the days
* **cold** — the coldest 12% of the days
* **recover** — days at or above the file mean, excluding that warm tail
* **cycle** — every day in the file, alternating above and below the mean

Each reading is one of those real days. The position in the series picks the bin:

| Progress | Las Palmas | Helsinki |
| --- | --- | --- |
| first | warm until 6% | warm until 8% |
| then | cold until 16% | cold until 20% |
| then | recover until 28% | recover until 32%, cold again until 42%, recover until 55% |
| rest | cycle through the whole file | cycle through the whole file |

Helsinki therefore spends longer on its extremes, and it dips twice, so its running average swings more than Las Palmas. Both settle toward the climate of their own file. Tests call the same recipe with a few thousand rows and skip the extra arithmetic that makes a full run last about fifteen seconds of wall-clock time. Do not edit the CSV numbers.
