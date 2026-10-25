package com.panda.fulldraw.logic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The weapons against each other and against melee. */
class BalanceTest {
    private static final ShotMath.Tuning T = ShotMath.DEFAULTS;

    /** A diamond sword's damage a second at full swings: 7 damage at 1.6 attacks a second. */
    private static final double DIAMOND_SWORD_DPS = 7 * 1.6;

    /**
     * Up close a sword out-damages every bow, even firing nothing but Perfect headshots back to back: the best shot two
     * blocks away, divided by the seconds from the start of the draw to the Perfect window.
     */
    @Test
    void meleeWinsUpClose() {
        for (WeaponStats w : List.of(WeaponStats.VANILLA_BOW, WeaponStats.SHORTBOW, WeaponStats.LONGBOW, WeaponStats.RECURVE_BOW)) {
            double shot = ShotMath.damage(ShotMathTest.launchBase(w), w.damageMult(), 1, true, T.precisionMobs(), 2, T);
            double seconds = (w.drawTicks() + T.perfectDelay()) / 20.0;
            double dps = shot / seconds;
            assertTrue(dps < DIAMOND_SWORD_DPS, w + ": " + dps + " damage a second at two blocks, a diamond sword does " + DIAMOND_SWORD_DPS);
        }
    }

    /**
     * Full Draw's enchantments buy a bow no more up close than Sharpness V buys a sword (7 → 10 damage, ×1.43): the best
     * of them together (Quickdraw III with Deadeye III; Steady can't join Quickdraw and adds nothing to the rate), on
     * each bow, Perfect headshots at two blocks, over the same bow bare. So the gap {@link #meleeWinsUpClose} keeps
     * between bare weapons holds between enchanted ones too. (Power is vanilla's and scales bows past this; it is not
     * Full Draw's to price here.)
     */
    @Test
    void enchantedBowsKeepMeleeAhead() {
        double sharpness = (7 + 3) / 7.0;
        for (WeaponStats w : List.of(WeaponStats.VANILLA_BOW, WeaponStats.SHORTBOW, WeaponStats.LONGBOW, WeaponStats.RECURVE_BOW)) {
            WeaponStats quick = EnchantmentRules.apply(w, EnchantmentRules.QUICKDRAW_MAX, 0);
            double bare = closeDps(w, 0), best = closeDps(quick, EnchantmentRules.deadeyeBonus(EnchantmentRules.DEADEYE_MAX));
            assertTrue(best / bare <= sharpness, w + ": enchanted ×" + best / bare + " up close, Sharpness V gives a sword ×" + sharpness);
        }
    }

    private static double closeDps(WeaponStats w, double deadeye) {
        double shot = ShotMath.damage(ShotMathTest.launchBase(w), w.damageMult(), 1, true, T.precisionMobs() + deadeye, 2, T);
        return shot / ((w.drawTicks() + T.perfectDelay()) / 20.0);
    }

    /**
     * The Arbalest trades rate for weight: its damage a minute against one target stays at most 1.2 × a vanilla
     * crossbow's, unarmoured and in full iron, and at most 1.5 × in diamond or netherite, where cutting through armour is
     * its job. A line of mobs is its other job, so this is per target. A minute is 1200 ticks of back-to-back loads.
     */
    @Test
    void arbalestTradeoff() {
        assertRatio(0, 0, 1.2);   // unarmoured
        assertRatio(15, 0, 1.2);  // full iron: 15 points, no toughness
        assertRatio(20, 8, 1.5);  // full diamond
        assertRatio(20, 12, 1.5); // full netherite
        // and up close a sword still wins against it
        double arbalest = perMinute(WeaponStats.ARBALEST, false, 0, 0, 1.0);
        assertTrue(arbalest / 60 < DIAMOND_SWORD_DPS, "Arbalest " + arbalest / 60 + " damage a second, a diamond sword does " + DIAMOND_SWORD_DPS);
    }

