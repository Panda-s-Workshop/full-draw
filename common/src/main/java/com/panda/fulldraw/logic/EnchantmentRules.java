package com.panda.fulldraw.logic;

/**
 * What Full Draw's four enchantments do, apart from the game: the shot pipeline reads each one's level and asks here.
 */
public final class EnchantmentRules {
    private EnchantmentRules() {}

    /** Deadeye: a weak spot struck counts this much more per level (×1.5 on a mob becomes ×1.65, ×1.8, ×1.95). */
    public static final double DEADEYE_PRECISION_PER_LEVEL = 0.15;
    /** Steady: the Perfect window stays open this many ticks longer per level (and the breath lasts a second longer). */
    public static final int STEADY_WINDOW_PER_LEVEL = 2;
    /** Quickdraw: the draw takes this share less per level (III: about a fifth). */
    public static final double QUICKDRAW_PER_LEVEL = 0.07;
    /** Ricochet: a bolt keeps this share of its speed off each block it bounces from. */
    public static final double RICOCHET_SPEED_KEPT = 0.7;
    /** The tiers only the Marksman's Eye makes: an anvil won't merge two lower ones into them. */
    public static final int DEADEYE_TABLE_MAX = 2, STEADY_TABLE_MAX = 1;
    /**
     * The levels the effects go up to: a book or command past them (a pack raising the max, {@code /give}) does no
     * more than the top level.
     */
    public static final int DEADEYE_MAX = 3, STEADY_MAX = 2, QUICKDRAW_MAX = 3, RICOCHET_MAX = 2;

    /** A weapon's numbers with Quickdraw and Steady on it: a shorter draw (never under a tick), a wider Perfect window. */
    public static WeaponStats apply(WeaponStats base, int quickdraw, int steady) {
        if (quickdraw <= 0 && steady <= 0) return base;
        int draw = Math.max(1, (int) Math.round(base.drawTicks() * (1.0 - QUICKDRAW_PER_LEVEL * Math.clamp(quickdraw, 0, QUICKDRAW_MAX))));
        int window = base.extraWindow() + STEADY_WINDOW_PER_LEVEL * Math.clamp(steady, 0, STEADY_MAX);
        return new WeaponStats(draw, base.damageMult(), base.velocityMult(), base.spread(), window, base.gravityMult());
    }

    public static double deadeyeBonus(int level) {
        return DEADEYE_PRECISION_PER_LEVEL * Math.clamp(level, 0, DEADEYE_MAX);
    }

    /**
     * The level an anvil may give a Marksman's Eye tier: past the table's best only if one of the two inputs already
     * carried that level (a book made from the Eye); two lower ones merge no higher than the table's best.
     */
    public static int anvilLevel(int result, int left, int right, int tableMax) {
        if (result <= tableMax) return result;
        int carried = Math.max(left, right);
        return carried >= result ? result : Math.max(tableMax, carried);
    }

    /** A velocity off a block face: mirrored across the face's normal, at the speed a ricochet keeps. */
    public static double[] bounce(double vx, double vy, double vz, int nx, int ny, int nz) {
        double dot = vx * nx + vy * ny + vz * nz;
        return new double[] {(vx - 2 * dot * nx) * RICOCHET_SPEED_KEPT, (vy - 2 * dot * ny) * RICOCHET_SPEED_KEPT,
                (vz - 2 * dot * nz) * RICOCHET_SPEED_KEPT};
    }
}
