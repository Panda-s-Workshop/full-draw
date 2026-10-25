package com.panda.fulldraw.logic;

/**
 * The shot pipeline's numbers. Pure functions over plain numbers so JUnit can pin
 * every rule without a game; the server calls them with the config's {@link Tuning}.
 */
public final class ShotMath {
    /** Ticks after full draw before the Perfect window opens. */
    public static final int PERFECT_DELAY = 2;
    /** Ticks the Perfect window stays open on a vanilla-like bow. */
    public static final int PERFECT_WINDOW = 4;

    /** Every tunable number of the pipeline; {@link #DEFAULTS} are the shipped values. */
    public record Tuning(int perfectWindow, int perfectDelay, double perfectMult, double precisionMobs, double precisionPlayers, double skillCap,
                         double rangeStart, double rangePerBlock, double rangeCap, double pointBlankDistance, double pointBlankMult,
                         double trembleStartSeconds, double trembleFullSeconds, double trembleMaxSpread) {}

    public static final Tuning DEFAULTS = new Tuning(PERFECT_WINDOW, PERFECT_DELAY, 1.5, 1.5, 1.25, 2.5, 20, 0.01, 1.30, 3, 0.6, 3, 6, 3.0);

    private ShotMath() {}

    /**
     * Whether a release after {@code ticksHeld} ticks of drawing lands in the Perfect window: it opens
     * {@code perfectDelay} ticks after full draw and stays open {@code perfectWindow} ticks, plus the weapon's own
     * extra ticks and any from enchantments.
     */
    public static boolean isPerfect(int ticksHeld, WeaponStats weapon, Tuning t, int extraWindow) {
        int open = weapon.drawTicks() + t.perfectDelay();
        return ticksHeld >= open && ticksHeld < open + t.perfectWindow() + weapon.extraWindow() + extraWindow;
    }

    /** Ticks the server's window opens before the ring's: client and server count use ticks a tick apart. */
    public static final int SERVER_EARLY_GRACE = 1;

    /**
     * The server's judgement: the window the ring shows, opened {@link #SERVER_EARLY_GRACE} tick early, so a release on
     * the ring's first gold tick is never denied. The ring itself draws {@link #isPerfect}.
     */
    public static boolean isPerfectOnServer(int ticksHeld, WeaponStats weapon, Tuning t, int extraWindow) {
        return isPerfect(ticksHeld, weapon, t, extraWindow) || isPerfect(ticksHeld + SERVER_EARLY_GRACE, weapon, t, extraWindow);
    }

    /** Where the release stands for the HUD ring: below 1 drawing, 1 full, and whether the Perfect window is open. */
    public static double drawFraction(int ticksHeld, WeaponStats weapon) {
        return Math.min(1.0, ticksHeld / (double) weapon.drawTicks());
    }

    /** Vanilla's arrow damage before its random crit bonus: {@code ceil(speed * baseDamage)}. */
    public static int vanillaDamage(double speed, double baseDamage) {
        return (int) Math.ceil(Math.clamp(speed * baseDamage, 0.0, Integer.MAX_VALUE));
    }

    /** +{@code rangePerBlock} per block past {@code rangeStart}, capped at {@code rangeCap}. */
    public static double rangeMult(double distance, Tuning t) {
        return Math.min(t.rangeCap(), 1.0 + t.rangePerBlock() * Math.max(0.0, distance - t.rangeStart()));
    }

    /** The point-blank penalty: shots from closer than {@code pointBlankDistance} lose damage, so melee wins up close. */
    public static double pointBlankMult(double distance, Tuning t) {
        return distance < t.pointBlankDistance() ? t.pointBlankMult() : 1.0;
    }

    /** The skill multiplier: Perfect × precision × range, never above the cap. */
    public static double skillMult(boolean perfect, double precisionMult, double rangeMult, Tuning t) {
        return Math.min(t.skillCap(), (perfect ? t.perfectMult() : 1.0) * precisionMult * rangeMult);
    }

    /**
     * The final damage of a player's arrow.
     *
     * @param vanillaDamage {@link #vanillaDamage} at impact (already includes Power and other enchantments' damage)
     * @param weaponMult    the weapon's {@link WeaponStats#damageMult()}
     * @param arrowMult     the arrow type's multiplier against this target
     * @param precisionMult 1, or the precision multiplier when the head or a weak spot was hit
     * @param distance      blocks from the release point to the impact
     */
    public static double damage(int vanillaDamage, double weaponMult, double arrowMult, boolean perfect, double precisionMult,
                                double distance, Tuning t) {
        return breakdown(vanillaDamage, weaponMult, arrowMult, perfect, precisionMult, distance, t).total();
    }

