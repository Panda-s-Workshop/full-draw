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

    public static boolean vanillaCritOnFullDraw() {
        return source.vanillaCritOnFullDraw();
    }

    /**
     * The shot pipeline's numbers: the server's on a client connected to one, this side's own otherwise. In singleplayer
     * both sides read the same file, so the two agree.
     */
    public static ShotMath.Tuning tuning() {
        ShotMath.Tuning r = remote;
        return r != null ? r : source.tuning();
    }

    /** This side's own numbers, never the remote ones: what a server sends. */
    public static ShotMath.Tuning localTuning() {
        return source.tuning();
    }
}
