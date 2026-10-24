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
 * default (with a warning). {@link #save} rewrites the file with every key and comment and keeps what the player wrote:
 * an invalid value stays as typed, with a note saying what is used instead, and keys this mod doesn't know are kept at
 * the end. Comments of the player's own are not kept.
 */
public final class PropertiesConfig {
    private final Path file;
    private final Properties read = new Properties();
    private final List<String> lines = new ArrayList<>();
    private final java.util.Set<String> known = new java.util.HashSet<>();

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
            else return invalid(key, raw, fallback, comment + " (true/false)");
        }
        put(key, String.valueOf(value), comment + " (true/false)");
        return value;
    }

    public int integer(String key, int fallback, int min, int max, String comment) {
        String raw = read.getProperty(key);
        int value = fallback;
        String range = comment + " (" + min + " to " + max + ")";
        if (raw != null) {
            try {
                int v = Integer.parseInt(raw.trim());
                if (v >= min && v <= max) value = v;
                else return invalid(key, raw, fallback, range);
            } catch (NumberFormatException e) {
                return invalid(key, raw, fallback, range);
            }
        }
        put(key, String.valueOf(value), range);
        return value;
    }

    public double decimal(String key, double fallback, double min, double max, String comment) {
        String raw = read.getProperty(key);
        double value = fallback;
        String range = comment + " (" + min + " to " + max + ")";
        if (raw != null) {
            try {
                double v = Double.parseDouble(raw.trim());
                if (v >= min && v <= max) value = v;
                else return invalid(key, raw, fallback, range);
            } catch (NumberFormatException e) {
                return invalid(key, raw, fallback, range);
            }
        }
        put(key, String.valueOf(value), range);
        return value;
    }

    public <E extends Enum<E>> E choice(String key, E fallback, String comment) {
        String raw = read.getProperty(key);
        E value = fallback;
        StringBuilder names = new StringBuilder();
        for (E e : fallback.getDeclaringClass().getEnumConstants()) names.append(names.isEmpty() ? "" : ", ").append(e.name());
        if (raw != null) {
            try {
                value = Enum.valueOf(fallback.getDeclaringClass(), raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return invalid(key, raw, fallback, comment + " (" + names + ")");
            }
        }
        put(key, value.name(), comment + " (" + names + ")");
        return value;
    }

    /** Writes every key read so far, with its comment and value, then any keys this mod doesn't know, as written. */
    public void save(String title) {
        StringBuilder out = new StringBuilder("# ").append(title).append('\n');
        for (String line : lines) out.append(line).append('\n');
        List<String> unknown = read.stringPropertyNames().stream().filter(k -> !known.contains(k)).sorted().toList();
        if (!unknown.isEmpty()) {
            out.append("\n# Not Full Draw settings: kept as written, not read\n");
            for (String key : unknown) out.append(escape(key)).append('=').append(escape(read.getProperty(key))).append('\n');
        }
        try (Writer w = Files.newBufferedWriter(file)) {
            w.write(out.toString());
        } catch (IOException e) {
            FullDrawMod.LOGGER.warn("Couldn't write {}", file, e);
        }
    }

    private void put(String key, String value, String comment) {
        known.add(key);
        lines.add("");
        lines.add("# " + comment);
        lines.add(key + "=" + escape(value));
    }

    /** Keeps the player's value in the file, notes what is used instead, and returns that. */
    private <T> T invalid(String key, String raw, T fallback, String comment) {
        FullDrawMod.LOGGER.warn("{}: {}={} isn't valid, using {}", file.getFileName(), key, raw, fallback);
        put(key, raw, comment);
        lines.add(lines.size() - 1, "# Invalid, so " + fallback + " is used until this is fixed");
        return fallback;
    }

    /** What {@link Properties#load} reads back as {@code s}. */
    private static String escape(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' || c == '=' || c == ':' || c == '#' || c == '!' || (c == ' ' && i == 0)) out.append('\\');
            out.append(c == '\n' ? "\\n" : String.valueOf(c));
        }
        return out.toString();
    }
}
