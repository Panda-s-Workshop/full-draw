package com.panda.fulldraw.logic;

/**
 * The Bog Lurker's rules. It lies in swamp water with only its eyes above the surface and spits a slowing glob at a
 * player 8 to 16 blocks off; closer than 4 it sinks and swims away. Its eyes are its weak spot; a hit on its body
 * under the water does half.
 */
public final class LurkerRules {
    /** It spits at a player at least this far off... */
    public static final double SPIT_MIN = 8.0;
    /** ...and at most this far. */
    public static final double SPIT_MAX = 16.0;
    /** A player closer than this sends it under and away. */
    public static final double FLEE_WITHIN = 4.0;
    /** It swims away until the player is this far off... */
    public static final double FLEE_UNTIL = 10.0;
    /** ...or for this many ticks at most, then comes back up. */
    public static final int FLEE_MAX_TICKS = 80;
    /** Struck from beyond its spitting range, where it can't answer, it goes under and swims off for this many ticks. */
    public static final int HIT_DIVE_TICKS = 60;
    /** Ticks from one spit to the next, at least... */
    public static final int SPIT_GAP_MIN = 50;
    /** ...and at most (2.5 to 3.5 s). */
    public static final int SPIT_GAP_MAX = 70;
    /** The tell before a spit: its throat swells and gurgles for this many ticks. */
    public static final int SPIT_TELL_TICKS = 12;
    /** The glob's damage, and the Slowness I it leaves, in ticks. */
    public static final float SPIT_DAMAGE = 2.0F;
    public static final int SPIT_SLOW_TICKS = 60;
    /** The glob's speed, blocks a tick, and its drop each tick (a thrown item's). */
    public static final double SPIT_SPEED = 1.1;
    public static final double SPIT_GRAVITY = 0.03;
    /** What a hit on its body under the water does, against a hit in the air. */
    public static final double SUBMERGED_BODY_MULT = 0.5;
    /** Lurking, the top of its box shows this far above the surface: its eyes stand above that. */
    public static final double SHOWS_ABOVE = 0.2;
    /** Fleeing, the top of its box sinks this far below the surface. */
    public static final double SINK_BELOW = 1.6;
    /** It swims away at this speed, blocks a tick... */
    public static final double SWIM_SPEED = 0.2;
    /** ...and backs off along the surface at this one when a player is inside its spitting range. */
    public static final double DRIFT_SPEED = 0.05;
    /** It rises and sinks at most this fast. */
    public static final double DEPTH_SPEED = 0.08;

    public enum Act { FLEE, BACK_OFF, SPIT, WATCH }

    private LurkerRules() {}

    /** What it does about a player this far off. */
    public static Act act(double distance) {
        if (distance < FLEE_WITHIN) return Act.FLEE;
        if (distance < SPIT_MIN) return Act.BACK_OFF;
        if (distance <= SPIT_MAX) return Act.SPIT;
        return Act.WATCH;
    }

    /** The ticks to its next spit for a roll in [0, 1). */
    public static int spitGap(double roll) {
        return SPIT_GAP_MIN + (int) (roll * (SPIT_GAP_MAX - SPIT_GAP_MIN + 1));
    }

    /** Where its feet go to lurk with only its eyes above water whose surface is at {@code surface}. */
    public static double lurkY(double surface, double height) {
        return surface + SHOWS_ABOVE - height;
    }

    /** Where its feet go to swim under water whose surface is at {@code surface}. */
    public static double sunkY(double surface, double height) {
        return surface - SINK_BELOW - height;
    }

    /**
     * How far above a straight line at the target to aim a glob that drops {@link #SPIT_GRAVITY} a tick, so it arrives at
     * the target's height {@code distance} blocks off.
     */
    public static double spitLift(double distance) {
        double ticks = distance / SPIT_SPEED;
        return 0.5 * SPIT_GRAVITY * ticks * ticks;
    }
}
