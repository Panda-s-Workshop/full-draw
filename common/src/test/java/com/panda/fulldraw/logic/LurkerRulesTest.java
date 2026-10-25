package com.panda.fulldraw.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LurkerRulesTest {
    @Test
    void whatItDoesByDistance() {
        assertEquals(LurkerRules.Act.FLEE, LurkerRules.act(3.9));
        assertEquals(LurkerRules.Act.BACK_OFF, LurkerRules.act(4.0));
        assertEquals(LurkerRules.Act.BACK_OFF, LurkerRules.act(7.9));
        assertEquals(LurkerRules.Act.SPIT, LurkerRules.act(8.0));
        assertEquals(LurkerRules.Act.SPIT, LurkerRules.act(16.0));
        assertEquals(LurkerRules.Act.WATCH, LurkerRules.act(16.1));
    }

    @Test
    void spitsEveryTwoAndAHalfToThreeAndAHalfSeconds() {
        assertEquals(LurkerRules.SPIT_GAP_MIN, LurkerRules.spitGap(0.0));
        assertEquals(LurkerRules.SPIT_GAP_MAX, LurkerRules.spitGap(0.999999));
        assertEquals(50, LurkerRules.SPIT_GAP_MIN);
        assertEquals(70, LurkerRules.SPIT_GAP_MAX);
    }

    @Test
    void lurkingOnlyItsEyesShowAboveTheWater() {
        // its box is 0.65 high, and its eye zone (the weak-spot file) is the front of the box from 0.68 of its height up,
        // where the model's eyes stand: lurking, the zone's lower edge is at most a sliver under the surface, so the eyes
        // that show are all in it, and the top of the box stands SHOWS_ABOVE clear of the water
        double height = 0.65, eyeZone = 0.68 * height, surface = 3.0;
        double feet = LurkerRules.lurkY(surface, height);
        assertTrue(surface - feet >= eyeZone - 0.05, "the eye zone starts at most a sliver under the surface");
        assertEquals(LurkerRules.SHOWS_ABOVE, feet + height - surface, 1.0E-9);
        assertTrue(LurkerRules.SHOWS_ABOVE >= 0.15, "enough of the eyes shows to aim at");
        // fleeing, the whole of it is well under
        assertTrue(LurkerRules.sunkY(surface, height) + height < surface - 1.0);
    }

    @Test
    void aGlobAimedHighEnoughArrivesAtItsTarget() {
        // a thrown item: it moves, then loses 1% of its speed and drops 0.03 a tick
        for (double distance : new double[] {LurkerRules.SPIT_MIN, 12.0, LurkerRules.SPIT_MAX}) {
            double lift = LurkerRules.spitLift(distance);
            double len = Math.hypot(distance, lift);
            double vx = distance / len * LurkerRules.SPIT_SPEED, vy = lift / len * LurkerRules.SPIT_SPEED;
            double x = 0.0, y = 0.0;
            while (x < distance) {
                x += vx;
                y += vy;
                vx *= 0.99;
                vy = vy * 0.99 - LurkerRules.SPIT_GRAVITY;
            }
            assertEquals(0.0, y, 0.6, "at " + distance + " blocks it arrives within a chest's height of where it was aimed");
        }
    }
}
