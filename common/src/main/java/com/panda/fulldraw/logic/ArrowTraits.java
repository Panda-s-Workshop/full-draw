package com.panda.fulldraw.logic;

import java.util.function.IntPredicate;

/**
 * What an arrow's head does to a hit. Pure data, like {@link BoltTraits}; the arrow mixin applies it to the damage, the
 * armour step applies {@link #armourKept}, and the arrow itself adds the knockback.
 *
 * @param mult         damage × against an unarmoured target
 * @param armouredMult damage × against {@link #FULL_ARMOUR} or more armour points; in between the two blend linearly
 * @param armourKept   the share of the target's armour points that still counts
 * @param fixedDamage  when above 0, every hit does exactly this, whatever the bow and the shot
 * @param knockback    knockback added to the weapon's own, in Punch levels
 */
public record ArrowTraits(double mult, double armouredMult, double armourKept, float fixedDamage, float knockback) {
    /** A wide cutting head: tears into flesh, glances off plate, and the wound bleeds. */
    public static final ArrowTraits BROADHEAD = new ArrowTraits(1.25, 0.75, 1.0, 0.0F, 0.0F);
    /** A narrow armour-piercing spike: a little lighter, but 60% of the armour doesn't count. */
    public static final ArrowTraits BODKIN = new ArrowTraits(0.8, 0.8, 0.4, 0.0F, 0.0F);
    /** A padded head: always 1 damage, a hard shove, and the target's attack starts over. */
    public static final ArrowTraits BLUNT = new ArrowTraits(1.0, 1.0, 1.0, 1.0F, 2.0F);
    /** A coil of rope behind a hook: a weak hit; what it's for is the rope it hangs where it strikes. */
    public static final ArrowTraits ROPE = new ArrowTraits(0.5, 0.5, 1.0, 0.0F, 0.0F);

    /** Armour points at which {@link #armouredMult} is reached (iron armour is 15, a zombie's own 2). */
    public static final double FULL_ARMOUR = 10.0;

    /** A broadhead's bleed: {@link #BLEED_DAMAGE} every {@link #BLEED_INTERVAL} ticks, for {@link #BLEED_TICKS}. */
    public static final int BLEED_TICKS = 80;
    public static final int BLEED_INTERVAL = 20;
    public static final float BLEED_DAMAGE = 1.0F;

    /** How long a blunt arrow holds off a brain-driven mob's next attack. */
    public static final int INTERRUPT_TICKS = 20;

    /** The most rope one Rope Arrow hangs, counting the knot it ties where it strikes. */
    public static final int ROPE_LENGTH = 24;

    /** The damage × against a target with {@code armour} points. */
    public double armourMult(double armour) {
        double t = Math.clamp(armour / FULL_ARMOUR, 0.0, 1.0);
        return mult + (armouredMult - mult) * t;
    }

    /**
     * The factor this head puts on a hit that would otherwise do {@code damage} to a target with {@code armour} points:
     * a fixed-damage head turns any hit into its own amount.
     */
    public double multFor(double damage, double armour) {
        if (fixedDamage > 0) return damage > 0 ? fixedDamage / damage : 0.0;
        return armourMult(armour);
    }

    /**
     * The bleed's length after a broadhead lands on a target with {@code remaining} ticks of it left: a fresh wound is
     * {@link #BLEED_TICKS}; on an open one the count stays in step with its ticks and runs to the furthest point that
     * still gives four of them, so hits faster than a second apart don't keep putting the next tick off.
     */
    public static int bleedTicks(int remaining) {
        if (remaining <= 0) return BLEED_TICKS;
        return remaining + BLEED_INTERVAL * ((BLEED_TICKS - remaining) / BLEED_INTERVAL);
    }

    /**
     * How long a Rope Arrow's rope hangs: block {@code i} below the knot (0 is the knot) takes rope while {@code free}
     * says it's open, down to the first that isn't or to {@link #ROPE_LENGTH}.
     */
    public static int ropeLength(IntPredicate free) {
        int length = 0;
        while (length < ROPE_LENGTH && free.test(length)) length++;
        return length;
    }

    /** Whether a hit with this head counts only part of the armour. */
    public boolean piercesArmour() {
        return armourKept < 1.0;
    }
}
