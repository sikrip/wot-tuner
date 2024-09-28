package gr.sikrip;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import static gr.sikrip.EcuDataHandler.*;

/**
 * Given a csv log from PowerTune, creates a new base fuel map
 * adjusted on cruising driving conditions.
 */
class CruiseTuner {

    private static final String DECIMAL_FORMAT = "%6.3f";
    private static final String DECIMAL_FORMAT_AFR = "%6.2f";
    private static final String NEW_FUEL_MAP_FILE = "./new-fuel.map";

    static void tune(String ecuFilePath) throws IOException {
        final double[][] currentFuelMap = readFuelMap();
        if (currentFuelMap == null) {
            return;
        }
        System.out.printf("\nAnalyzing %s...\n", ecuFilePath);
        final List<LogEntry> logEntries = readEcuLog(ecuFilePath);
        final double[][] loggedAfr = new double[fuelTableSize][fuelTableSize];
        final int[][] loggedAfrSample = new int[fuelTableSize][fuelTableSize];

        double lastLoggedTime = -1;
        double lastThrottleVolt = -1;
        for (final LogEntry logEntry : logEntries) {
            if (lastLoggedTime == -1) {
                lastLoggedTime = logEntry.getTimeSeconds();
                lastThrottleVolt = logEntry.getThrottle();
                continue;
            }
            if (isCruiseConditions(logEntry, lastThrottleVolt, lastLoggedTime)) {
                int col = logEntry.getMapP();
                int row = logEntry.getMapN();
                final double currentAvgSum = loggedAfr[col][row] * loggedAfrSample[col][row];
                loggedAfrSample[col][row]++;
                loggedAfr[col][row] = (currentAvgSum + logEntry.getAfr()) / loggedAfrSample[col][row];
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
                    System.out.printf(DECIMAL_FORMAT_AFR, loggedAfr[i][j]);
                } else {
                    System.out.printf(DECIMAL_FORMAT_AFR, 0.0);
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
                    final double newFuelValue = (loggedAfr[i][j] / cruiseTargetAfr) * currentFuelMap[i][j];
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

    private static boolean isCruiseConditions(LogEntry logEntry,
                                              double lastThrottleVolt,
                                              double lastLoggedTime) {
        if (logEntry.getThrottle() < minCruiseThrottleVolts) {
            // not under throttle
            return false;
        }

        if (logEntry.getThrottle() > maxCruiseThrottleVolts) {
            // too much throttle for cruise
            return false;
        }

        if (logEntry.getRpm() > maxCruiseRpm) {
            // too much RPM for cruise
            return false;
        }

        if (logEntry.getWaterTemp() < minWaterTemp) {
            // water temp too low
            return false;
        }

        final double voltChangePerSecond = (logEntry.getThrottle() - lastThrottleVolt) / (logEntry.getTimeSeconds() - lastLoggedTime);
        if (voltChangePerSecond > maxTuneVoltChange) {
            // accel enrich
            return false;
        }

        return true;
    }
}
