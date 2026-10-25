package com.panda.fulldraw.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The Gale Harrier's dive: its line, when it has gone past, how long it takes, and what grounds it. */
class HarrierDiveTest {
    @Test
    void theDiveIsAStraightLineAtWhereTheTargetWas() {
        double[] dir = HarrierFlight.direction(0, 25, 0, 10, 0, 0);
        assertEquals(1.0, Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]), 1.0E-9, "a unit line");
        assertTrue(dir[0] > 0 && dir[1] < 0 && dir[2] == 0, "down and toward the target");
        // from 25 up and 10 across it comes down steeply, about 68 degrees
        assertEquals(Math.toDegrees(Math.atan2(25, 10)), HarrierFlight.divePitch(dir), 1.0E-9);
        double[] straightDown = HarrierFlight.direction(3, 3, 3, 3, 3, 3);
        assertEquals(-1.0, straightDown[1], "on top of its target it drops straight down");
    }

    @Test
    void itHasPassedOnceTheTargetIsBehind() {
        double[] dir = HarrierFlight.direction(0, 25, 0, 10, 0, 0);
        assertFalse(HarrierFlight.passed(0, 25, 0, dir, 10, 0, 0), "at the start the target lies ahead");
        assertFalse(HarrierFlight.passed(9, 2.6, 0, dir, 10, 0, 0), "still ahead just short of it");
        assertTrue(HarrierFlight.passed(11, -2.5, 0, dir, 10, 0, 0), "past it");
        // a target that stepped aside: the line goes by, and once level with it the target is behind
        assertTrue(HarrierFlight.passed(10.5, -1.25, 0, dir, 10, 0, 3), "gone by a target that stepped 3 blocks aside");
    }

    @Test
    void aDiveIsShortButLongEnoughToShootInto() {
        // a dive from the circle (20 to 30 up, up to 10 across) takes about a second: long enough to loose an arrow
        // at it after the tell, too short to wait it out. It tells only near its circle's height, so the shortest is
        // straight down from just under the lowest circle
        double shortest = HarrierFlight.CIRCLE_HEIGHT_MIN - HarrierFlight.CIRCLE_SLACK;
        double longest = Math.hypot(HarrierFlight.CIRCLE_HEIGHT_MAX, HarrierFlight.CIRCLE_RADIUS);
        assertTrue(HarrierFlight.diveTicks(shortest) >= 15, "shortest dive " + HarrierFlight.diveTicks(shortest) + " ticks");
        assertTrue(HarrierFlight.diveTicks(longest) <= 30, "longest dive " + HarrierFlight.diveTicks(longest) + " ticks");
        assertTrue(HarrierFlight.diveTicks(longest) < HarrierFlight.DIVE_MAX_TICKS, "it gives up only after a real dive could have ended");
    }

    @Test
    void divesComeEverySixToTenSeconds() {
        assertEquals(120, HarrierFlight.diveGap(0.0));
        assertEquals(200, HarrierFlight.diveGap(0.9999));
        assertEquals(200, HarrierFlight.diveGap(1.0));
        for (double roll = 0; roll < 1; roll += 0.01) {
            int gap = HarrierFlight.diveGap(roll);
            assertTrue(gap >= 120 && gap <= 200, "gap " + gap);
        }
        assertEquals(20, HarrierFlight.TELL_TICKS, "the tell lasts a second");
    }

    @Test
    void onlyAPerfectOrAHeadshotInADiveGroundsIt() {
        assertTrue(HarrierFlight.grounds(true, true, false));
        assertTrue(HarrierFlight.grounds(true, false, true));
        assertFalse(HarrierFlight.grounds(true, false, false), "a plain hit in a dive");
        assertFalse(HarrierFlight.grounds(false, true, true), "a Perfect headshot while it circles");
        assertEquals(60, HarrierFlight.GROUNDED_TICKS, "down for 3 s");
    }

    @Test
    void aHeadshotInADiveIsOnTheLeadingPartAlongTheLine() {
        // a steep dive (70 degrees) toward +x; the box is 1.0 across and 0.6 high
        double pitch = Math.toRadians(70);
        double[] steep = {Math.cos(pitch), -Math.sin(pitch), 0.0};
        // an arrow head on, aimed at the middle, meets the box where the line leaves it: the bottom face, ahead of the
        // middle, which by the box's yaw alone would be its body
        double t = Math.min(0.5 / steep[0], 0.3 / -steep[1]);
        assertTrue(HarrierFlight.diveHeadshot(steep[0] * t, steep[1] * t, 0.0, steep, 0.5, 0.3), "head on into a steep dive");
        assertFalse(HarrierFlight.diveHeadshot(0.0, 0.0, 0.5, steep, 0.5, 0.3), "into its side");
        assertFalse(HarrierFlight.diveHeadshot(-steep[0] * t, -steep[1] * t, 0.0, steep, 0.5, 0.3), "into its tail");
        // a level dive: the front of the box is the head, as its weak-spot file has it
        double[] level = {1.0, 0.0, 0.0};
        assertTrue(HarrierFlight.diveHeadshot(0.5, 0.0, 0.0, level, 0.5, 0.3), "into its face");
        assertTrue(HarrierFlight.diveHeadshot(0.2, 0.3, 0.2, level, 0.5, 0.3), "the top of its head");
        assertFalse(HarrierFlight.diveHeadshot(0.1, 0.3, 0.0, level, 0.5, 0.3), "its back");
    }

    @Test
    void itCirclesAtItsRadius() {
        double[] p = HarrierFlight.circlePoint(5, 90, -5, Math.PI / 2);
        assertEquals(5, p[0], 1.0E-9);
        assertEquals(90, p[1], 1.0E-9);
        assertEquals(5, p[2], 1.0E-9);
        assertEquals(HarrierFlight.CIRCLE_SPEED, HarrierFlight.circleStep() * HarrierFlight.CIRCLE_RADIUS, 1.0E-12);
    }
}
