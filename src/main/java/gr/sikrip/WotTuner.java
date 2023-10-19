package gr.sikrip;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
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
import lombok.Builder;
import lombok.ToString;

/**
 * Given a csv log from PowerTune, creates a map of logged AFR to be used for manual fine-tuning the base fuel map.
 */
public class WotTuner {

    private static final String DECIMAL_FORMAT = "%6.3f";
    private static final String NEW_FUEL_MAP_FILE = "./new-fuel.map";
    private static int fuelTableSize;
    private static Integer[] rpmLabels;
    private static Integer[] loadLabels;
    private static String timeHeader;
    private static String rpmHeader;
    private static String afrHeader;
    private static String throttleHeader;
    private static String rpmIdxHeader;
    private static String loadIdxHeader;
    private static double wotVolts;
    private static double accelEnrichSeconds;
    private static int minNumberOfSamples;
    private static double wotTargetAfr;

    public static void main(String[] args) throws IOException {
        printVersion();
        if (args.length != 1) {
            printUsage();
            return;
        }

        final Properties properties = loadProperties();
        if (properties == null) {
            return;
        }
        loadProperties(properties);

        final double[][] currentFuelMap = readFuelMap();
        if (currentFuelMap == null) {
            return;
        }
        final String ecuFilePath = args[0];
        System.out.printf("\nAnalyzing %s...\n", ecuFilePath);
        final List<LogEntry> logEntries = readEcuLog(ecuFilePath);
        final double[][] loggedAfr = new double[fuelTableSize][fuelTableSize];
        final int[][] loggedAfrSample = new int[fuelTableSize][fuelTableSize];
        double wotStart = 0;
        boolean underWOT = false;
        for (final LogEntry logEntry : logEntries) {
            final boolean underWotNow = logEntry.throttle >= wotVolts;
            if (underWotNow) {
                if (!underWOT) {
                    // Start of wot
                    wotStart = logEntry.timeSeconds;
                    underWOT = true;
                }
            } else {
                // End of wot
                wotStart = 0;
                underWOT = false;
            }
            if (underWOT) {
                if (logEntry.timeSeconds - wotStart >= accelEnrichSeconds) {
                    // fuel enrichment done
                    int col = logEntry.mapP - 1;
                    int row = logEntry.mapN - 1;
                    final double currentAvgSum =
                        loggedAfr[col][row] * loggedAfrSample[col][row];
                    loggedAfrSample[col][row]++;
                    loggedAfr[col][row] =
                        (currentAvgSum + logEntry.afr) / loggedAfrSample[col][row];
                }
            }

        }

        System.out.println("\n========= Logged AFR ===========");
        for (int i = 0; i < loggedAfr.length; i++) {
            if (i==0) {
                printMapN_Values();
            }
            for (int j = 0; j < loggedAfr[i].length; j++) {
                if (j==0) {
                    printMapP_Value(i);
                }
                if (loggedAfrSample[i][j] > minNumberOfSamples) {
                    System.out.printf(DECIMAL_FORMAT, loggedAfr[i][j]);
                } else {
                    System.out.printf(DECIMAL_FORMAT, 0.0);
                }
                if (j < loggedAfr[i].length - 1) {
                    System.out.print("\t");
                }
            }
            System.out.println();
        }
        final double[][] newFuelMap = new double[fuelTableSize][fuelTableSize];
        for (int i = 0; i < loggedAfr.length; i++) {
            for (int j = 0; j < loggedAfr[i].length; j++) {
                if (loggedAfrSample[i][j] > minNumberOfSamples) {
                    final double newFuelValue = (loggedAfr[i][j] / wotTargetAfr) * currentFuelMap[i][j];
                    newFuelMap[i][j] = newFuelValue;
                } else {
                    newFuelMap[i][j] = currentFuelMap[i][j];
                }
            }
        }

        System.out.printf("\n========= New Fuel Map (also saved under %s) ===========\n", NEW_FUEL_MAP_FILE);
        try (BufferedWriter newFuelMapWriter = new BufferedWriter(new FileWriter(NEW_FUEL_MAP_FILE))) {
            for (int i = 0; i < newFuelMap.length; i++) {
                if (i==0) {
                    printMapN_Values();
                }
                for (int j = 0; j < newFuelMap[i].length; j++) {
                    if (j==0) {
                        printMapP_Value(i);
                    }
                    System.out.printf(DECIMAL_FORMAT, newFuelMap[i][j]);
                    newFuelMapWriter.write(String.format("%.3f", newFuelMap[i][j]));
                    if (j < newFuelMap[i].length - 1) {
                        System.out.print("\t");
                        newFuelMapWriter.write("\t");
                    }
                }
                System.out.println();
                newFuelMapWriter.write("\n");
            }
        }
        System.out.println("\n========= New-Old Map ===========");
        for (int i = 0; i < newFuelMap.length; i++) {
            if (i==0) {
                printMapN_Values();
            }
            for (int j = 0; j < newFuelMap[i].length; j++) {
                if (j==0) {
                    printMapP_Value(i);
                }
                System.out.printf(DECIMAL_FORMAT, newFuelMap[i][j] - currentFuelMap[i][j]);
                if (j < newFuelMap[i].length - 1) {
                    System.out.print("\t");
                }
            }
            System.out.println();
        }
    }

