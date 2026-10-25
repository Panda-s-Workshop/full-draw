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
    void recoveryFollowsThePlainArrow() {
        // each head's chance at the default
        assertEquals(0.4, ArrowTraits.recoveryChance(ArrowTraits.PLAIN_RECOVERY, ArrowTraits.PLAIN_RECOVERY), EPS);
        assertEquals(0.25, ArrowTraits.recoveryChance(ArrowTraits.BROADHEAD.recovery(), ArrowTraits.PLAIN_RECOVERY), EPS);
        assertEquals(0.4, ArrowTraits.recoveryChance(ArrowTraits.BODKIN.recovery(), ArrowTraits.PLAIN_RECOVERY), EPS);
        assertEquals(1.0, ArrowTraits.recoveryChance(ArrowTraits.BLUNT.recovery(), ArrowTraits.PLAIN_RECOVERY), EPS);
        assertEquals(0.0, ArrowTraits.recoveryChance(ArrowTraits.FLARE.recovery(), ArrowTraits.PLAIN_RECOVERY), EPS);
        assertEquals(0.0, ArrowTraits.recoveryChance(ArrowTraits.BLAST.recovery(), ArrowTraits.PLAIN_RECOVERY), EPS);
        // the config moves every head with the plain arrow, up to certain; 0 turns it off
        assertEquals(0.5, ArrowTraits.recoveryChance(ArrowTraits.BROADHEAD.recovery(), 0.8), EPS);
        assertEquals(1.0, ArrowTraits.recoveryChance(ArrowTraits.BLUNT.recovery(), 0.8), EPS, "no more than certain");
        assertEquals(0.5, ArrowTraits.recoveryChance(ArrowTraits.BLUNT.recovery(), 0.2), EPS);
        for (ArrowTraits head : new ArrowTraits[] {ArrowTraits.BROADHEAD, ArrowTraits.BODKIN, ArrowTraits.BLUNT, ArrowTraits.ROPE})
            assertEquals(0.0, ArrowTraits.recoveryChance(head.recovery(), 0.0), EPS);
    }

    @Test
    void bluntAlwaysDoesItsOwnDamage() {
        for (double damage : new double[] {2, 6, 13.5, 30})
            assertEquals(1.0, damage * ArrowTraits.BLUNT.multFor(damage, 7), EPS, "a " + damage + " hit");
        assertEquals(0.0, ArrowTraits.BLUNT.multFor(0, 0), EPS, "nothing to scale");
        assertEquals(2.0F, ArrowTraits.BLUNT.knockback());
    }

    @Test
    void ropeHangsToTheFirstBlockOrItsLength() {
        assertEquals(24, ArrowTraits.ROPE_LENGTH, "the default length");
        assertEquals(24, ArrowTraits.ropeLength(i -> true, 24), "open all the way down");
        assertEquals(8, ArrowTraits.ropeLength(i -> true, 8), "a server's shorter rope");
        assertEquals(5, ArrowTraits.ropeLength(i -> i < 5, 24), "stops on the floor");
        assertEquals(0, ArrowTraits.ropeLength(i -> false, 24), "no room for the knot");
        assertEquals(0.5, ArrowTraits.ROPE.multFor(6.0, 15), EPS, "a weak hit, armour or not");
    }

    @Test
    void flareAndBlastNumbers() {
        assertEquals(30, ArrowTraits.FLARE_LIGHT_TICKS / 20, "the flare burns 30 s");
        assertEquals(10, ArrowTraits.FLARE_GLOW_TICKS / 20, "mobs glow 10 s");
        assertEquals(8.0, ArrowTraits.FLARE_GLOW_RADIUS, EPS);
        assertEquals(1.5F, ArrowTraits.BLAST_POWER, "half a creeper's");
        assertEquals(11.0, ArrowTraits.blastDamage(0.0, 1.0), EPS, "half of vanilla's 22 at the centre");
        assertEquals(0.0, ArrowTraits.blastDamage(3.0, 1.0), EPS, "nothing past its diameter");
        assertTrue(ArrowTraits.blastDamage(1.0, 1.0) < ArrowTraits.blastDamage(0.5, 1.0), "less further off");
        assertTrue(ArrowTraits.blastDamage(1.0, 0.5) < ArrowTraits.blastDamage(1.0, 1.0), "less when half hidden");
        assertEquals(0.5, ArrowTraits.FLARE.multFor(6.0, 10), EPS);
        assertEquals(0.5, ArrowTraits.BLAST.multFor(6.0, 10), EPS);
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

    /**
     * Armour shortens the bleed and plate stops it: a broadhead's hit and bleed together beat a bodkin's hit unarmoured,
     * and from 10 armour points up the bodkin's hit is the better arrow.
     */
    @Test
    void bleedStopsAtPlate() {
        assertEquals(4, ArrowTraits.bleedWounds(0));
        assertEquals(4, ArrowTraits.bleedWounds(2), "a zombie's own armour");
        assertEquals(2, ArrowTraits.bleedWounds(5));
        assertEquals(1, ArrowTraits.bleedWounds(9));
        assertEquals(0, ArrowTraits.bleedWounds(10));
        assertEquals(0, ArrowTraits.bleedWounds(20));
        assertEquals(40, ArrowTraits.bleedTicks(0, 5), "a fresh wound through 5 points");
        assertEquals(50, ArrowTraits.bleedTicks(50, 10), "plate never cuts an open wound short");
        assertEquals(0, ArrowTraits.bleedTicks(0, 15), "no wound through plate");
        double bleed = ArrowTraits.BLEED_DAMAGE;
        assertTrue(afterArmour(6.0 * ArrowTraits.BROADHEAD.armourMult(0), 0) + 4 * bleed > afterArmour(6.0 * 0.8, 0), "unarmoured, the broadhead");
        for (int armour = 10; armour <= 20; armour++) {
            double broadhead = afterArmour(6.0 * ArrowTraits.BROADHEAD.armourMult(armour), armour) + ArrowTraits.bleedWounds(armour) * bleed;
            double bodkin = afterArmour(6.0 * 0.8, armour * ArrowTraits.BODKIN.armourKept());
            assertTrue(bodkin > broadhead, "at " + armour + " armour the bodkin should win: " + bodkin + " vs " + broadhead);
        }
    }

    @Test
    void tooltipsStateTheNumbers() throws Exception {
        String lang = Files.readString(Path.of("src", "main", "resources", "assets", "fulldraw", "lang", "en_us.json"));
        assertTrue(lang.contains("×1.25 damage unarmoured, falling to ×0.75"), "broadhead tooltip");
        assertTrue(lang.contains("1 damage a second for 4 s; armour shortens it and heavy armour stops it"), "bleed tooltip");
        assertTrue(lang.contains("×0.8 damage, but 60% of armour"), "bodkin tooltip");
        assertTrue(lang.contains("1 damage, whatever the bow or the shot"), "blunt tooltip");
        assertTrue(lang.contains("×0.5 damage"), "rope arrow tooltip");
        assertTrue(lang.contains("hangs a rope down to %s blocks"), "rope length tooltip, filled with the server's length");
        assertTrue(lang.contains("Lights where it lands for 30 s; mobs within 8 blocks glow for 10 s"), "flare tooltip");
        assertTrue(lang.contains("×0.5 damage, then an explosion where it strikes"), "blast tooltip");
        assertTrue(lang.contains("Hurts and throws back all around it, you too"), "the blast hurts its shooter too");
    }

    /** Vanilla's {@code CombatRules.getDamageAfterAbsorb} with no toughness. */
    private static double afterArmour(double damage, double armour) {
        double effective = Math.clamp(armour - damage / 2.0, armour * 0.2, 20.0);
        return damage * (1.0 - effective / 25.0);
    }
}
