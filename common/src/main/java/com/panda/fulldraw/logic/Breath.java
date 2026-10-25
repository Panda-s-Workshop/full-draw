package com.panda.fulldraw.logic;

/**
 * Steady Aim's breath, as pure state: sneaking at full draw holds the breath, which drains over
 * {@link #capacityTicks}. It refills in {@link #REFILL_TICKS} only between draws, so a draw gets what the lungs held
 * when it started: standing up and sneaking again can't buy a fresh breath. When it runs out, the
 * archer is spent for the rest of the draw: no focus, and the tremble starts at once (counted by {@link #exhaustedTicks}).
 *
 * @param fill           breath left, 0 to 1
 * @param focusing       the breath is being held this tick
 * @param exhaustedTicks ticks since the breath ran out during this draw, or -1
 */
public record Breath(double fill, boolean focusing, int exhaustedTicks) {
    public static final Breath FULL = new Breath(1.0, false, -1);
    /** 2.5 s of breath, +1 s per Steady level (proposed). */
    public static final int BASE_TICKS = 50, TICKS_PER_STEADY = 20;
    /** From empty to full in 4 s between draws. */
    public static final int REFILL_TICKS = 80;
    /** A new breath needs this much in the lungs: tapping sneak can't start one every other tick. */
    public static final double MIN_INHALE = 0.2;

    public static int capacityTicks(int steadyLevel) {
        return BASE_TICKS + TICKS_PER_STEADY * Math.clamp(steadyLevel, 0, EnchantmentRules.STEADY_MAX);
    }

    public boolean exhausted() {
        return exhaustedTicks >= 0;
    }

    /**
     * One tick.
     *
     * @param wantsFocus sneaking while holding a full draw
     * @param drawing    a bow is being drawn (at any stage)
     */
    public Breath tick(boolean wantsFocus, boolean drawing, int steadyLevel) {
        if (!drawing) return fill + 1.0 / REFILL_TICKS >= 1.0 - 1e-9 ? FULL : new Breath(fill + 1.0 / REFILL_TICKS, false, -1);
        if (exhausted()) return new Breath(fill, false, exhaustedTicks + 1);
        if (!wantsFocus) return focusing ? new Breath(fill, false, -1) : this;
        if (!focusing && fill < MIN_INHALE) return this;
        double left = fill - 1.0 / capacityTicks(steadyLevel);
        if (left <= 1e-9) return new Breath(0.0, false, 0);
        return new Breath(left, true, -1);
    }

    /** What changed between two ticks, for the sounds: a breath drawn in, or let out because it ran out. */
    public enum Event { NONE, INHALE, EXHALE }

    public static Event event(Breath before, Breath after) {
        if (!before.focusing() && after.focusing()) return Event.INHALE;
        if (!before.exhausted() && after.exhausted()) return Event.EXHALE;
        return Event.NONE;
    }
}
