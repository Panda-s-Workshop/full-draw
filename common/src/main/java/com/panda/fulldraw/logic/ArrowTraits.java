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
 * @param recovery     the chance one of these stuck in a mob drops back when it dies, at the default {@link #PLAIN_RECOVERY}
 */
public record ArrowTraits(double mult, double armouredMult, double armourKept, float fixedDamage, float knockback, double recovery) {
    /** A wide cutting head: tears into flesh, glances off plate, and the wound bleeds. */
    public static final ArrowTraits BROADHEAD = new ArrowTraits(1.25, 0.75, 1.0, 0.0F, 0.0F, 0.25);
    /** A narrow armour-piercing spike: a little lighter, but 60% of the armour doesn't count. */
    public static final ArrowTraits BODKIN = new ArrowTraits(0.8, 0.8, 0.4, 0.0F, 0.0F, 0.4);
    /** A padded head: always 1 damage, a hard shove, and the target's attack starts over. */
    public static final ArrowTraits BLUNT = new ArrowTraits(1.0, 1.0, 1.0, 1.0F, 2.0F, 1.0);
    /** A coil of rope behind a hook: a weak hit; what it's for is the rope it hangs where it strikes. */
    public static final ArrowTraits ROPE = new ArrowTraits(0.5, 0.5, 1.0, 0.0F, 0.0F, 0.4);
    /** A burning head: a weak hit; what it's for is the light and the glow where it lands. */
    public static final ArrowTraits FLARE = new ArrowTraits(0.5, 0.5, 1.0, 0.0F, 0.0F, 0.0);
    /** A charge behind the head: a weak hit, then the explosion. */
    public static final ArrowTraits BLAST = new ArrowTraits(0.5, 0.5, 1.0, 0.0F, 0.0F, 0.0);

    /**
     * The chance a plain arrow (or any arrow without one of these heads) stuck in a mob drops back when the mob dies, by
     * default (config {@code arrowRecoveryChance}).
     */
    public static final double PLAIN_RECOVERY = 0.4;

    /**
     * By default an arrow a player can pick up stays in a block for 5 minutes; all others keep vanilla's minute (config
     * {@code arrowPickupLifetimeSeconds}).
     */
    public static final int PICKUP_LIFETIME_SECONDS = 300;
    /** The longest lifetime the config takes: an arrow's age is saved as a short (32767 ticks). */
    public static final int MAX_PICKUP_LIFETIME_SECONDS = 1638;

    /**
     * The chance an arrow whose head recovers {@code recovery} at the default comes back when the plain arrow's is
     * {@code plain}: every head keeps its ratio to the plain arrow's, up to certain, so 0 turns recovery off.
     */
    public static double recoveryChance(double recovery, double plain) {
        return Math.clamp(recovery * plain / PLAIN_RECOVERY, 0.0, 1.0);
    }

    /** Armour points at which {@link #armouredMult} is reached (iron armour is 15, a zombie's own 2). */
    public static final double FULL_ARMOUR = 10.0;

    /** A broadhead's bleed: {@link #BLEED_DAMAGE} every {@link #BLEED_INTERVAL} ticks, for {@link #BLEED_TICKS}. */
    public static final int BLEED_TICKS = 80;
    public static final int BLEED_INTERVAL = 20;
    public static final float BLEED_DAMAGE = 1.0F;

    /** How long a blunt arrow holds off a brain-driven mob's next attack. */
    public static final int INTERRUPT_TICKS = 20;

    /** The most rope one Rope Arrow hangs by default, counting the knot it ties where it strikes (config {@code ropeArrowLength}). */
    public static final int ROPE_LENGTH = 24;

    /** A Flare Arrow's light burns this long (30 s), and mobs within {@link #FLARE_GLOW_RADIUS} glow {@link #FLARE_GLOW_TICKS}. */
    public static final int FLARE_LIGHT_TICKS = 600;
    public static final double FLARE_GLOW_RADIUS = 8.0;
    public static final int FLARE_GLOW_TICKS = 200;

    /** A Blast Arrow's explosion: a creeper's is 3, TNT's 4. */
    public static final float BLAST_POWER = 1.5F;
    /** A Blast Arrow's explosion hurts creatures this much of what a vanilla explosion of its power does. */
    public static final double BLAST_CREATURE_DAMAGE = 0.5;

    /**
     * What a Blast Arrow's explosion does to a creature {@code distance} blocks from its centre, {@code seen} (0 to 1)
     * of it in the open: vanilla's explosion damage at {@link #BLAST_POWER}, times {@link #BLAST_CREATURE_DAMAGE}. The
     * creature the arrow struck takes it as at the centre, fully seen, on top of the arrow's own hit.
     */
    public static double blastDamage(double distance, double seen) {
        double diameter = BLAST_POWER * 2.0;
        if (distance >= diameter) return 0.0;
        double impact = (1.0 - distance / diameter) * seen;
        return ((impact * impact + impact) / 2.0 * 7.0 * diameter + 1.0) * BLAST_CREATURE_DAMAGE;
    }

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
     * says it's open, down to the first that isn't or to {@code max}.
     */
    public static int ropeLength(IntPredicate free, int max) {
        int length = 0;
        while (length < max && free.test(length)) length++;
        return length;
    }

    /** Whether a hit with this head counts only part of the armour. */
    public boolean piercesArmour() {
        return armourKept < 1.0;
    }
}
