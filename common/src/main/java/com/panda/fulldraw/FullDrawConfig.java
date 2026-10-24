package com.panda.fulldraw;

import com.panda.fulldraw.logic.ShotMath;

/**
 * The server's settings. Each loader keeps them in its own config and plugs them in with {@link #use}.
 * A client on a server takes the server's numbers from {@code server_tuning}, so the ring never shows a
 * window the server doesn't use.
 */
public final class FullDrawConfig {
    /** Where the values come from. The defaults apply until the loader's config is loaded. */
    public interface Source {
        /** Players' full-draw shots crit as in vanilla (false: only a Perfect Release crits). */
        boolean vanillaCritOnFullDraw();

        /** The shot pipeline's numbers. */
        ShotMath.Tuning tuning();
    }

    public static final Source DEFAULTS = new Source() {
        @Override public boolean vanillaCritOnFullDraw() { return false; }
        @Override public ShotMath.Tuning tuning() { return ShotMath.DEFAULTS; }
    };

    private static Source source = DEFAULTS;
    /** The connected server's numbers, on a client; null when not connected. */
    private static ShotMath.Tuning remote;

    private FullDrawConfig() {}

    public static void use(Source loaderConfig) {
        source = loaderConfig;
    }

    /** On the client: the server's numbers, or null on disconnect. */
    public static void useRemote(ShotMath.Tuning serverTuning) {
        remote = serverTuning;
    }

    /** Whether this client has the server's numbers (for tests of the join sync). */
    public static boolean hasRemote() {
        return remote != null;
    }

    private static double clampedFull = Double.NaN;

    /**
     * The tremble's full point, at least a tick (0.05 s) after its start: a full point before the start would divide by a
     * negative span. Logs once per value when it has to move it.
     */
    public static double trembleFull(double start, double full) {
        double min = start + 0.05;
        if (full >= min) return full;
        if (full != clampedFull) {
            clampedFull = full;
            com.panda.fulldraw.FullDrawMod.LOGGER.warn("trembleFullSeconds {} is not after trembleStartSeconds {}, using {}", full, start, min);
        }
        return min;
    }

    public static boolean vanillaCritOnFullDraw() {
        return source.vanillaCritOnFullDraw();
    }

    /** The shot pipeline's numbers as this side's config has them now: what the server decides shots with. */
    public static ShotMath.Tuning tuning() {
        return source.tuning();
    }

    /**
     * The numbers the client shows (the ring, the tremble): the connected server's, or this side's own before any
     * arrive. Server code never reads these.
     */
    public static ShotMath.Tuning clientTuning() {
        ShotMath.Tuning r = remote;
        return r != null ? r : source.tuning();
    }
}
