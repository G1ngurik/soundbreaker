package ru.toone.soundbreaker.config;

import net.fabricmc.loader.api.FabricLoader;
import ru.toone.soundbreaker.SoundBreakerMod;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Simple properties file at config/soundbreaker.properties. */
public final class ModConfig {

    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("soundbreaker.properties");

    private static final Properties PROPS = new Properties();

    private static int threshold = 50;
    private static double recordSeconds = 1.5;
    private static boolean enabled = true;
    private static boolean rememberSuccess = true;
    private static double passSeconds = 20.0;
    private static boolean effects = true;

    private ModConfig() {
    }

    public static void load() {
        PROPS.clear();
        if (Files.exists(FILE)) {
            try (InputStream in = Files.newInputStream(FILE)) {
                PROPS.load(in);
            } catch (IOException e) {
                SoundBreakerMod.LOGGER.warn("[Sound Breaker] could not read config", e);
            }
        }
        threshold = clamp(readInt("similarity_threshold", 50), 1, 100);
        recordSeconds = clamp(readDouble("record_seconds", 1.5), 0.3, 5.0);
        enabled = readBool("enabled", true);
        rememberSuccess = readBool("remember_success", true);
        passSeconds = clamp(readDouble("pass_seconds", 20.0), 0.0, 3600.0);
        effects = readBool("effects", true);

        PROPS.setProperty("similarity_threshold", Integer.toString(threshold));
        PROPS.setProperty("record_seconds", Double.toString(recordSeconds));
        PROPS.setProperty("enabled", Boolean.toString(enabled));
        PROPS.setProperty("remember_success", Boolean.toString(rememberSuccess));
        PROPS.setProperty("pass_seconds", Double.toString(passSeconds));
        PROPS.setProperty("effects", Boolean.toString(effects));
        save();
    }

    public static void save() {
        try {
            if (Files.notExists(FILE.getParent())) {
                Files.createDirectories(FILE.getParent());
            }
            try (OutputStream out = Files.newOutputStream(FILE)) {
                PROPS.store(out, "Sound Breaker configuration");
            }
        } catch (IOException e) {
            SoundBreakerMod.LOGGER.warn("[Sound Breaker] could not write config", e);
        }
    }

    public static int threshold() { return threshold; }
    public static double recordSeconds() { return recordSeconds; }
    public static boolean enabled() { return enabled; }
    public static boolean rememberSuccess() { return rememberSuccess; }
    public static double passSeconds() { return passSeconds; }
    public static boolean effects() { return effects; }

    public static void setThreshold(int value) {
        threshold = clamp(value, 1, 100);
        PROPS.setProperty("similarity_threshold", Integer.toString(threshold));
        save();
    }

    public static void setRecordSeconds(double value) {
        recordSeconds = clamp(value, 0.3, 5.0);
        PROPS.setProperty("record_seconds", Double.toString(recordSeconds));
        save();
    }

    private static int readInt(String key, int def) {
        try {
            return Integer.parseInt(PROPS.getProperty(key, Integer.toString(def)).trim());
        } catch (RuntimeException e) {
            return def;
        }
    }

    private static double readDouble(String key, double def) {
        try {
            return Double.parseDouble(PROPS.getProperty(key, Double.toString(def)).trim());
        } catch (RuntimeException e) {
            return def;
        }
    }

    private static boolean readBool(String key, boolean def) {
        return Boolean.parseBoolean(PROPS.getProperty(key, Boolean.toString(def)).trim());
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
}
