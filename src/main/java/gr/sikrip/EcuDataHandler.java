package gr.sikrip;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Heads ECU maps and logged data.
 */
public class EcuDataHandler {

    static int fuelTableSize;
    static Integer[] rpmLabels;
    static Integer[] loadLabels;
    static String separator;
    static int linesToSkip;
    static String timeHeader;
    static String rpmHeader;
    static String afrHeader;
    static String throttleHeader;
    static String rpmIdxHeader;
    static String loadIdxHeader;
    static double wotVolts;
    static double minTuneThrottleVolts;
    static double maxTuneVoltChange;
    static double accelEnrichSeconds;
    static int minNumberOfSamples;
    static double wotTargetAfr;
    static double cruiseTargetAfr;

    static double[][] readFuelMap() {
        try (final Stream<String> fileLines = Files.lines(Paths.get("./fuel.map"))) {
            final double[][] fuelMap = new double[fuelTableSize][fuelTableSize];
            final List<String> mapLines = fileLines.collect(Collectors.toList());
            for (int i = 0; i < mapLines.size(); i++) {
                final String[] mapValues = mapLines.get(i).split("\t");
                for (int j = 0; j < mapValues.length; j++) {
                    fuelMap[i][j] = Double.parseDouble(mapValues[j]);
                }
            }
            return fuelMap;
        } catch (IOException e) {
            System.err.println("Could not load fuel map");
        }
        return null;
    }

    static void loadProperties(Properties properties) {
        rpmLabels = Arrays.stream(properties.getProperty("rpmLabels")
                .split(","))
            .map(l -> Integer.parseInt(l.trim()))
            .toArray(Integer[]::new);

        loadLabels = Arrays.stream(properties.getProperty("loadLabels")
                .split(","))
            .map(l -> Integer.parseInt(l.trim()))
            .toArray(Integer[]::new);

        fuelTableSize = Integer.parseInt(properties.getProperty("fuelTableSize"));
        separator = properties.getProperty("separator");
        linesToSkip = Integer.parseInt(properties.getProperty("linesToSkip"));
        timeHeader = properties.getProperty("timeHeader");
        rpmHeader = properties.getProperty("rpmHeader");
        afrHeader = properties.getProperty("afrHeader");
        throttleHeader = properties.getProperty("throttleHeader");
        rpmIdxHeader = properties.getProperty("rpmIdxHeader");
        loadIdxHeader = properties.getProperty("loadIdxHeader");
        wotVolts = Double.parseDouble(properties.getProperty("wotVolt"));
        accelEnrichSeconds = Double.parseDouble(properties.getProperty("accelEnrichSeconds"));
        minNumberOfSamples = Integer.parseInt(properties.getProperty("minNumberOfSamples"));
        wotTargetAfr = Double.parseDouble(properties.getProperty("wotTargetAfr"));
        cruiseTargetAfr = Double.parseDouble(properties.getProperty("cruiseTargetAfr"));
        minTuneThrottleVolts = Double.parseDouble(properties.getProperty("minTuneThrottleVolts"));
        maxTuneVoltChange = Double.parseDouble(properties.getProperty("maxTuneVoltChange"));
    }

    static List<LogEntry> readEcuLog(String filePath) throws IOException {
        final List<LogEntry> logData = new ArrayList<>();
        final AtomicInteger linesCount = new AtomicInteger(0);
        final AtomicInteger timeIdx = new AtomicInteger();
        final AtomicInteger rpmIdx = new AtomicInteger();
        final AtomicInteger afrIdx = new AtomicInteger();
        final AtomicInteger throttleIdx = new AtomicInteger();
        final AtomicInteger mapNIdx = new AtomicInteger();
        final AtomicInteger mapPIdx = new AtomicInteger();
        final AtomicBoolean headerCreated = new AtomicBoolean(false);
        Files.lines(Paths.get(filePath)).forEach(line -> {
            if (linesCount.incrementAndGet() > linesToSkip) {
                final List<String> values = Arrays.stream(line.split(separator))
                    .map(String::trim).collect(Collectors.toList());
                if (headerCreated.get()) {
                    logData.add(
                        LogEntry.builder()
                            .timeSeconds(Double.parseDouble(values.get(timeIdx.get())))
                            .rpm(Integer.parseInt(values.get(rpmIdx.get())))
                            .afr(Double.parseDouble(values.get(afrIdx.get())))
                            .throttle(Double.parseDouble(values.get(throttleIdx.get())))
                            .mapN(Integer.parseInt(values.get(mapNIdx.get())))
                            .mapP(Integer.parseInt(values.get(mapPIdx.get())))
                            .build()
                    );
                } else {
                    timeIdx.set(values.indexOf(timeHeader));
                    rpmIdx.set(values.indexOf(rpmHeader));
                    afrIdx.set(values.indexOf(afrHeader));
                    throttleIdx.set(values.indexOf(throttleHeader));
                    mapNIdx.set(values.indexOf(rpmIdxHeader));
                    mapPIdx.set(values.indexOf(loadIdxHeader));
                    headerCreated.set(true);
                }
            }
        });
        return logData;
    }

    static void printMapP_Value(int i) {
        System.out.printf("%5d(%2d)\t", loadLabels[i], i + 1);
    }

    static void printMapN_Values() {
        // each MAPP is 9 chars long
        System.out.print("         \t");
        for(int j = 0; j < fuelTableSize; j++) {
            System.out.printf("%6d", rpmLabels[j]);
            if (j < fuelTableSize -1) {
                System.out.print("\t");
            }
        }
        System.out.println();
    }

    static Properties readProperties() {
        try (final FileInputStream stream = new FileInputStream("tuner.properties")){
            final Properties properties = new Properties();
            properties.load(stream);
            return properties;
        } catch (IOException e) {
            System.err.println("Could not load properties");
        }
        return null;
    }

}
