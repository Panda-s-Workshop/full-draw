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
    /** Vanilla's crossbow: 25 ticks to load. A crossbow's velocityMult scales vanilla's 3.15 for a bolt. */
    public static final WeaponStats VANILLA_CROSSBOW = new WeaponStats(25, 1.0, 1.0, 1.0, 0, 1.0);
    /** The siege crossbow: a 3 s crank, bolts at 3.6 for ×1.6 with a 0.4 spread. Its bolts' extras are {@link BoltTraits#ARBALEST}. */
    public static final WeaponStats ARBALEST = new WeaponStats(60, 1.6, 3.6 / 3.15, 0.4, 0, 1.0);
    /**
     * The burst crossbow: a 2 s crank loads a magazine ({@link Magazine#REPEATING_CROSSBOW}), each bolt at 3.0 for ×0.6
     * with a 2.0 spread. A bolt hits for ceil(speed × 1.2) = 4 at any speed from 2.5 to 3.33, so the spread's swing in
     * speed (a tenth at most) doesn't make some bolts 3 or 5. A magazine of 20 out-damages a vanilla crossbow over the
     * same 2.4 s and stays under two Arbalest bolts; fired back to back it deals about 0.6 × a vanilla crossbow's damage a
     * minute.
     */
    public static final WeaponStats REPEATING_CROSSBOW = new WeaponStats(40, 0.6, 3.0 / 3.15, 2.0, 0, 1.0);
    public static final WeaponStats SHORTBOW = new WeaponStats(12, 0.7, 2.6 / 3.0, 1.5, 0, 1.0);
    /**
     * Slow, heavy and true: a long draw, hard hits, a tight spread and a flatter arc. ×1.2, not more: its fast arrow
     * already starts from 8, and 8 × 1.2 × the 2.5 skill cap = 24 is the most that still leaves an iron-armoured zombie
     * standing.
     */
    public static final WeaponStats LONGBOW = new WeaponStats(32, 1.2, 3.8 / 3.0, 0.6, 0, 0.75);
    /**
     * The forgiving bow: a Perfect window two ticks wider, a little quicker to draw (18 ticks, not 20) and a little
     * harder, at vanilla's launch speed. Not faster too: its arrow at 3.2 hits for 7, and an untimed full draw every 18
     * ticks would then out-damage a vanilla bow that never misses its Perfect window.
     */
    public static final WeaponStats RECURVE_BOW = new WeaponStats(18, 1.15, 1.0, 0.9, 2, 1.0);

    /** Vanilla's crossbow load in seconds, the time Quick Charge shortens. */
    public static final float VANILLA_CHARGE_SECONDS = 1.25F;

    /**
     * This crossbow's load in ticks when Quick Charge has cut vanilla's 1.25 s to {@code vanillaSeconds}: cut by the same
     * share, so each level takes a fifth of the Arbalest's 3 s as it does of vanilla's 1.25 s. A flat 0.25 s a level
     * would barely touch a long crank, and at Quick Charge III a vanilla crossbow would out-damage the Arbalest even
     * through armour.
     */
    public int chargeTicks(float vanillaSeconds) {
        return (int) Math.floor(drawTicks * (double) Math.max(0.0F, vanillaSeconds) / VANILLA_CHARGE_SECONDS);
    }

    /** Converts ticks held on this weapon to the ticks a vanilla bow would need for the same draw fraction. */
    public int toVanillaTicks(int ticksHeld) {
        return (int) Math.round(ticksHeld * (double) VANILLA_BOW.drawTicks / drawTicks);
    }
}
