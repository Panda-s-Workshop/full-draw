package com.panda.fulldraw.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** What the Shieldbearer's tower shield stops, and how many Shieldbearers join a raid's wave. */
class TowerShieldTest {
    /** A projectile coming at a bearer that faces +Z (yaw 0), from this many degrees off its facing, flat. */
    private static boolean fromAngle(double degrees) {
        double a = Math.toRadians(degrees);
        // it sits out along the angle, (sin a, cos a) from the bearer, and flies back toward it
        return TowerShield.stops(0.0F, -Math.sin(a), 0.0, -Math.cos(a));
    }

    @Test
    void stopsWhatComesFromTheFront() {
        assertTrue(TowerShield.stops(0.0F, 0.0, 0.0, -2.0), "head on, at a bearer facing +Z");
        assertTrue(TowerShield.stops(90.0F, 2.0, 0.0, 0.0), "head on, at a bearer facing -X");
        assertTrue(TowerShield.stops(-90.0F, -2.0, -0.3, 0.0), "head on, at a bearer facing +X, falling a little");
    }

    @Test
    void theArcEndsAtSixtyDegrees() {
        assertTrue(fromAngle(0));
        assertTrue(fromAngle(59));
        assertTrue(fromAngle(-59));
        assertFalse(fromAngle(61));
        assertFalse(fromAngle(-61));
        assertFalse(fromAngle(90), "from the side");
        assertFalse(fromAngle(180), "from behind");
    }

    @Test
    void steepShotsDropInOverTheTop() {
        double flat = 1.0;
        double at34 = Math.tan(Math.toRadians(34)) * flat, at36 = Math.tan(Math.toRadians(36)) * flat;
        assertTrue(TowerShield.stops(0.0F, 0.0, -at34, -flat), "34 degrees down still hits the shield");
        assertFalse(TowerShield.stops(0.0F, 0.0, -at36, -flat), "36 degrees down comes over the top");
        assertTrue(TowerShield.stops(0.0F, 0.0, at36, -flat), "rising steeply still meets the shield");
        assertFalse(TowerShield.stops(0.0F, 0.0, -2.0, 0.0), "straight down");
    }

    @Test
    void aSprintingPlayerCanCircleIt() {
        // a player sprinting round a bearer gains on its shield out to this radius; a bow's draw is about a second, so
        // the range should leave room to sprint round at a sensible shooting distance
        assertTrue(TowerShield.outCircleRadius() >= 6.0, "out-circle radius " + TowerShield.outCircleRadius());
        assertTrue(TowerShield.outCircleRadius() <= 8.0, "it should still turn: out-circle radius " + TowerShield.outCircleRadius());
        assertEquals(60, TowerShield.CRACK_TICKS, "a crack lasts as long as a player's shield is cracked: 3 s");
    }

    @Test
    void shieldbearersFromTheThirdWave() {
        for (int difficulty = 1; difficulty <= 3; difficulty++) {
            assertEquals(0, TowerShield.perWave(1, difficulty, 0.0));
            assertEquals(0, TowerShield.perWave(2, difficulty, 0.9));
        }
        assertEquals(1, TowerShield.perWave(3, 1, 0.9), "Easy: one");
        assertEquals(1, TowerShield.perWave(3, 2, 0.2), "Normal: one or two");
        assertEquals(2, TowerShield.perWave(5, 2, 0.7));
        assertEquals(2, TowerShield.perWave(7, 3, 0.0), "Hard: two");
        assertEquals(0, TowerShield.perWave(5, 0, 0.0), "Peaceful: none");
    }
}