    private static void printMapP_Value(int i) {
        System.out.printf("%5d(%2d)", loadLabels[i], i + 1);
    }

    private static void printMapN_Values() {
        System.out.print("\t\t");
        for(int j = 0; j < fuelTableSize; j++) {
            System.out.printf("%6d", rpmLabels[j]);
            if (j < fuelTableSize -1) {
                System.out.print("\t");
            }
        }
        System.out.println();
    }

    private static void loadProperties(Properties properties) {
        rpmLabels = Arrays.stream(properties.getProperty("rpmLabels")
            .split(","))
            .map(l -> Integer.parseInt(l.trim()))
            .toArray(Integer[]::new);

        loadLabels = Arrays.stream(properties.getProperty("loadLabels")
            .split(","))
            .map(l -> Integer.parseInt(l.trim()))
            .toArray(Integer[]::new);

        fuelTableSize = Integer.parseInt(properties.getProperty("fuelTableSize"));
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
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("java -jar wot-tuner.jar <ecu file1> <ecu file2> ...\n");
    }

    private static void printVersion() {
        try {
            final Properties properties = new Properties();
            properties.load(WotTuner.class.getClassLoader().getResourceAsStream("project.properties"));
            System.out.printf("vbo-merger version %s\n", properties.getProperty("version"));
        } catch (IOException e) {
            // ignore
        }
    }

    private static Properties loadProperties() {
        try (final FileInputStream stream = new FileInputStream("tuner.properties")){
            final Properties properties = new Properties();
            properties.load(stream);
            return properties;
        } catch (IOException e) {
            System.err.println("Could not load properties");
        }
        return null;
    }

    private static double[][] readFuelMap() {
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

    private static List<LogEntry> readEcuLog(String filePath) throws IOException {
        final List<LogEntry> logData = new ArrayList<>();
        final AtomicInteger timeIdx = new AtomicInteger();
        final AtomicInteger rpmIdx = new AtomicInteger();
        final AtomicInteger afrIdx = new AtomicInteger();
        final AtomicInteger throttleIdx = new AtomicInteger();
        final AtomicInteger mapNIdx = new AtomicInteger();
        final AtomicInteger mapPIdx = new AtomicInteger();
        final AtomicBoolean headerCreated = new AtomicBoolean(false);
        Files.lines(Paths.get(filePath)).forEach(line -> {
            final List<String> values = Arrays.asList(line.split(","));
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
        });
        return logData;
    }

    @Builder
    @ToString
    private static class LogEntry {
        double timeSeconds;
        int rpm;
        double afr;
        double throttle;
        int mapN;
        int mapP;
    }
}
