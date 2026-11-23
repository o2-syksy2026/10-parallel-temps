> [!NOTE]
> The English translation for this exercise can be found in [this file](./readme.en.md).

# Soveltava tehtävä: "Rinnakkaiset lämpötilapalkit"

Kaksi säiettä lukee lämpötiloja, toinen Las Palmasista ja toinen Helsingistä. Kumpikin säie laskee oman kaupunkinsa juoksevaa keskiarvoa ja kasvattaa omaa laskuriaan. Pääohjelma on ainoa, joka piirtää: se lukee säikeiden volatile-tilan ja tulostaa kaksi ASCII-lämpötilapalkkia. Palkin pituus on juokseva keskiarvo yhteisellä asteikolla, ei käsiteltyjen havaintojen osuus. Rivin lopussa näkyvät lämpötila, monesko rivi on menossa ja kuinka monta prosenttia sarjasta on käsitelty.

## Tehtävän aloittaminen

Forkkaa ensin tämä repositorio omaan GitHub-repositorioosi GitHubin **Fork**-painikkeella ja kloonaa vasta sitten oma forkkisi koneellesi:

```sh
git clone https://github.com/<oma-github-tunnuksesi>/10-parallel-temps.git
```

Avaa kloonattu projekti koodieditorissasi.

## Testaaminen

