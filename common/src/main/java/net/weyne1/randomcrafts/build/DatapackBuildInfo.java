package net.weyne1.randomcrafts.build;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class DatapackBuildInfo {
    private static final String RESOURCE_PATH = "/randomcrafts-datapack.properties";

    private static final Properties PROPERTIES = load();

    private DatapackBuildInfo() {}

    public static String datapackName() {
        return requiredString("datapack_name");
    }

    public static String datapackDescription() {
        return requiredString("datapack_description");
    }

    public static int packFormat() {
        String raw = requiredString("pack_format");
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid integer for 'pack_format' in " + RESOURCE_PATH + ": '" + raw + "'", e);
        }
    }

    private static Properties load() {
        Properties props = new Properties();
        try (InputStream in = DatapackBuildInfo.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                throw new IllegalStateException("Missing build metadata resource: " + RESOURCE_PATH);
            }
            props.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read build metadata resource: " + RESOURCE_PATH, e);
        }
        return props;
    }

    private static String requiredString(String key) {
        String value = PROPERTIES.getProperty(key);
        if (value == null) {
            throw new IllegalStateException("Missing '" + key + "' in " + RESOURCE_PATH);
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalStateException("Blank '" + key + "' in " + RESOURCE_PATH);
        }
        if (trimmed.contains("${")) {
            throw new IllegalStateException(
                    "Unexpanded placeholder for '" + key + "' in " + RESOURCE_PATH + ": '" + trimmed + "'. " +
                            "Make sure processResources expands randomcrafts-datapack.properties."
            );
        }
        return trimmed;
    }
}
