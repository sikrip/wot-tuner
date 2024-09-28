package gr.sikrip;

import static gr.sikrip.EcuDataHandler.accelEnrichSeconds;
import static gr.sikrip.EcuDataHandler.fuelTableSize;
import static gr.sikrip.EcuDataHandler.minNumberOfSamples;
import static gr.sikrip.EcuDataHandler.printMapN_Values;
import static gr.sikrip.EcuDataHandler.printMapP_Value;
import static gr.sikrip.EcuDataHandler.readEcuLog;
import static gr.sikrip.EcuDataHandler.readFuelMap;
import static gr.sikrip.EcuDataHandler.wotTargetAfr;
import static gr.sikrip.EcuDataHandler.wotVolts;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Given a csv log from PowerTune, creates a new base fuel map
 * adjusted under WOT conditions.
 */
class WotTuner {

    private static final String DECIMAL_FORMAT = "%6.3f";
    private static final String NEW_FUEL_MAP_FILE = "./new-fuel.map";

    static void tune(String... ecuLogPaths) throws IOException {
        final double[][] currentFuelMap = readFuelMap();
        if (currentFuelMap == null) {
            return;
        }
        final double[][] loggedAfr = new double[fuelTableSize][fuelTableSize];
        final int[][] loggedAfrSample = new int[fuelTableSize][fuelTableSize];
        for (String ecuLogPath : ecuLogPaths) {
            System.out.printf("\nAnalyzing %s...\n", ecuLogPath);
            final List<LogEntry> logEntries = readEcuLog(ecuLogPath);
            double wotStart = 0;
            boolean underWOT = false;
            for (final LogEntry logEntry : logEntries) {
                final boolean underWotNow = logEntry.getThrottle() >= wotVolts;
                if (underWotNow) {
                    if (!underWOT) {
                        // Start of wot
                        wotStart = logEntry.getTimeSeconds();
                        underWOT = true;
                    }
                } else {
                    // End of wot
                    wotStart = 0;
                    underWOT = false;
                }
                if (underWOT) {
                    if (logEntry.getTimeSeconds() - wotStart >= accelEnrichSeconds) {
                        // fuel enrichment done
                        int col = logEntry.getMapP();
                        int row = logEntry.getMapN();
                        final double currentAvgSum =
                                loggedAfr[col][row] * loggedAfrSample[col][row];
                        loggedAfrSample[col][row]++;
                        loggedAfr[col][row] =
                                (currentAvgSum + logEntry.getAfr()) / loggedAfrSample[col][row];
                    }
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
                if (loggedAfrSample[i][j] >= minNumberOfSamples) {
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
                if (loggedAfrSample[i][j] >= minNumberOfSamples) {
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
}
