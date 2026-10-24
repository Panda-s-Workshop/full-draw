package com.panda.fulldraw.logic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Pins the shot pipeline's rules and its balance targets. */
class ShotMathTest {
    private static final ShotMath.Tuning T = ShotMath.DEFAULTS;
    private static final WeaponStats BOW = WeaponStats.VANILLA_BOW;

    @Test
    void perfectWindowOpensTwoTicksAfterFullDrawForFourTicks() {
        assertFalse(ShotMath.isPerfect(20, BOW, T, 0), "full draw itself is not Perfect");
        assertFalse(ShotMath.isPerfect(21, BOW, T, 0));
        for (int t = 22; t <= 25; t++) assertTrue(ShotMath.isPerfect(t, BOW, T, 0), "tick " + t);
        assertFalse(ShotMath.isPerfect(26, BOW, T, 0), "the window closes after four ticks");
        assertFalse(ShotMath.isPerfect(10, BOW, T, 0));
    }

    @Test
    void theWindowFollowsTheConfig() {
        ShotMath.Tuning wide = new ShotMath.Tuning(6, 1, T.perfectMult(), T.precisionMobs(), T.precisionPlayers(), T.skillCap(), T.rangeStart(),
                T.rangePerBlock(), T.rangeCap(), T.pointBlankDistance(), T.pointBlankMult(), T.trembleStartSeconds(), T.trembleFullSeconds(),
                T.trembleMaxSpread());
        assertFalse(ShotMath.isPerfect(20, BOW, wide, 0));
        for (int t = 21; t <= 26; t++) assertTrue(ShotMath.isPerfect(t, BOW, wide, 0), "tick " + t);
        assertFalse(ShotMath.isPerfect(27, BOW, wide, 0));
        WeaponStats recurveLike = new WeaponStats(20, 1, 1, 1, 3, 1);
        assertTrue(ShotMath.isPerfect(28, recurveLike, T, 0), "a weapon's extra ticks add to the base window");
        assertFalse(ShotMath.isPerfect(29, recurveLike, T, 0));
    }

    @Test
    void perfectWindowOnTheServerOpensOneTickEarlier() {
        assertFalse(ShotMath.isPerfectOnServer(20, BOW, T, 0));
        for (int t = 21; t <= 25; t++) assertTrue(ShotMath.isPerfectOnServer(t, BOW, T, 0), "tick " + t);
        assertFalse(ShotMath.isPerfectOnServer(26, BOW, T, 0), "no grace at the late edge");
    }

    @Test
    void steadyAimHalvesSpreadAndHoldsTheTremble() {
        assertEquals(0.5, ShotMath.spread(BOW, false, 0, 1, true, -1, T), 1e-9, "focused: half the bow's spread");
        assertEquals(0.5, ShotMath.spread(BOW, false, 200, 1, true, -1, T), 1e-9, "focused: no tremble however long");
        assertEquals(0.25, ShotMath.spread(BOW, false, 0, 0.5, true, -1, T), 1e-9, "focus and sneaking both count");
        assertEquals(0.0, ShotMath.spread(BOW, true, 0, 1, true, -1, T), 1e-9, "a Perfect still flies true");
    }

    @Test
    void runningOutOfBreathStartsTheTrembleAtOnce() {
        // 1 s past full draw would be calm, but the breath ran out: the tremble grows from its start
        assertEquals(1.0, ShotMath.spread(BOW, false, 20, 1, false, 0, T), 1e-9, "the tick the breath runs out");
        assertEquals(2.0, ShotMath.spread(BOW, false, 20, 1, false, 30, T), 1e-9, "1.5 s later: halfway");
        assertEquals(3.0, ShotMath.spread(BOW, false, 20, 1, false, 60, T), 1e-9, "3 s later: full");
        assertEquals(3.0, ShotMath.spread(BOW, false, 120, 1, false, 0, T), 1e-9, "a 6 s hold stays at full: the clock never goes back");
        assertEquals(2.0, ShotMath.spread(BOW, false, 90, 1, false, 10, T), 1e-9, "the longer of the two clocks counts");
        assertEquals(0.5, ShotMath.tremble(BOW, 90, -1, T), 1e-9, "the sway's strength at 4.5 s");
        assertEquals(0.0, ShotMath.tremble(BOW, 60, -1, T), 1e-9);
    }