    /**
     * A full magazine from the Repeating Crossbow, every bolt landing, deals at most 1.5 × one Arbalest bolt (a burst,
     * not a heavier hit) and at least one Arbalest bolt (else it has no reason to exist). Plain arrows, no crit on either.
     * That every bolt does land on one target is the GameTest {@code repeater_burst_lands}.
     */
    @Test
    void clankOnlyWhenTheBoltGetsThroughRealArmour() {
        double arbalest = BoltTraits.ARBALEST.armourKept();
        assertTrue(BoltTraits.clanks(arbalest, 12.0F, 8.0F), "an Arbalest bolt into an iron chestplate clanks");
        assertFalse(BoltTraits.clanks(arbalest, 0.0F, 8.0F), "a bolt a shield stopped reaches the armour with 0: no clank");
        assertFalse(BoltTraits.clanks(arbalest, 12.0F, 2.0F), "a husk's own 2 points: no clank");
        assertFalse(BoltTraits.clanks(BoltTraits.NONE.armourKept(), 12.0F, 20.0F), "a plain bolt ignores no armour: no clank");
        assertTrue(BoltTraits.clanks(ArrowTraits.BODKIN.armourKept(), 4.0F, 8.0F), "a bodkin bites through armour too");
    }

    @Test
    void repeaterBurst() {
        int bolt = ShotMath.vanillaDamage(3.15 * WeaponStats.REPEATING_CROSSBOW.velocityMult(), 2.0 * WeaponStats.REPEATING_CROSSBOW.damageMult());
        int magazine = bolt * Magazine.REPEATING_CROSSBOW.shots();
        int arbalest = ShotMath.vanillaDamage(3.15 * WeaponStats.ARBALEST.velocityMult(), 2.0 * WeaponStats.ARBALEST.damageMult());
        assertTrue(magazine <= 1.5 * arbalest, "a magazine deals " + magazine + ", one Arbalest bolt " + arbalest);
        assertTrue(magazine >= arbalest, "a magazine deals " + magazine + ", less than one Arbalest bolt's " + arbalest);
    }

    /**
     * A Blast Arrow rewards the hit: a creature it strikes takes the arrow's hit and then the explosion in full, so a body
     * hit beats a shot into the ground at the feet and a head hit beats the body, from every bow. And it is no single-target
     * upgrade: its best hit from any bow stays at or under the best plain arrow in the mod, a Perfect Longbow head hit.
     */
    @Test
    void blastRewardsTheHit() {
        double feet = ArrowTraits.blastDamage(0.0, 1.0);
        WeaponStats l = WeaponStats.LONGBOW;
        double bestPlain = ShotMath.damage(ShotMathTest.launchBase(l), l.damageMult(), 1, true, T.precisionMobs(), 200, T);
        double blastMult = ArrowTraits.BLAST.multFor(6.0, 0);
        for (WeaponStats w : List.of(WeaponStats.VANILLA_BOW, WeaponStats.SHORTBOW, WeaponStats.LONGBOW, WeaponStats.RECURVE_BOW)) {
            double body = ShotMath.damage(ShotMathTest.launchBase(w), w.damageMult(), blastMult, true, 1.0, 30, T) + feet;
            double head = ShotMath.damage(ShotMathTest.launchBase(w), w.damageMult(), blastMult, true, T.precisionMobs(), 200, T) + feet;
            assertTrue(head > body && body > feet, w + ": head " + head + ", body " + body + ", at the feet " + feet);
            assertTrue(head <= bestPlain, w + ": a Blast Arrow's best hit " + head + " beats the best plain arrow's " + bestPlain);
        }
    }

