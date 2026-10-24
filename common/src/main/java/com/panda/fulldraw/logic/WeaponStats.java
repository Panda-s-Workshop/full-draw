package com.panda.fulldraw.logic;

/**
 * A ranged weapon's numbers. Pure data: the shot pipeline reads it, the items build their components
 * from it, and the balance tests pin it.
 *
 * @param drawTicks     ticks to full draw (vanilla bow 20)
 * @param damageMult    multiplies the arrow's vanilla damage
 * @param velocityMult  multiplies vanilla's launch speed (bow 3.0 at full draw)
 * @param spread        inaccuracy in vanilla units (the bow passes 1.0 to {@code Projectile.shoot})
 * @param extraWindow   ticks the Perfect Release window stays open beyond the configured base (the Recurve's wide window)
 * @param gravityMult   multiplies the arrow's gravity (a flatter arc below 1)
 */
public record WeaponStats(int drawTicks, double damageMult, double velocityMult, double spread, int extraWindow, double gravityMult) {
    /** The vanilla bow, and any bow from another mod that Full Draw doesn't know. */
    public static final WeaponStats VANILLA_BOW = new WeaponStats(20, 1.0, 1.0, 1.0, 0, 1.0);
    public static final WeaponStats SHORTBOW = new WeaponStats(12, 0.7, 2.6 / 3.0, 1.5, 0, 1.0);
    /**
     * Slow, heavy and true: a long draw, hard hits, a tight spread and a flatter arc. ×1.2, not more: its fast arrow
     * already starts from 8, and 8 × 1.2 × the 2.5 skill cap = 24 is the most that still leaves an iron-armoured zombie
     * standing.
     */
    public static final WeaponStats LONGBOW = new WeaponStats(32, 1.2, 3.8 / 3.0, 0.6, 0, 0.75);
    /** A little slower and harder than vanilla's bow, with a Perfect window two ticks wider. */
    public static final WeaponStats RECURVE_BOW = new WeaponStats(18, 1.15, 3.2 / 3.0, 0.9, 2, 1.0);

    /** Converts ticks held on this weapon to the ticks a vanilla bow would need for the same draw fraction. */
    public int toVanillaTicks(int ticksHeld) {
        return (int) Math.round(ticksHeld * (double) VANILLA_BOW.drawTicks / drawTicks);
    }
}