    @Test
    void movementRulesScaleSpread() {
        double walking = 0.01;
        assertEquals(1.0, ShotMath.movementMult(false, false, false, walking));
        assertEquals(2.0, ShotMath.movementMult(true, false, false, walking), "sprinting");
        assertEquals(2.0, ShotMath.movementMult(false, true, false, 0), "airborne, even when not moving sideways");
        assertEquals(4.0, ShotMath.movementMult(true, true, false, walking), "sprint-jumping");
        assertEquals(0.5, ShotMath.movementMult(false, false, false, 0), "standing still");
        assertEquals(0.5, ShotMath.movementMult(false, false, true, walking), "sneaking");
        assertEquals(2.0, ShotMath.movementMult(false, true, true, 0), "sneaking in the air is still airborne");
    }

    @Test
    void perfectWindowFollowsTheWeaponsDrawTimeAndExtraTicks() {
        WeaponStats shortbow = WeaponStats.SHORTBOW;
        assertTrue(ShotMath.isPerfect(14, shortbow, T, 0));
        assertFalse(ShotMath.isPerfect(18, shortbow, T, 0));
        assertTrue(ShotMath.isPerfect(18, shortbow, T, 2), "Steady widens the window");
    }

    @Test
    void vanillaDamageMatchesTheVanillaFormula() {
        assertEquals(6, ShotMath.vanillaDamage(3.0, 2.0), "a full-draw bow arrow");
        assertEquals(6, ShotMath.vanillaDamage(2.6, 2.0), "ceil(5.2)");
        assertEquals(0, ShotMath.vanillaDamage(0, 2.0));
    }

    @Test
    void rangeBonusStartsAtTwentyBlocksAndCapsAtFifty() {
        assertEquals(1.0, ShotMath.rangeMult(10, T), 1e-9);
        assertEquals(1.0, ShotMath.rangeMult(20, T), 1e-9);
        assertEquals(1.10, ShotMath.rangeMult(30, T), 1e-9);
        assertEquals(1.30, ShotMath.rangeMult(50, T), 1e-9);
        assertEquals(1.30, ShotMath.rangeMult(120, T), 1e-9, "capped");
    }

    @Test
    void pointBlankPenaltyBelowThreeBlocks() {
        assertEquals(0.6, ShotMath.pointBlankMult(2.9, T), 1e-9);
        assertEquals(1.0, ShotMath.pointBlankMult(3.0, T), 1e-9);
    }

    @Test
    void skillMultiplierIsCapped() {
        assertEquals(1.5, ShotMath.skillMult(true, 1.0, 1.0, T), 1e-9);
        assertEquals(2.25, ShotMath.skillMult(true, 1.5, 1.0, T), 1e-9);
        assertEquals(2.5, ShotMath.skillMult(true, 1.5, 1.3, T), 1e-9, "raw 2.925 is capped");
    }

    @Test
    void theDamageTable() {
        assertEquals(6.0, ShotMath.damage(6, 1, 1, false, 1, 10, T), 1e-9);
        assertEquals(9.0, ShotMath.damage(6, 1, 1, true, 1, 10, T), 1e-9);
        assertEquals(13.5, ShotMath.damage(6, 1, 1, true, 1.5, 10, T), 1e-9);
        assertEquals(15.0, ShotMath.damage(6, 1, 1, true, 1.5, 50, T), 1e-9);
        assertEquals(3.6, ShotMath.damage(6, 1, 1, false, 1, 2, T), 1e-9);
    }

    @Test
    void theBreakdownExplainsTheDamage() {
        ShotMath.Breakdown b = ShotMath.breakdown(6, 1, 1, true, 1.5, 50, T);
        assertEquals(1.5, b.perfectMult(), 1e-9);
        assertEquals(1.3, b.rangeMult(), 1e-9);
        assertEquals(2.925, b.skillRaw(), 1e-9);
        assertTrue(b.capped(), "2.925 is over the 2.5 cap");
        assertEquals(15.0, b.total(), 1e-9);
        assertEquals(ShotMath.damage(6, 0.7, 1, false, 1, 2, T), ShotMath.breakdown(6, 0.7, 1, false, 1, 2, T).total(), 1e-9);
        assertFalse(ShotMath.breakdown(6, 1, 1, true, 1, 10, T).capped());
    }

