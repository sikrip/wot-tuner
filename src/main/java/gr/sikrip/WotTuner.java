package gr.sikrip;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Builder;
import lombok.ToString;

/**
 * Given a csv log from PowerTune, creates a map of logged AFR to be used for manual fine-tuning the base fuel map.
 */
public class WotTuner {

    private static final String ECU_TIME_HEADER = "Time(S)";
    private static final String ECU_RPM_HEADER = "RPM";
    private static final String ECU_AFR_HEADER = "WideBand";
    private static final String ECU_THROTTLE_HEADER = "VTA V";
    private static final String ECU_MAPN_HEADER = "MAPN";
    private static final String ECU_MAPP_HEADER = "MAPP";
    private static final double WOT_VALUE = 4.0;
    private static final double ACCEL_ENRICH_DURATION_SECONDS = 0.5;

    public static void main(String[] args) throws IOException {
        printVersion();
        if (args.length == 0) {
            printUsage();
        }
        for (String ecuFilePath : args) {
            System.out.printf("\nAnalyzing %s\n", ecuFilePath);
            final List<LogEntry> logEntries = readEcuLog(ecuFilePath);
            final double[][] loggedAfr = new double[20][20];
            final int[][] loggedAfrSample = new int[20][20];
            double wotStart = 0;
            boolean underWOT = false;
            for (final LogEntry logEntry : logEntries) {
                final boolean underWotNow = logEntry.throttle >= WOT_VALUE;
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
                    if (logEntry.timeSeconds - wotStart >= ACCEL_ENRICH_DURATION_SECONDS) {
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
            final DecimalFormat decimalFormat = new DecimalFormat("00.00");
            for (int c = 0; c < loggedAfr.length; c++) {
                for (int r = 0; r < loggedAfr[c].length; r++) {
                    System.out.print(decimalFormat.format(loggedAfr[c][r]));
                    if (r < loggedAfr[c].length - 1) {
                        System.out.print("\t");
                    }
                }
                System.out.println();
            }
        }
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
                timeIdx.set(values.indexOf(ECU_TIME_HEADER));
                rpmIdx.set(values.indexOf(ECU_RPM_HEADER));
                afrIdx.set(values.indexOf(ECU_AFR_HEADER));
                throttleIdx.set(values.indexOf(ECU_THROTTLE_HEADER));
                mapNIdx.set(values.indexOf(ECU_MAPN_HEADER));
                mapPIdx.set(values.indexOf(ECU_MAPP_HEADER));
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
