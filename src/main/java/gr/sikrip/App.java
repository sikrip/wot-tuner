package gr.sikrip;

import static gr.sikrip.EcuDataHandler.loadProperties;
import static gr.sikrip.EcuDataHandler.readProperties;

import java.io.IOException;
import java.util.Properties;

/**
 * The app entry point.
 */
public class App {

    public static final String WOT_MODE = "wot";
    public static final String CRUISE_MODE = "cruise";

    public static void main(String[] args) throws IOException {
        printVersion();
        if (args.length != 2) {
            printUsage();
            return;
        }
        final Properties properties = readProperties();
        if (properties == null) {
            System.err.println("Cannot load properties.");
            return;
        }
        loadProperties(properties);
        String mode = args[0];
        String ecuFilePath = args[1];
        switch (mode) {
            case CRUISE_MODE:
                CruiseTuner.tune(ecuFilePath);
                break;
            case WOT_MODE:
                WotTuner.tune(ecuFilePath);
                break;
            default:
                System.err.println("Unknown mode. Use 'cruise' or 'wot'.");
        }
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("java -jar wot-tuner.jar cruise|wot <ecu file path>\n");
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
