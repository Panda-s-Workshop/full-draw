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
        assertTrue(BoltTraits.ARBALEST.clanks(12.0F, 8.0F), "an Arbalest bolt into an iron chestplate clanks");
        assertFalse(BoltTraits.ARBALEST.clanks(0.0F, 8.0F), "a bolt a shield stopped reaches the armour with 0: no clank");
        assertFalse(BoltTraits.ARBALEST.clanks(12.0F, 2.0F), "a husk's own 2 points: no clank");
        assertFalse(BoltTraits.NONE.clanks(12.0F, 20.0F), "a plain bolt ignores no armour: no clank");
    }

    @Test
    void repeaterBurst() {
        int bolt = ShotMath.vanillaDamage(3.15 * WeaponStats.REPEATING_CROSSBOW.velocityMult(), 2.0 * WeaponStats.REPEATING_CROSSBOW.damageMult());
        int magazine = bolt * Magazine.REPEATING_CROSSBOW.shots();
        int arbalest = ShotMath.vanillaDamage(3.15 * WeaponStats.ARBALEST.velocityMult(), 2.0 * WeaponStats.ARBALEST.damageMult());
        assertTrue(magazine <= 1.5 * arbalest, "a magazine deals " + magazine + ", one Arbalest bolt " + arbalest);
        assertTrue(magazine >= arbalest, "a magazine deals " + magazine + ", less than one Arbalest bolt's " + arbalest);
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
