package com.panda.fulldraw;

import com.panda.fulldraw.logic.ArrowTraits;
import com.panda.fulldraw.logic.MarksmanRules;
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

        /** Blast Arrows break blocks (as TNT, where tnt_explodes allows; a mob's under mobGriefing); false: they only hurt and knock back. */
        boolean blastArrowBreaksBlocks();

        /** The most rope one Rope Arrow hangs, counting its knot. */
        int ropeArrowLength();

        /** The chance a plain arrow stuck in a mob drops back when it dies; other heads keep their ratio to it (0: none come back). */
        double arrowRecoveryChance();

        /** How long an arrow a player can pick up stays in a block, in seconds. */
        int arrowPickupLifetimeSeconds();

        /** Shieldbearers join raids and guard pillager outposts (false: only spawn eggs and commands make them). */
        boolean shieldbearerSpawns();

        /** Gale Harriers spawn on their own over the windswept hills and peaks (false: only spawn eggs and commands make them). */
        boolean galeHarrierSpawns();

        /** Bog Lurkers spawn on their own in swamp water (false: only spawn eggs and commands make them). */
        boolean bogLurkerSpawns();

        /** The Marksman's health, draw, arrow and blow. */
        default MarksmanRules.Tuning marksman() {
            return MarksmanRules.DEFAULTS;
        }
    }

    public static final Source DEFAULTS = new Source() {
        @Override public boolean vanillaCritOnFullDraw() { return false; }
        @Override public ShotMath.Tuning tuning() { return ShotMath.DEFAULTS; }
        @Override public boolean blastArrowBreaksBlocks() { return false; }
        @Override public int ropeArrowLength() { return ArrowTraits.ROPE_LENGTH; }
        @Override public double arrowRecoveryChance() { return ArrowTraits.PLAIN_RECOVERY; }
        @Override public int arrowPickupLifetimeSeconds() { return ArrowTraits.PICKUP_LIFETIME_SECONDS; }
        @Override public boolean shieldbearerSpawns() { return true; }
        @Override public boolean galeHarrierSpawns() { return true; }
        @Override public boolean bogLurkerSpawns() { return true; }
    };

    private static Source source = DEFAULTS;
    /** The connected server's numbers, on a client; null when not connected. */
    private static ShotMath.Tuning remote;
    /** The connected server's rope length, on a client; 0 when not connected. */
    private static int remoteRopeLength;
    /** The connected server's arrow recovery chance, on a client; NaN when not connected. */
    private static double remoteRecovery = Double.NaN;

    private FullDrawConfig() {}

    public static void use(Source loaderConfig) {
        source = loaderConfig;
    }

    /** On the client: the server's numbers, or null (and 0, NaN) on disconnect. */
    public static void useRemote(ShotMath.Tuning serverTuning, int ropeLength, double recoveryChance) {
        remote = serverTuning;
        remoteRopeLength = ropeLength;
        remoteRecovery = recoveryChance;
    }

    /** The source the loader plugged in, so a test can wrap it and put it back. */
    public static Source source() {
        return source;
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

    public static boolean blastArrowBreaksBlocks() {
        return source.blastArrowBreaksBlocks();
    }

    /** The rope length the server hangs. */
    public static int ropeArrowLength() {
        return source.ropeArrowLength();
    }

    public static double arrowRecoveryChance() {
        return source.arrowRecoveryChance();
    }

    public static int arrowPickupLifetimeSeconds() {
        return source.arrowPickupLifetimeSeconds();
    }

    public static boolean shieldbearerSpawns() {
        return source.shieldbearerSpawns();
    }

    public static boolean galeHarrierSpawns() {
        return source.galeHarrierSpawns();
    }

    public static boolean bogLurkerSpawns() {
        return source.bogLurkerSpawns();
    }

    public static MarksmanRules.Tuning marksman() {
        return source.marksman();
    }

    /** Whether the client is connected to a server that sent its recovery chance, so a server with Full Draw. */
    public static boolean knowsServerRecovery() {
        return !Double.isNaN(remoteRecovery);
    }

    /** The plain arrow's recovery chance the client's tooltips state: the connected server's, or this side's own. */
    public static double clientArrowRecoveryChance() {
        return Double.isNaN(remoteRecovery) ? source.arrowRecoveryChance() : remoteRecovery;
    }

    /** The rope length the client's tooltip states: the connected server's, or this side's own. */
    public static int clientRopeArrowLength() {
        return remoteRopeLength > 0 ? remoteRopeLength : source.ropeArrowLength();
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