    /**
     * The Marksman can be beaten with a vanilla bow in iron armour within 3 minutes, by an archer who Perfects half their
     * draws and takes cover at two of its creaks in three; one who stands in the open and trades shots loses. The duel at
     * 25 blocks, cycle by cycle of the Marksman's (ducked 2 s, then drawing 1.5 s, 1.2 s below half health): the archer
     * gets one shot at it standing in a crenel's gap to draw (3 in 4 land, a third of those on the head). From the
     * ground below its tower, its head ducked behind the parapet sits under the parapet's line: no shot then but a lob. Its arrow lands on an archer who doesn't take cover, on the head 1 time in 3. The archer heals a point
     * every 4 s (full hunger, no food eaten during the fight), on Normal.
     */
    @Test
    void marksmanBeatable() {
        double distance = 25, ironArmour = 15;
        // the archer's shot on it: base 6 from a full draw, Perfect half the time, through its 4 armour
        double body = 0, head = 0;
        for (boolean perfect : new boolean[] {true, false}) {
            body += 0.5 * afterArmour(ShotMath.damage(6, 1.0, 1, perfect, 1.0, distance, T), 4, 0);
            head += 0.5 * afterArmour(ShotMath.damage(6, 1.0, 1, perfect, T.precisionMobs(), distance, T), 4, 0);
        }
        double perCycle = 0.75 * (2.0 / 3.0 * body + 1.0 / 3.0 * head);
        double health = MarksmanRules.MAX_HEALTH, seconds = 0;
        int cycles = 0;
        while (health > 0) {
            boolean two = MarksmanRules.phaseTwo((float) health, MarksmanRules.MAX_HEALTH);
            seconds += (MarksmanRules.COVER_TICKS + MarksmanRules.drawTicks(two)) / 20.0;
            health -= perCycle;
            cycles++;
        }
        assertTrue(seconds <= 180, "the archer takes " + seconds + " s to win");
        // its arrow on the archer: through iron, the head a third of the time
        double hit = 2.0 / 3.0 * afterArmour(MarksmanRules.arrowDamage(distance, false, T), ironArmour, 0)
                + 1.0 / 3.0 * afterArmour(MarksmanRules.arrowDamage(distance, true, T), ironArmour, 0);
        assertTrue(hit <= 6.5, "its arrow takes " + hit + " from an archer in iron: no more than about three hearts");
        // on Hard a mob's arrow deals half again: its worst, a Perfect headshot from as far as it fights on a player with
        // no armour, still leaves them standing from full health, with a heart and a half to spare
        double worst = 1.5 * MarksmanRules.arrowDamage(MarksmanRules.SIGHT, true, T);
        assertTrue(worst <= 17.0, "on Hard its worst arrow takes " + worst + " from a bare player");
        double healed = seconds / 4.0;
        double takingCover = cycles / 3.0 * hit;
        assertTrue(takingCover - healed < 20, "taking cover at two creaks in three, the archer loses " + takingCover + " and heals " + healed);
        double inTheOpen = cycles * hit;
        assertTrue(inTheOpen - healed >= 20, "trading shots in the open should lose: the archer loses " + inTheOpen + " and heals " + healed);
    }

    private static void assertRatio(double armour, double toughness, double ceiling) {
        double vanilla = perMinute(WeaponStats.VANILLA_CROSSBOW, true, armour, toughness, 1.0);
        double arbalest = perMinute(WeaponStats.ARBALEST, false, armour, toughness, BoltTraits.ARBALEST.armourKept());
        assertTrue(arbalest <= ceiling * vanilla, "armour " + armour + "/" + toughness + ": Arbalest " + arbalest
                + " damage a minute against a vanilla crossbow's " + vanilla + " (at most " + ceiling + " ×)");
    }

    /**
     * A player's mean damage a minute with a crossbow from a plain arrow (base 2) through armour, firing as fast as it
     * loads. Vanilla's bolt from a player always crits: ceil(speed × base) plus nextInt(hit / 2 + 2); a Full Draw
     * crossbow's doesn't.
     */
    private static double perMinute(WeaponStats w, boolean crit, double armour, double toughness, double armourKept) {
        int hit = ShotMath.vanillaDamage(3.15 * w.velocityMult(), 2.0 * w.damageMult());
        int bonuses = crit ? hit / 2 + 2 : 1;
        double total = 0;
        for (int bonus = 0; bonus < bonuses; bonus++) total += afterArmour(hit + bonus, armour * armourKept, toughness);
        return total / bonuses * 1200.0 / w.drawTicks();
    }

    /** Vanilla's armour formula ({@code CombatRules.getDamageAfterAbsorb}, no enchantments). */
    private static double afterArmour(double damage, double armour, double toughness) {
        double effective = Math.clamp(armour - damage / (2.0 + toughness / 4.0), armour * 0.2, 20.0);
        return damage * (1.0 - effective / 25.0);
    }
}
