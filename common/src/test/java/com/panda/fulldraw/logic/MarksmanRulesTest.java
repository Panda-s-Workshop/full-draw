package com.panda.fulldraw.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MarksmanRulesTest {
    @Test
    void coverTurnsAwayAllButTheHeadFromBeyondTheParapet() {
        // down behind the parapet: a body hit from beyond it is turned away, a head hit gets through
        assertTrue(MarksmanRules.screens(true, false, 2.9));
        assertFalse(MarksmanRules.screens(true, true, 2.9));
        // standing in the crenel's gap to shoot, nothing is turned away
        assertFalse(MarksmanRules.screens(false, false, 2.9));
        // a blow from the battlements behind it lands, as does one from nowhere (a command, the void)
        assertFalse(MarksmanRules.screens(true, false, -2.9));
        assertFalse(MarksmanRules.screens(true, false, 0.0));
    }

    /**
     * From the ground below its tower (its battlements 16 blocks up, as the watchtower's are) a vanilla bow's full draw
     * can't put an arrow over the parapet into its head while it is ducked: on the flatter arc the arrow still climbs,
     * and meets the parapet below its top, out to about the distance it fights at. So only its rise into the gap gives
     * an archer below a shot.
     */
    @Test
    void theParapetHidesItsHeadFromTheGround() {
        double v = 3.0, g = MarksmanRules.ARROW_GRAVITY, floor = 16.0 - 1.62, head = floor + 1.2, parapetTop = floor + 1.0;
        for (double d = 6; d <= 44; d += 1) {
            double pitch = MarksmanRules.pitch(d, head, v, g);
            if (Double.isNaN(pitch)) continue;
            // the parapet is the block 0.5 to 1.5 nearer the archer than the head
            double over = Math.min(MarksmanRules.heightAt(d - 1.5, pitch, v, g), MarksmanRules.heightAt(d - 0.5, pitch, v, g));
            assertTrue(over < parapetTop, "from " + d + " blocks out the arrow clears the parapet by " + (over - parapetTop));
        }
    }

    @Test
    void itsHealthAndPhases() {
        assertEquals(100.0F, MarksmanRules.MAX_HEALTH);
        assertFalse(MarksmanRules.phaseTwo(50.5F, MarksmanRules.MAX_HEALTH));
        assertTrue(MarksmanRules.phaseTwo(50.0F, MarksmanRules.MAX_HEALTH));
        assertEquals(30, MarksmanRules.drawTicks(false));
        assertEquals(24, MarksmanRules.drawTicks(true));
    }

    @Test
    void whatItLooses() {
        assertEquals(MarksmanRules.Shot.ARROW, MarksmanRules.shot(false, true, false));
        assertEquals(MarksmanRules.Shot.ARROW, MarksmanRules.shot(true, true, true));
        assertEquals(MarksmanRules.Shot.NONE, MarksmanRules.shot(false, false, false));
        assertEquals(MarksmanRules.Shot.FLARE, MarksmanRules.shot(true, false, false));
        assertEquals(MarksmanRules.Shot.BLAST, MarksmanRules.shot(true, false, true));
    }

    @Test
    void theAimCarriesTheArrowToItsMark() {
        double v = MarksmanRules.ARROW_SPEED, g = MarksmanRules.ARROW_GRAVITY;
        // from a tower top 18 blocks up down to the ground, level, and up a hill, near and far
        for (double distance : new double[] {5, 12, 20, 30, 45})
            for (double rise : new double[] {-18, -6, 0, 6}) {
                double pitch = MarksmanRules.pitch(distance, rise, v, g);
                assertFalse(Double.isNaN(pitch), distance + ", " + rise);
                assertEquals(rise, MarksmanRules.heightAt(distance, pitch, v, g), 0.01, distance + ", " + rise);
            }
        // a level shot at 30 blocks aims a few degrees up; straight down the tower it aims steeply down
        double level = Math.toDegrees(MarksmanRules.pitch(30, 0, v, g));
        assertTrue(level > 1.0 && level < 6.0, "level at 30: " + level);
        assertTrue(Math.toDegrees(MarksmanRules.pitch(5, -18, v, g)) < -60.0);
        // out of reach
        assertTrue(Double.isNaN(MarksmanRules.pitch(400, 0, v, g)));
    }

    @Test
    void flightTimeForTheLead() {
        // about 9 ticks to 25 blocks at a full draw; never for a mark out of reach
        int ticks = MarksmanRules.flightTicks(25, Math.toRadians(-20), MarksmanRules.ARROW_SPEED);
        assertTrue(ticks >= 8 && ticks <= 11, "ticks to 25 blocks: " + ticks);
        assertEquals(-1, MarksmanRules.flightTicks(1000, 0, MarksmanRules.ARROW_SPEED));
    }
}