    /**
     * Every factor of a shot's damage, for {@code /fulldraw stats}: base × weapon × the head's penalty × skill
     * (Perfect × precision × range × the head's bonus, capped) × point-blank: a head's bonus can't lift a shot past the
     * cap, and a capped shot still pays its penalty.
     */
    public record Breakdown(int base, double weaponMult, double arrowMult, double perfectMult, double precisionMult, double rangeMult,
                            double skillCap, double pointBlankMult, double distance) {
        /** Perfect × precision × range × the arrow head's bonus, before the cap. */
        public double skillRaw() {
            return perfectMult * precisionMult * rangeMult * headBonus();
        }

        /** What the head adds (above ×1): it counts toward the skill cap, so it can't lift a shot past it. */
        public double headBonus() {
            return Math.max(1.0, arrowMult);
        }

        /** What the head takes (below ×1): it applies after the cap, so a capped shot still pays it. */
        public double headPenalty() {
            return Math.min(1.0, arrowMult);
        }

        public double skillMult() {
            return Math.min(skillCap, skillRaw());
        }

        public boolean capped() {
            return skillRaw() > skillCap;
        }

        public double total() {
            return base * weaponMult * headPenalty() * skillMult() * pointBlankMult;
        }

        public Breakdown withArrowMult(double mult) {
            return new Breakdown(base, weaponMult, mult, perfectMult, precisionMult, rangeMult, skillCap, pointBlankMult, distance);
        }
    }

    public static Breakdown breakdown(int vanillaDamage, double weaponMult, double arrowMult, boolean perfect, double precisionMult,
                                      double distance, Tuning t) {
        return new Breakdown(vanillaDamage, weaponMult, arrowMult, perfect ? t.perfectMult() : 1.0, precisionMult, rangeMult(distance, t),
                t.skillCap(), pointBlankMult(distance, t), distance);
    }

    /** Below this horizontal speed (squared, blocks per tick) a shooter counts as standing still. */
    public static final double STILL_SPEED_SQR = 1.0e-4;

    /**
     * Position rules for spread: sprinting ×2 and airborne ×2 (both: ×4); standing still or sneaking ×0.5,
     * only when neither of those applies.
     */
    public static double movementMult(boolean sprinting, boolean airborne, boolean sneaking, double horizontalSpeedSqr) {
        double m = 1.0;
        if (sprinting) m *= 2.0;
        if (airborne) m *= 2.0;
        if (m == 1.0 && (sneaking || horizontalSpeedSqr < STILL_SPEED_SQR)) m = 0.5;
        return m;
    }

    /**
     * The launch inaccuracy (vanilla units). Perfect shots fly true; past {@code trembleStartSeconds} of holding a full
     * draw the spread grows linearly to {@code trembleMaxSpread} at {@code trembleFullSeconds}.
     *
     * @param ticksPastFull ticks held after full draw (0 or less while still drawing)
     * @param movementMult  1 normally; the position rules (3.5) scale it
     */
    public static double spread(WeaponStats weapon, boolean perfect, int ticksPastFull, double movementMult, Tuning t) {
        return spread(weapon, perfect, ticksPastFull, movementMult, false, -1, t);
    }

    /** Steady Aim halves the base spread while the breath is held. */
    public static final double FOCUS_SPREAD = 0.5;

    /**
     * The launch inaccuracy with Steady Aim: while focusing, half the weapon's spread and no tremble; once the
     * breath has run out with the focus still held, the tremble starts at once, as if the hold had just reached
     * {@code trembleStartSeconds}.
     *
     * @param exhaustedTicks ticks since the breath ran out, or -1 when it hasn't
     */
    public static double spread(WeaponStats weapon, boolean perfect, int ticksPastFull, double movementMult, boolean focused,
                                int exhaustedTicks, Tuning t) {
        if (perfect) return 0.0;
        double base = weapon.spread();
        if (focused) return base * FOCUS_SPREAD * movementMult;
        double k = tremble(weapon, ticksPastFull, exhaustedTicks, t);
        return (base + (Math.max(base, t.trembleMaxSpread()) - base) * k) * movementMult;
    }

    /**
     * How far the tremble has grown, 0 to 1: nothing for {@code trembleStartSeconds} after full draw, then
     * linear to full at {@code trembleFullSeconds}. Running out of breath moves the clock forward to the tremble's start,
     * never back. The client's sway reads the same number.
     */
    public static double tremble(WeaponStats weapon, int ticksPastFull, int exhaustedTicks, Tuning t) {
        double seconds = Math.max(0, ticksPastFull) / 20.0;
        if (exhaustedTicks >= 0) seconds = Math.max(seconds, t.trembleStartSeconds() + exhaustedTicks / 20.0);
        if (seconds <= t.trembleStartSeconds()) return 0.0;
        return Math.min(1.0, (seconds - t.trembleStartSeconds()) / (t.trembleFullSeconds() - t.trembleStartSeconds()));
    }
}