Tehtävän osille on valmiit [JUnit](https://junit.org/)-yksikkötestit hakemistossa [src/test/java](./src/test/java/). Kunkin osan kohdalla kerrotaan, millä testiluokalla se arvioidaan. Voit ajaa testit koodieditorisi testaustyökalulla ([VS Code](https://code.visualstudio.com/docs/java/java-testing), [Eclipse](https://www.vogella.com/tutorials/JUnitEclipse/article.html)) tai [Gradlella](https://docs.gradle.org/current/userguide/java_testing.html), esimerkiksi:

```
./gradlew test --tests ShowProgressTest       # unix
.\gradlew.bat test --tests ShowProgressTest   # windows
```

Lisää komennon loppuun `--info`, jos haluat tarkemmat tiedot epäonnistuneista testeistä. Testien tekniseen toteutukseen ei tarvitse perehtyä. Saat kirjoittaa omia testejä lisää, mutta älä muuta tai poista valmiita testejä.

Älä muuta valmiiden luokkien, metodien ja konstruktorien nimiä, parametreja tai paluuarvojen tyyppejä, sillä silloin testit eivät käänny. Saat tiedoston alkuperäisen version takaisin komennolla `git restore <tiedoston polku>` tai kurssijärjestelmän **Palauta lähtökoodi** -toiminnolla.

## Palauttaminen ja pisteet

Jokaisella tehtävän osalla on kurssijärjestelmässä (https://jtjuslin.kapsi.fi/java) oma sivunsa, jonka koodikentissä on osan koodipohja. Kopioi ratkaisusi koodikenttiin tai kirjoita se suoraan niihin. **Testaa ilman palautusta** ajaa osan testit tallentamatta pisteitä, ja **Lähetä** palauttaa ratkaisun. Jokainen testiluokka hyväksytään vain, jos sen kaikki testit menevät läpi, ja pisteet jaetaan hyväksyttyjen testiluokkien mukaan.

**Pisteet kahdessa vaiheessa**

1. Kun ratkaisusi menee läpi kurssijärjestelmässä, saat osasta **puolet pisteistä**.
2. Pushaa lopuksi kaikki julkiseen GitHub-repositorioosi. Paina tämän jälkeen osan sivulla **Lähetä tarkistettavaksi** -nappia. Kun testit menevät läpi, saat osasta **täydet pisteet**. Repositorio saa jäädä julkiseksi.

GitHubissa ei ole arvioivaa YAML-tehtävävirtaa (GitHub Actions -workflowta), eikä `git push` käynnistä arviointia. Kun painat **Lähetä tarkistettavaksi**, kurssijärjestelmän botti hakee koodisi repositoriostasi ja tarkistaa sen samoilla testeillä. Molemmat vaiheet pitää tehdä ennen luvun määräaikaa. Voit lähettää ratkaisusi niin monta kertaa kuin tarvitset, ja voimaan jää paras tulos.


## Lämpötilat

Tiedosto [data/las-palmas.csv](./data/las-palmas.csv) sisältää Las Palmasin ja [data/helsinki.csv](./data/helsinki.csv) Helsingin päiväkeskilämpötilat vuodelta 2024 (366 vuorokautta). Älä muokkaa tiedostoja. Yksi rivi on yksi havainto, muodossa `päivä,lämpötila`. Kommenttirivit alkavat merkillä `#`. Tarkempi kuvaus lähteestä, lisenssistä ja siitä, miten pitkä sarja kootaan, on tiedostossa [data/README.md](./data/README.md).

Luvut ovat Open-Meteo Historical Weather API:n ([open-meteo.com](https://open-meteo.com/)) ERA5-uusanalyysiin perustuvia päiväkeskiarvoja (Copernicus / ECMWF). Las Palmasin pyydetyt koordinaatit ovat 28.1235 N, 15.4363 W ja Helsingin 60.1699 N, 24.9384 E. Koko vuoden keskiarvo on Las Palmasissa noin 21 °C ja Helsingissä noin 7 °C. Testit hyväksyvät Las Palmasin keskiarvon väliltä 19–24 °C ja Helsingin keskiarvon väliltä 3–9 °C. Helsingin vertailussa on väljempi toleranssi kuin Las Palmasin.

Ohjelma ei piirrä vuotta kalenterijärjestyksessä. [TemperatureSeries](./src/main/java/temps/TemperatureSeries.java) toistaa tiedoston lukuja 1 500 000 kertaa tässä järjestyksessä: ensin lämpimimmät päivät, sitten kylmimmät, sitten palautuminen kohti vuoden keskiarvoa, ja lopuksi kaikki päivät vuorotellen keskiarvon ylä- ja alapuolelta. Juokseva keskiarvo käy siis ensin korkealla, painuu alas ja asettuu vähitellen. Helsinki viipyy ääripäissä kauemmin ja käy kylmänä kahdesti, joten sen palkki heiluu selvemmin. Las Palmasin vaihtelu on kapeampi. Jokainen lukema on silti jokin tiedoston oikea päivä.

Täysi ajo tekee jokaista havaintoa kohti pienen määrän laskentaa, jotta molemmat säikeet yhdessä kestävät noin 15 sekuntia seinäkelloaikaa. Työntekijä ei kutsu `Thread.sleep`-metodia. Testit pyytävät samaa reseptiä muutamalla tuhannella rivillä, jolloin lisälaskenta jää pois ja testit pysyvät nopeina.

Havainnot luetaan valmiilla luokalla [TemperatureData](./src/main/java/temps/TemperatureData.java). Rajapinta [TemperatureSource](./src/main/java/temps/TemperatureSource.java) on se kohta, johon myöhemmin voi kytkeä erillisen syöttöohjelman. [QueueTemperatureSource](./src/main/java/temps/QueueTemperatureSource.java) ottaa vastaan yksittäisiä lukemia toisesta säikeestä: syöttäjä kutsuu `publish`-metodia ja lopuksi `close`-metodia, työntekijä vain `next`-metodia. Viive havaintojen välissä kuuluu syöttäjälle, ei työntekijälle. Testit käyttävät viivettä `0`.

## Miten palkit piirretään

Kummallakin työntekijällä on `volatile int checked` ja `volatile double runningAverage`. Työntekijä päivittää molemmat jokaista havaintoa kohti: ensin keskiarvon, sitten laskurin. Pääsäie lukee ne kesken ajon. Työntekijä ei tulosta mitään eikä kutsu `Thread.sleep`-metodia.

Pääsäie käynnistää molemmat säikeet, ja silmukassa:

1. lukee molempien `checked`- ja `runningAverage`-arvot,
2. piirtää molemmat palkit metodilla `showProgress`,
3. kutsuu `Thread.sleep`-metodia ennen seuraavaa ruutua.

Palkin pituus on juokseva keskiarvo asteikolla −10…30 °C. Palkissa on 50 merkkiä, joten yksi merkki on 0,8 °C. −10 °C on tyhjä palkki ja 30 °C täysi. Ennen ensimmäistä havaintoa palkki on tyhjä, vaikka keskiarvo on vielä 0. Kun havaintoja on, 0 °C piirretään asteikon kohdalle (13 merkkiä). Koko rivi mahtuu 80 merkkiin. Rivinumerot lyhennetään: `120k` on rivi 120 000 ja `1.5M` on rivi 1 500 000. Prosentti on käsitelty osuus ja se tulostetaan palkin jälkeen, se ei muuta palkin pituutta.

Kun molemmat työntekijät ovat valmiit, pääsäie kutsuu kummallekin `join()` ja piirtää viimeisen ruudun. Viimeinen ruutu on se tulos, jonka testit tarkistavat: palkin pituus vastaa lopullista keskiarvoa.

Esimerkkejä ruuduista. Ennen ensimmäistä havaintoa:

```
Las Palmas |--------------------------------------------------|  0.0°C    0   0%
Helsinki   |--------------------------------------------------|  0.0°C    0   0%
```

Korkea vaihe. Las Palmas on noin 24 °C ja Helsinki noin 19 °C:

```
Las Palmas |###########################################-------| 24.2°C  90k   6%
Helsinki   |#####################################-------------| 19.3°C 120k   8%
```

Matala vaihe. Helsinki on painunut lähelle nollaa, Las Palmas vain vähän vuoden keskiarvon alle:

```
Las Palmas |######################################------------| 20.0°C 240k  16%
Helsinki   |################----------------------------------|  2.6°C 300k  20%
```

Asettuminen. Palkit eivät täyty, koska keskiarvo ei ole asteikon yläpää:

```
Las Palmas |######################################------------| 20.7°C 1.5M 100%
Helsinki   |#####################-----------------------------|  7.1°C 1.5M 100%
```

`#` on keskiarvon kohta asteikolla ja `-` on loput palkista. Desimaalierotin on piste.

Voit suorittaa ohjelman koodieditorissasi tai komennolla `./gradlew run` (Windowsissa `.\gradlew.bat run`). Ajo kestää noin 15 sekuntia, ja pääsäie piirtää ruudun 50 millisekunnin välein. Testit kutsuvat `runChart(0, 0, rivimäärä)` lyhyellä sarjalla.


## Osa 1: showProgress *(soveltava, 30 %)*

Toteuta [Main](./src/main/java/temps/Main.java)-luokan metodi `showProgress`. Se saa kahden kaupungin nimet, käsiteltyjen havaintojen määrät, havaintojen kokonaismäärät ja juoksevat keskiarvot, ja palauttaa kahden rivin merkkijonon. Metodi ei tulosta itse. Rivien välissä on `\n`, eikä merkkijonon lopussa ole rivinvaihtoa eikä päätteen ohjauskoodeja. Kumpikin rivi on enintään 80 merkkiä.

Yksi rivi kootaan näin:

* kaupungin nimi täytetään 10 merkin levyiseksi ja sen jälkeen tulee välilyönti ja `|`,
* palkki on `Main.BAR_WIDTH` merkkiä (50) ja sen perään `|`,
* jos `checked` on 0, palkki on pelkkiä `-`-merkkejä,
* muuten täyttöä on `Main.temperatureBarLength(average)` kappaletta merkkiä `#` ja loput ovat `-`,
* keskiarvo tulostetaan yhdellä desimaalilla, viiden merkin levyisenä, luokalla `Locale.ROOT`, ja heti perään `°C`,
* rivinumero neljän merkin levyisenä (`0`, `120k`, `1.5M`),
* prosentti `checked * 100 / total` kolmen merkin levyisenä ja heti perään `%`.

Jos `total` on 0, prosentti on 0. Tarkempi kuvaus on metodin kommenteissa.

Arvioinnissa ajetaan testiluokka [ShowProgressTest](./src/test/java/temps/ShowProgressTest.java). Testit tarkistavat tyhjän palkin, 0 °C:n kohdan asteikolla, korkean ja matalan ruudun sekä sen, että palkin pituus seuraa keskiarvoa.


## Osa 2: TemperatureWorker *(edistynyt, 40 %)*

Toteuta [TemperatureWorker](./src/main/java/temps/TemperatureWorker.java). Luokka perii `Thread`-luokan. Kentät `checked` ja `runningAverage` ovat valmiina. Molemmat ovat `volatile`. `checked` kasvaa yhdellä jokaista käsiteltyä havaintoa kohti, ja `runningAverage` on siihen asti nähtyjen lukemien keskiarvo.

`run` lukee lähteestä `TemperatureSource.next()` kunnes lähde palauttaa tyhjän `OptionalDouble`-arvon. Jokainen lukema lisätään summaan. Sen jälkeen julkaistaan `runningAverage` ja vasta sitten `checked`. Metodissa ei saa tulostaa eikä nukkua.

`average` palauttaa `runningAverage`. Jos yhtään lukemaa ei ole käsitelty, tulos on `0`. Pääsäie saa lukea kenttiä kesken ajon. `join()`-kutsun jälkeen ne eivät enää muutu.

Arvioinnissa ajetaan testiluokka [TemperatureWorkerTest](./src/test/java/temps/TemperatureWorkerTest.java). Testit tarkistavat, että luokka on säie, että molemmat kentät ovat `volatile`, että laskuri etenee havainto kerrallaan, että keskiarvo muuttuu, että kaksi säiettä on yhtä aikaa käynnissä ja että sarjan juokseva keskiarvo käy lopullisen keskiarvon ylä- ja alapuolella. Helsingin heilahdus on Las Palmasia suurempi.


## Osa 3: pääohjelman silmukka *(edistynyt, 30 %)*

Toteuta `Main.runChart(redrawDelayMillis, feedDelayMillis, rows)`. Se avaa molemmat CSV-tiedostot `TemperatureData.open(tiedosto, feedDelayMillis, rows)` -metodilla, käynnistää säikeen `Las Palmas` ja säikeen `Helsinki`, piirtää ruutuja `showProgress`-metodilla niin kauan kuin jompikumpi säie on elossa ja nukkuu ruutujen välissä `Thread.sleep(redrawDelayMillis)`. Silmukassa se lukee `checked` ja `runningAverage`. Sen jälkeen se kutsuu `join()` kummallekin ja palauttaa viimeisen ruudun.

Kaksiparametrinen `runChart` on valmiina: se kutsuu kolmiparametrista versiota täydellä rivimäärällä. Älä siirrä piirtämistä tai nukkumista työntekijöihin. Parametri `feedDelayMillis` on havaintojen syöttöviive; testit antavat sille arvoksi `0` ja rivimääräksi muutaman tuhannen.

Arvioinnissa ajetaan testiluokka [ParallelTempsTest](./src/test/java/temps/ParallelTempsTest.java). Se ajaa `runChart(0, 0, rivimäärä)` ja tarkistaa viimeisen ruudun: kaupungit on nimetty, prosentti on 100, palkin pituus vastaa lopullista keskiarvoa ja keskiarvot osuvat edellä kerrottuihin väleihin. Helsingin toleranssi on Las Palmasia väljempi.


## Lisenssi ja tekijät

Tämän tehtävän on laatinut Jukka Juslin kurssin aiempien tehtävärepositorioiden rakenteen pohjalta, ja se on lisensoitu [Creative Commons BY-NC-SA -lisenssillä](https://creativecommons.org/licenses/by-nc-sa/4.0/). Lämpötilat on haettu Open-Meteo Historical Weather API:sta, ja ne perustuvat ERA5-uusanalyysiin (Copernicus Climate Change Service / ECMWF). Open-Meteon lisenssi: [open-meteo.com/en/license](https://open-meteo.com/en/license). Aineiston kuvaus on tiedostossa [data/README.md](./data/README.md).
