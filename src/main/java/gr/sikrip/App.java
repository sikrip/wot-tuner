package gr.sikrip;

import static gr.sikrip.EcuDataHandler.loadProperties;
import static gr.sikrip.EcuDataHandler.readProperties;

import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

/**
 * The app entry point.
 */
public class App {

    public static final String WOT_MODE = "w";
    public static final String CRUISE_MODE = "c";

    public static void main(String[] args) throws IOException {
        printVersion();
        if (args.length < 2) {
            printUsage();
            return;
        }
        final Properties properties = readProperties();
        if (properties == null) {
            System.err.println("Cannot load properties.");
            return;
        }
        loadProperties(properties);
        final String mode = args[0];
        final String[] ecuLogPaths = Arrays.copyOfRange(args, 1, args.length);
        switch (mode) {
            case CRUISE_MODE:
                CruiseTuner.tune(ecuLogPaths);
                break;
            case WOT_MODE:
                WotTuner.tune(ecuLogPaths);
                break;
            default:
                System.err.println("Unknown mode. Use 'cruise' or 'wot'.");
        }
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.printf(
            "java -jar wot-tuner.jar %s|%s <ecu log file 1> <ecu log file 2> ...\n",
            CRUISE_MODE, WOT_MODE
        );
    }

    private static void printVersion() {
        try {
            final Properties properties = new Properties();
            properties.load(CruiseTuner.class.getClassLoader().getResourceAsStream("project.properties"));
            System.out.printf("wot-tuner version %s\n", properties.getProperty("version"));
        } catch (IOException e) {
            // ignore
        }
    }
}
