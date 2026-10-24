package com.panda.fulldraw.fabric;

import com.panda.fulldraw.FullDrawMod;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/**
 * A config file in {@code config/} as Java properties, the Fabric side's stand-in for NeoForge's config specs. Each
 * read names its key, default, range and comment; values that are missing, malformed or out of range fall back to the
 * default (with a warning), and {@link #save} rewrites the file with every key and comment, keeping the player's values.
 */
public final class PropertiesConfig {
    private final Path file;
    private final Properties read = new Properties();
    private final List<String> lines = new ArrayList<>();

    public PropertiesConfig(String fileName) {
        file = FabricLoader.getInstance().getConfigDir().resolve(fileName);
        if (Files.exists(file)) {
            try (Reader in = Files.newBufferedReader(file)) {
                read.load(in);
            } catch (IOException e) {
                FullDrawMod.LOGGER.warn("Couldn't read {}, using the defaults", file, e);
            }
        }
    }

    public boolean bool(String key, boolean fallback, String comment) {
        String raw = read.getProperty(key);
        boolean value = fallback;
        if (raw != null) {
            if (raw.trim().equalsIgnoreCase("true") || raw.trim().equalsIgnoreCase("false")) value = Boolean.parseBoolean(raw.trim());
            else warn(key, raw, fallback);
        }
        put(key, String.valueOf(value), comment + " (true/false)");
        return value;
    }

    public int integer(String key, int fallback, int min, int max, String comment) {
        String raw = read.getProperty(key);
        int value = fallback;
        if (raw != null) {
            try {
                int v = Integer.parseInt(raw.trim());
                if (v >= min && v <= max) value = v;
                else warn(key, raw, fallback);
            } catch (NumberFormatException e) {
                warn(key, raw, fallback);
            }
        }
        put(key, String.valueOf(value), comment + " (" + min + " to " + max + ")");
        return value;
    }

    public double decimal(String key, double fallback, double min, double max, String comment) {
        String raw = read.getProperty(key);
        double value = fallback;
        if (raw != null) {
            try {
                double v = Double.parseDouble(raw.trim());
                if (v >= min && v <= max) value = v;
                else warn(key, raw, fallback);
            } catch (NumberFormatException e) {
                warn(key, raw, fallback);
            }
        }
        put(key, String.valueOf(value), comment + " (" + min + " to " + max + ")");
        return value;
    }

    public <E extends Enum<E>> E choice(String key, E fallback, String comment) {
        String raw = read.getProperty(key);
        E value = fallback;
        if (raw != null) {
            try {
                value = Enum.valueOf(fallback.getDeclaringClass(), raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                warn(key, raw, fallback);
            }
        }
        StringBuilder names = new StringBuilder();
        for (E e : fallback.getDeclaringClass().getEnumConstants()) names.append(names.isEmpty() ? "" : ", ").append(e.name());
        put(key, value.name(), comment + " (" + names + ")");
        return value;
    }

    /** Writes every key read so far, with its comment and current value. */
    public void save(String title) {
        StringBuilder out = new StringBuilder("# ").append(title).append('\n');
        for (String line : lines) out.append(line).append('\n');
        try (Writer w = Files.newBufferedWriter(file)) {
            w.write(out.toString());
        } catch (IOException e) {
            FullDrawMod.LOGGER.warn("Couldn't write {}", file, e);
        }
    }

    private void put(String key, String value, String comment) {
        lines.add("");
        lines.add("# " + comment);
        lines.add(key + "=" + value);
    }

    private void warn(String key, String raw, Object fallback) {
        FullDrawMod.LOGGER.warn("{}: {}={} isn't valid, using {}", file.getFileName(), key, raw, fallback);
    }
}
