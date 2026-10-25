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

    /**
     * Vanilla's Power isn't Full Draw's to price, and vanilla's own bow with Power V already out-damages a Sharpness V
     * sword up close: a full draw is 15 and a crit adds 0 to 8, 19 a second on average, against the sword's 16. Full Draw
     * adds nothing past that: the vanilla bow's best shot two blocks out, a Perfect headshot with Power V, does no more a
     * second than vanilla's.
     */
    @Test
    void powerNoStrongerThanVanillaUpClose() {
        int powered = ShotMath.vanillaDamage(3.0, 2.0 + 3.0);
        double vanilla = (powered + (powered / 2 + 1) / 2.0) / (WeaponStats.VANILLA_BOW.drawTicks() / 20.0);
        double fullDraw = ShotMath.damage(powered, 1.0, 1, true, T.precisionMobs(), 2, T) / ((WeaponStats.VANILLA_BOW.drawTicks() + T.perfectDelay()) / 20.0);
        assertTrue(fullDraw <= vanilla, "a Power V bow's Perfect headshot at two blocks: " + fullDraw + " a second, vanilla's crit " + vanilla);
    }

    private static double closeDps(WeaponStats w, double deadeye) {
        double shot = ShotMath.damage(ShotMathTest.launchBase(w), w.damageMult(), 1, true, T.precisionMobs() + deadeye, 2, T);
        return shot / ((w.drawTicks() + T.perfectDelay()) / 20.0);
    }

    /**
     * The Arbalest trades rate for weight: its damage a minute against one target stays at most 1.2 × a vanilla
     * crossbow's, unarmoured and in full iron, and at most 1.5 × in diamond or netherite, where cutting through armour is
     * its job; and in diamond and netherite it stays ahead of vanilla's, which is the point of carrying it. Both with
     * the same Quick Charge, 0 to III. A line of mobs is its other job, so this is per target. A minute is 1200 ticks
     * of back-to-back loads.
     */
    @Test
    void arbalestTradeoff() {
        for (int quick = 0; quick <= 3; quick++) {
            assertRatio(0, 0, 1.2, quick);   // unarmoured
            assertRatio(15, 0, 1.2, quick);  // full iron: 15 points, no toughness
            assertRatio(20, 8, 1.5, quick);  // full diamond
            assertRatio(20, 12, 1.5, quick); // full netherite
            for (double toughness : new double[] {8, 12}) {
                double vanilla = perMinute(WeaponStats.VANILLA_CROSSBOW, true, 20, toughness, 1.0, quick);
                double arbalest = perMinute(WeaponStats.ARBALEST, false, 20, toughness, BoltTraits.ARBALEST.armourKept(), quick);
                assertTrue(arbalest > vanilla, "Quick Charge " + quick + ", armour 20/" + toughness + ": Arbalest " + arbalest + " a minute, vanilla " + vanilla);
            }
        }
        // and up close a sword still wins against it
        double arbalest = perMinute(WeaponStats.ARBALEST, false, 0, 0, 1.0);
        assertTrue(arbalest / 60 < DIAMOND_SWORD_DPS, "Arbalest " + arbalest / 60 + " damage a second, a diamond sword does " + DIAMOND_SWORD_DPS);
    }

    @Test
    void clankOnlyWhenTheBoltGetsThroughRealArmour() {
        double arbalest = BoltTraits.ARBALEST.armourKept();
        assertTrue(BoltTraits.clanks(arbalest, 12.0F, 8.0F), "an Arbalest bolt into an iron chestplate clanks");
        assertFalse(BoltTraits.clanks(arbalest, 0.0F, 8.0F), "a bolt a shield stopped reaches the armour with 0: no clank");
        assertFalse(BoltTraits.clanks(arbalest, 12.0F, 2.0F), "a husk's own 2 points: no clank");
        assertFalse(BoltTraits.clanks(BoltTraits.NONE.armourKept(), 12.0F, 20.0F), "a plain bolt ignores no armour: no clank");
        assertTrue(BoltTraits.clanks(ArrowTraits.BODKIN.armourKept(), 4.0F, 8.0F), "a bodkin bites through armour too");
    }

    /**
     * The Repeating Crossbow is the burst: a full magazine, every bolt landing, out-damages a vanilla crossbow over the
     * same time (its first shot and the one it loads and fires after 25 ticks, crits at their mean), and stays under two
     * Arbalest bolts (a burst, not a heavier hit). Fired back to back, crank and magazine, it keeps at least 0.55 × a
     * vanilla crossbow's damage a minute: what it gives up for the burst. Plain arrows, unarmoured. That every bolt does
     * land on one target is the GameTest {@code repeater_burst_lands}.
     */
    @Test
    void repeaterBurst() {
        WeaponStats r = WeaponStats.REPEATING_CROSSBOW;
        Magazine m = Magazine.REPEATING_CROSSBOW;
        int bolt = ShotMath.vanillaDamage(3.15 * r.velocityMult(), 2.0 * r.damageMult());
        int magazine = bolt * m.shots();
        int arbalest = ShotMath.vanillaDamage(3.15 * WeaponStats.ARBALEST.velocityMult(), 2.0 * WeaponStats.ARBALEST.damageMult());
        int burstTicks = (m.shots() - 1) * m.shotTicks();
        int vanillaShots = 1 + burstTicks / WeaponStats.VANILLA_CROSSBOW.drawTicks();
        double vanillaBurst = vanillaShots * perMinute(WeaponStats.VANILLA_CROSSBOW, true, 0, 0, 1.0) * WeaponStats.VANILLA_CROSSBOW.drawTicks() / 1200.0;
        assertTrue(magazine > vanillaBurst, "a magazine deals " + magazine + " over " + burstTicks + " ticks, a vanilla crossbow " + vanillaBurst);
        assertTrue(magazine < 2 * arbalest, "a magazine deals " + magazine + ", two Arbalest bolts " + 2 * arbalest);
        double sustained = magazine * 1200.0 / (r.drawTicks() + burstTicks);
        double vanilla = perMinute(WeaponStats.VANILLA_CROSSBOW, true, 0, 0, 1.0);
        assertTrue(sustained >= 0.55 * vanilla, "back to back it deals " + sustained + " a minute, a vanilla crossbow " + vanilla);
        assertTrue(sustained / 60 < DIAMOND_SWORD_DPS, "back to back it deals " + sustained / 60 + " a second, a diamond sword " + DIAMOND_SWORD_DPS);
    }

    /**
     * Gear never replaces a good shot: no bow released untimed at full draw, shot after shot, deals as much a second as
     * vanilla's bow with every shot Perfect. Ten blocks out, body hits.
     */
    @Test
    void noBowOutshootsATimedVanillaBowUntimed() {
        WeaponStats v = WeaponStats.VANILLA_BOW;
        double perfect = ShotMath.damage(ShotMathTest.launchBase(v), v.damageMult(), 1, true, 1.0, 10, T) / ((v.drawTicks() + T.perfectDelay()) / 20.0);
        for (WeaponStats w : List.of(WeaponStats.SHORTBOW, WeaponStats.LONGBOW, WeaponStats.RECURVE_BOW)) {
            double untimed = ShotMath.damage(ShotMathTest.launchBase(w), w.damageMult(), 1, false, 1.0, 10, T) / (w.drawTicks() / 20.0);
            assertTrue(untimed < perfect, w + ": " + untimed + " a second untimed, vanilla's bow Perfect every shot " + perfect);
        }
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

    @Test
    void marksmanDuelableWithAVanillaBow() {
        // from the ground, up at its head in the gap 14 above the player's eyes
        int draw = WeaponStats.VANILLA_BOW.drawTicks();
        // up to 18 blocks out, a vanilla bow drawn at its creak lands before it looses
        int near = MarksmanRules.flightTicks(18, MarksmanRules.pitch(18, 14, 3.0, MarksmanRules.ARROW_GRAVITY), 3.0);
        assertTrue(draw + near <= MarksmanRules.drawTicks(false), "drawn at its creak from 18 blocks, a vanilla bow lands after " + (draw + near) + " ticks");
        // farther out, and below half health anywhere, it is too slow for that: the bow is drawn as it ducks, held through
        // its cover, and let go as it shows in the gap, before the tremble starts
        int far = MarksmanRules.flightTicks(25, MarksmanRules.pitch(25, 14, 3.0, MarksmanRules.ARROW_GRAVITY), 3.0);
        assertTrue(far <= MarksmanRules.drawTicks(true), "from 25 blocks the arrow flies " + far + " ticks");
        assertTrue(MarksmanRules.COVER_TICKS - draw <= T.trembleStartSeconds() * 20, "held " + (MarksmanRules.COVER_TICKS - draw) + " ticks at full draw");
    }

    private static void assertRatio(double armour, double toughness, double ceiling, int quick) {
        double vanilla = perMinute(WeaponStats.VANILLA_CROSSBOW, true, armour, toughness, 1.0, quick);
        double arbalest = perMinute(WeaponStats.ARBALEST, false, armour, toughness, BoltTraits.ARBALEST.armourKept(), quick);
        assertTrue(arbalest <= ceiling * vanilla, "Quick Charge " + quick + ", armour " + armour + "/" + toughness + ": Arbalest " + arbalest
                + " damage a minute against a vanilla crossbow's " + vanilla + " (at most " + ceiling + " ×)");
    }

    /**
     * A player's mean damage a minute with a crossbow from a plain arrow (base 2) through armour, firing as fast as it
     * loads. Vanilla's bolt from a player always crits: ceil(speed × base) plus nextInt(hit / 2 + 2); a Full Draw
     * crossbow's doesn't. Quick Charge cuts vanilla's 1.25 s by 0.25 s a level, and a Full Draw crossbow's load by the
     * same share.
     */
    private static double perMinute(WeaponStats w, boolean crit, double armour, double toughness, double armourKept) {
        return perMinute(w, crit, armour, toughness, armourKept, 0);
    }

    private static double perMinute(WeaponStats w, boolean crit, double armour, double toughness, double armourKept, int quick) {
        float seconds = WeaponStats.VANILLA_CHARGE_SECONDS - 0.25F * quick;
        int ticks = w == WeaponStats.VANILLA_CROSSBOW ? (int) Math.floor(seconds * 20.0F) : w.chargeTicks(seconds);
        int hit = ShotMath.vanillaDamage(3.15 * w.velocityMult(), 2.0 * w.damageMult());
        int bonuses = crit ? hit / 2 + 2 : 1;
        double total = 0;
        for (int bonus = 0; bonus < bonuses; bonus++) total += afterArmour(hit + bonus, armour * armourKept, toughness);
        return total / bonuses * 1200.0 / ticks;
    }

    /** Vanilla's armour formula ({@code CombatRules.getDamageAfterAbsorb}, no enchantments). */
    private static double afterArmour(double damage, double armour, double toughness) {
        double effective = Math.clamp(armour - damage / (2.0 + toughness / 4.0), armour * 0.2, 20.0);
        return damage * (1.0 - effective / 25.0);
    }
}
