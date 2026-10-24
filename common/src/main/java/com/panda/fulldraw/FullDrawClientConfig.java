package com.panda.fulldraw;

/**
 * Each player's own presentation settings: nothing here changes what the server decides. Each
 * loader keeps them in its client config and plugs them in with {@link #use}; a dedicated server keeps the defaults.
 */
public final class FullDrawClientConfig {
    /** Where the Quiver strip sits: left of the hotbar (beyond the offhand slot), right of it, or not at all. */
    public enum QuiverStrip { LEFT, RIGHT, HIDDEN }

    public interface Source {
        /** The draw ring, the hit tick on the crosshair and early/late under it. */
        boolean showRing();

        /** "Long shot: N m" on the action bar. */
        boolean distanceReadout();

        /** Every bow's zoom, Steady Aim's narrowing and the release kick (vanilla's FOV-effects slider still scales them). */
        boolean fovEffects();

        /** The particle trail behind Perfect arrows. */
        boolean perfectTrail();

        /** The draw creak, the strain under tremble, the twang on release and the archer's own breath. */
        boolean drawSounds();

        /** The chime and the flash at the bow of your own Perfect Release (others still hear yours). */
        boolean perfectChime();

        /** The hit tick and the precision thock. */
        boolean hitSounds();

        /** The bow shaking in the hand under tremble. */
        boolean bowShake();

        QuiverStrip quiverStrip();
    }

    public static final Source DEFAULTS = new Source() {
        @Override public boolean showRing() { return true; }
        @Override public boolean distanceReadout() { return true; }
        @Override public boolean fovEffects() { return true; }
        @Override public boolean perfectTrail() { return true; }
        @Override public boolean drawSounds() { return true; }
        @Override public boolean perfectChime() { return true; }
        @Override public boolean hitSounds() { return true; }
        @Override public boolean bowShake() { return true; }
        @Override public QuiverStrip quiverStrip() { return QuiverStrip.LEFT; }
    };

    private static Source source = DEFAULTS;

    private FullDrawClientConfig() {}

    public static void use(Source loaderConfig) {
        source = loaderConfig;
    }

    public static Source get() {
        return source;
    }
}