    /** A plain full-draw shot is never above vanilla's minimum (crit-free) damage. */
    @Test
    void plainNotAboveVanilla() {
        for (int d = 0; d <= 20; d++) assertTrue(ShotMath.damage(6, 1, 1, false, 1, d, T) <= 6.0, "distance " + d);
    }

    /** A Perfect shot beats vanilla's average full-draw damage (6 plus a crit bonus of nextInt(5), mean 8). */
    @Test
    void perfectBeatsVanillaAverage() {
        double vanillaAverage = 6 + (0 + 1 + 2 + 3 + 4) / 5.0;
        assertTrue(ShotMath.damage(6, 1, 1, true, 1, 10, T) >= vanillaAverage);
    }

    /**
     * No arrow from any bow kills a full-health zombie (20 HP, 2 natural + 15 iron armour) in one hit: each bow's
     * best case, a Perfect weak-spot shot at the skill cap with its arrow still at launch speed.
     */
    @Test
    void capHoldsAgainstIronZombie() {
        for (WeaponStats w : List.of(BOW, WeaponStats.SHORTBOW, WeaponStats.LONGBOW, WeaponStats.RECURVE_BOW)) {
            double best = ShotMath.damage(launchBase(w), w.damageMult(), 1, true, T.precisionMobs(), 200, T);
            assertTrue(afterArmour(best, 17, 0) < 20, w + ": best shot after armour " + afterArmour(best, 17, 0));
        }
    }

    /** A Longbow Perfect headshot at 50 m stays under 25, even counting its arrow at launch speed. */
    @Test
    void longbowCeiling() {
        WeaponStats w = WeaponStats.LONGBOW;
        double headshot = ShotMath.damage(launchBase(w), w.damageMult(), 1, true, T.precisionMobs(), 50, T);
        assertTrue(headshot < 25, "Longbow Perfect headshot at 50 m: " + headshot);
    }

    /** Vanilla's arrow damage for this bow's full-draw launch speed and a plain arrow (base 2). */
    static int launchBase(WeaponStats w) {
        return ShotMath.vanillaDamage(3.0 * w.velocityMult(), 2.0);
    }

    @Test
    void perfectShotsFlyTrueAndHoldingTooLongTrembles() {
        assertEquals(0.0, ShotMath.spread(BOW, true, 100, 1, T), 1e-9);
        assertEquals(1.0, ShotMath.spread(BOW, false, 20, 1, T), 1e-9, "1 s past full draw: vanilla");
        assertEquals(1.0, ShotMath.spread(BOW, false, 60, 1, T), 1e-9, "3 s: tremble just starts");
        assertEquals(2.0, ShotMath.spread(BOW, false, 90, 1, T), 1e-9, "4.5 s: halfway");
        assertEquals(3.0, ShotMath.spread(BOW, false, 120, 1, T), 1e-9, "6 s: full");
        assertEquals(3.0, ShotMath.spread(BOW, false, 400, 1, T), 1e-9, "stays at the max");
        assertEquals(0.5, ShotMath.spread(BOW, false, 0, 0.5, T), 1e-9, "standing still halves it");
    }

    @Test
    void weaponDrawTimeMapsToVanillaTicks() {
        assertEquals(20, WeaponStats.SHORTBOW.toVanillaTicks(12));
        assertEquals(10, WeaponStats.SHORTBOW.toVanillaTicks(6));
        assertEquals(20, BOW.toVanillaTicks(20));
    }

    /** Vanilla's CombatRules.getDamageAfterAbsorb. */
    static double afterArmour(double damage, double armour, double toughness) {
        double f = 2.0 + toughness / 4.0;
        double g = Math.clamp(armour - damage / f, armour * 0.2, 20.0);
        return damage * (1.0 - g / 25.0);
    }
}
