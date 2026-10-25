package com.panda.fulldraw.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The arrow heads' numbers, and the tooltips that state them. */
class ArrowTraitsTest {
    private static final double EPS = 1e-9;

    @Test
    void broadheadFallsOffWithArmour() {
        assertEquals(1.25, ArrowTraits.BROADHEAD.armourMult(0), EPS);
        assertEquals(1.0, ArrowTraits.BROADHEAD.armourMult(5), EPS, "halfway to full armour, halfway between");
        assertEquals(0.75, ArrowTraits.BROADHEAD.armourMult(10), EPS);
        assertEquals(0.75, ArrowTraits.BROADHEAD.armourMult(20), EPS, "no lower past full armour");
        assertEquals(1.0, ArrowTraits.BROADHEAD.armourKept(), EPS, "a broadhead counts all the armour");
    }

    @Test
    void bodkinIgnoresMostArmour() {
        assertEquals(0.8, ArrowTraits.BODKIN.multFor(6.0, 0), EPS);
        assertEquals(0.8, ArrowTraits.BODKIN.multFor(6.0, 20), EPS);
        assertEquals(0.4, ArrowTraits.BODKIN.armourKept(), EPS);
        assertTrue(ArrowTraits.BODKIN.piercesArmour());
    }

    @Test
    void bodkinBeatsAnArrowOnlyThroughArmour() {
        // vanilla's armour formula on a 6-damage hit: unarmoured an arrow wins, in diamond (20) the bodkin does
        assertTrue(afterArmour(6.0, 0) > afterArmour(6.0 * 0.8, 0));
        assertTrue(afterArmour(6.0 * 0.8, 20 * 0.4) > 1.5 * afterArmour(6.0, 20));
    }

    @Test
    void bluntAlwaysDoesItsOwnDamage() {
        for (double damage : new double[] {2, 6, 13.5, 30})
            assertEquals(1.0, damage * ArrowTraits.BLUNT.multFor(damage, 7), EPS, "a " + damage + " hit");
        assertEquals(0.0, ArrowTraits.BLUNT.multFor(0, 0), EPS, "nothing to scale");
        assertEquals(2.0F, ArrowTraits.BLUNT.knockback());
    }

    @Test
    void bleedIsOneASecondForFourSeconds() {
        assertEquals(4, ArrowTraits.BLEED_TICKS / ArrowTraits.BLEED_INTERVAL);
        assertEquals(1.0F, ArrowTraits.BLEED_DAMAGE);
    }

    /** A new hit on an open wound keeps the ticks in step and always leaves four to come, never putting the next off. */
    @Test
    void anotherHitKeepsTheBleedInStep() {
        assertEquals(80, ArrowTraits.bleedTicks(0), "a fresh wound");
        for (int remaining = 1; remaining <= 80; remaining++) {
            int ticks = ArrowTraits.bleedTicks(remaining);
            assertEquals(remaining % 20, ticks % 20, "in step at " + remaining);
            assertTrue(ticks >= remaining && ticks <= 80, "never shorter, never past 4 s: " + remaining + " -> " + ticks);
            int left = 0;
            for (int d = ticks; d > 0; d--) if (d % 20 == 1) left++;
            assertEquals(4, left, "four ticks to come after a hit at " + remaining);
        }
    }

    @Test
    void tooltipsStateTheNumbers() throws Exception {
        String lang = Files.readString(Path.of("src", "main", "resources", "assets", "fulldraw", "lang", "en_us.json"));
        assertTrue(lang.contains("×1.25 damage unarmoured, falling to ×0.75"), "broadhead tooltip");
        assertTrue(lang.contains("1 damage a second for 4 s"), "bleed tooltip");
        assertTrue(lang.contains("×0.8 damage, but 60% of armour"), "bodkin tooltip");
        assertTrue(lang.contains("1 damage, whatever the bow or the shot"), "blunt tooltip");
    }

    /** Vanilla's {@code CombatRules.getDamageAfterAbsorb} with no toughness. */
    private static double afterArmour(double damage, double armour) {
        double effective = Math.clamp(armour - damage / 2.0, armour * 0.2, 20.0);
        return damage * (1.0 - effective / 25.0);
    }
}
