package com.panda.fulldraw.logic;

/**
 * The Marksman's numbers and rules, apart from the entity: when it draws and how long, when its cover screens it, what
 * it looses, and how it aims an arrow that drops on its way.
 */
public final class MarksmanRules {
    private MarksmanRules() {}

    public static final float MAX_HEALTH = 100.0F;
    /** Below this share of its health it fights in its second phase. */
    public static final double PHASE_TWO_AT = 0.5;
    /** Ticks from the creak of its draw to the release: the warning a player gets. */
    public static final int DRAW_TICKS = 30;
    /** In its second phase it draws faster. */
    public static final int DRAW_TICKS_PHASE_TWO = 24;
    /** Ticks it stays down behind the parapet between shots, moving to the next crenel. */
    public static final int COVER_TICKS = 40;
    /** The most it takes to step up into a crenel's gap before it draws where it is. */
    public static final int RISE_TICKS = 30;
    /** If it can't reach the crenel it means to shoot from in this long, it shoots from where it is. */
    public static final int STUCK_TICKS = 100;
    /** Knocked off its tower (an explosion, a wind charge) for this long, it climbs back to its post. */
    public static final int STRANDED_TICKS = 100;
    /** A foe this close on the battlements gets the bow's end, not an arrow: a blow and a shove, once a second. */
    public static final double BASH_REACH = 3.0;
    public static final int BASH_COOLDOWN = 20;
    /** Everyone this near sees its boss bar. */
    public static final double BAR_RANGE = 48.0;
    /** How far it looks for someone to duel. */
    public static final double SIGHT = 48.0;
    /** How long a target may be out of sight before, in its second phase, it shoots at where they hid. */
    public static final int HIDDEN_TICKS = 60;
    /**
     * It shoots a vanilla bow, always at a Perfect Release, at the full draw's speed, with an arrow lighter than a plain
     * one (base damage 1.3, not 2: vanilla's 4 at a full draw, not 6): on Hard a mob's arrow deals half again, and a
     * Perfect headshot from afar must not take a bare player from full health. Its Recurve Bow is what it leaves, not what it shoots.
     */
    public static final WeaponStats BOW = WeaponStats.VANILLA_BOW;
    public static final double ARROW_SPEED = 3.0 * BOW.velocityMult();
    public static final double ARROW_BASE_DAMAGE = 1.3;
    /** An arrow's gravity and drag, as vanilla's. */
    public static final double ARROW_GRAVITY = 0.05;
    public static final double ARROW_DRAG = 0.99;
    /** The bow's end, for a foe on the battlements with it. */
    public static final double BASH_DAMAGE = 3.0;

    /** The numbers a server can change: its health, its draw (and below half health), its arrow's base damage, its blow. */
    public record Tuning(float health, int drawTicks, int drawTicksPhaseTwo, double arrowBaseDamage, double bashDamage) {
        public int drawTicks(boolean phaseTwo) {
            return phaseTwo ? drawTicksPhaseTwo : drawTicks;
        }
    }

    public static final Tuning DEFAULTS = new Tuning(MAX_HEALTH, DRAW_TICKS, DRAW_TICKS_PHASE_TWO, ARROW_BASE_DAMAGE, BASH_DAMAGE);

    /** What its arrow deals on striking a player this far off, before armour: a Perfect shot, to the head or not. */
    public static double arrowDamage(double distance, boolean head, ShotMath.Tuning t) {
        return ShotMath.damage(ShotMath.vanillaDamage(ARROW_SPEED, ARROW_BASE_DAMAGE), BOW.damageMult(), 1.0, true, head ? t.precisionPlayers() : 1.0,
                distance, t);
    }

    public static int drawTicks(boolean phaseTwo) {
        return phaseTwo ? DRAW_TICKS_PHASE_TWO : DRAW_TICKS;
    }

    public static boolean phaseTwo(float health, float maxHealth) {
        return health <= maxHealth * PHASE_TWO_AT;
    }

    /**
     * Whether its cover turns a blow away: while it is down behind the parapet, a blow from beyond the parapet lands
     * only on its head. One from the battlements behind it, or from nowhere (a command, the void), always lands.
     *
     * @param covered   down behind the parapet
     * @param precision the blow struck its head
     * @param beyond    how far beyond the parapet's inner face the blow came from, along the way out (for a projectile,
     *                  its speed inward across the parapet); zero or less when it came from the battlements or nowhere
     */
    public static boolean screens(boolean covered, boolean precision, double beyond) {
        return covered && !precision && beyond > 0.0;
    }

    public enum Shot { ARROW, FLARE, BLAST, NONE }

    /**
     * What it looses: an arrow at a target it can see; at one in hiding, in its second phase, a flare to light them up,
     * then, once they glow, a blast at their cover. In its first phase it waits for them to show again.
     */
    public static Shot shot(boolean phaseTwo, boolean seen, boolean glowing) {
        if (seen) return Shot.ARROW;
        if (!phaseTwo) return Shot.NONE;
        return glowing ? Shot.BLAST : Shot.FLARE;
    }

    /**
     * The pitch (radians, up is positive) that carries an arrow loosed at {@code speed} to {@code distance} blocks off
     * horizontally at {@code rise} blocks above the release (negative below), on the flatter of its two arcs; NaN when
     * it can't reach.
     */
    public static double pitch(double distance, double rise, double speed, double gravity) {
        // the flatter arc lies below the pitch that lifts the arrow highest at this distance: find that in whole degrees
        double lo = Math.toRadians(-85.0), hi = Double.NaN, best = Double.NEGATIVE_INFINITY;
        for (int degrees = -85; degrees <= 85; degrees++) {
            double h = heightAt(distance, Math.toRadians(degrees), speed, gravity);
            if (!Double.isNaN(h) && h > best) {
                best = h;
                hi = Math.toRadians(degrees);
            }
        }
        if (Double.isNaN(hi) || best < rise) return Double.NaN;
        for (int i = 0; i < 60; i++) {
            double mid = (lo + hi) / 2;
            double h = heightAt(distance, mid, speed, gravity);
            if (Double.isNaN(h) || h < rise) lo = mid; else hi = mid;
        }
        return (lo + hi) / 2;
    }

    /** Ticks an arrow loosed at this pitch takes to fly {@code distance} blocks horizontally; -1 if it never does. */
    public static int flightTicks(double distance, double pitch, double speed) {
        double vx = speed * Math.cos(pitch), x = 0;
        for (int tick = 1; tick <= 400; tick++) {
            x += vx;
            if (x >= distance) return tick;
            vx *= ARROW_DRAG;
        }
        return -1;
    }

    /**
     * The height an arrow loosed at this pitch has when it has flown {@code distance} blocks horizontally, tick by tick as
     * vanilla moves it (move, then drag, then gravity); NaN if it falls 200 blocks first.
     */
    public static double heightAt(double distance, double pitch, double speed, double gravity) {
        double vx = speed * Math.cos(pitch), vy = speed * Math.sin(pitch);
        double x = 0, y = 0;
        for (int tick = 0; tick < 400; tick++) {
            double nx = x + vx, ny = y + vy;
            if (nx >= distance) return y + (ny - y) * (distance - x) / (nx - x);
            x = nx;
            y = ny;
            if (y < -200) return Double.NaN;
            vx *= ARROW_DRAG;
            vy = vy * ARROW_DRAG - gravity;
        }
        return Double.NaN;
    }
}
