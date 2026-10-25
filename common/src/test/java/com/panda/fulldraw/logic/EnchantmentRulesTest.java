package com.panda.fulldraw.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class EnchantmentRulesTest {
    @Test
    void quickdrawShortensTheDraw() {
        WeaponStats bow = WeaponStats.VANILLA_BOW;
        assertSame(bow, EnchantmentRules.apply(bow, 0, 0));
        assertEquals(19, EnchantmentRules.apply(bow, 1, 0).drawTicks());
        assertEquals(17, EnchantmentRules.apply(bow, 2, 0).drawTicks());
        assertEquals(16, EnchantmentRules.apply(bow, 3, 0).drawTicks());
        // a level past the top (a command, a pack) does what the top level does
        assertEquals(16, EnchantmentRules.apply(bow, 10, 0).drawTicks());
        // the rest of the bow is unchanged
        WeaponStats fast = EnchantmentRules.apply(bow, 3, 0);
        assertEquals(bow.damageMult(), fast.damageMult());
        assertEquals(bow.velocityMult(), fast.velocityMult());
        assertEquals(bow.extraWindow(), fast.extraWindow());
        // a short draw never drops under a tick
        assertEquals(1, EnchantmentRules.apply(new WeaponStats(1, 1, 1, 1, 0, 1), 3, 0).drawTicks());
    }

    @Test
    void steadyWidensThePerfectWindow() {
        WeaponStats recurve = new WeaponStats(24, 1.1, 1.1, 0.6, 2, 1.0);
        assertEquals(4, EnchantmentRules.apply(recurve, 0, 1).extraWindow());
        assertEquals(6, EnchantmentRules.apply(recurve, 0, 2).extraWindow());
        assertEquals(6, EnchantmentRules.apply(recurve, 0, 255).extraWindow());
        assertEquals(Breath.capacityTicks(2), Breath.capacityTicks(255));
        assertEquals(24, EnchantmentRules.apply(recurve, 0, 2).drawTicks());
    }

    @Test
    void deadeyeAddsToEveryWeakSpot() {
        assertEquals(0.0, EnchantmentRules.deadeyeBonus(0));
        assertEquals(0.30, EnchantmentRules.deadeyeBonus(2), 1e-9);
        assertEquals(0.45, EnchantmentRules.deadeyeBonus(3), 1e-9);
        assertEquals(0.45, EnchantmentRules.deadeyeBonus(9), 1e-9);
    }

    @Test
    void anAnvilKeepsTheEyeTiers() {
        int table = EnchantmentRules.DEADEYE_TABLE_MAX;
        // up to the table's best, the anvil's own merging stands
        assertEquals(2, EnchantmentRules.anvilLevel(2, 1, 1, table));
        // two Deadeye II don't make a III
        assertEquals(2, EnchantmentRules.anvilLevel(3, 2, 2, table));
        // a III book (made from the Eye) goes onto a bow, either way round
        assertEquals(3, EnchantmentRules.anvilLevel(3, 0, 3, table));
        assertEquals(3, EnchantmentRules.anvilLevel(3, 3, 2, table));
        // Steady: two I don't make a II, an Eye-made II book goes on
        assertEquals(1, EnchantmentRules.anvilLevel(2, 1, 1, EnchantmentRules.STEADY_TABLE_MAX));
        assertEquals(2, EnchantmentRules.anvilLevel(2, 0, 2, EnchantmentRules.STEADY_TABLE_MAX));
    }

    @Test
    void aRicochetMirrorsOffTheFaceAndLosesSpeed() {
        // straight into a wall facing west: back the way it came, slower
        assertArrayEquals(new double[] {-2.1, 0, 0}, EnchantmentRules.bounce(3, 0, 0, -1, 0, 0), 1e-9);
        // a glancing shot into the floor keeps its heading and turns up
        assertArrayEquals(new double[] {1.4, 0.7, 0}, EnchantmentRules.bounce(2, -1, 0, 0, 1, 0), 1e-9);
    }
}
