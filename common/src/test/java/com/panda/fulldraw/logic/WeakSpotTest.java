package com.panda.fulldraw.logic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The weak-spot box maths. */
class WeakSpotTest {
    // a zombie: 0.6 wide, 1.95 tall, eyes at 1.74
    private static final double W = 0.6, H = 1.95, EYE = 1.74;

    @Test
    void defaultHeadZoneStartsAtTheNeck() {
        WeakSpot head = WeakSpot.head(EYE, H);
        double from = EYE - 0.15 * H; // 1.4475: a zombie's head starts at 1.46
        assertFalse(head.hit(0, 1.35, 0.3, 0, W, H), "the upper chest is not the head");
        assertTrue(head.hit(0, 1.8, 0, 0, W, H), "the face");
        assertTrue(head.hit(0, from + 0.01, 0.3, 0, W, H));
        assertFalse(head.hit(0, from - 0.01, 0.3, 0, W, H), "the chest");
        assertFalse(head.hit(0, 0.5, 0, 0, W, H), "the legs");
        assertTrue(head.hit(0.5, 2.1, 0.5, 33, W, H), "the top of the inflated box, any yaw");
    }

    @Test
    void onlyTheSilhouetteCountsNotTheHitMargin() {
        // an arrow flying +Z at a zombie: the hit point is on the front face of the inflated box
        assertTrue(WeakSpot.onSilhouette(0.0, 1.8, -0.6, 0, 1, W, H), "the face");
        assertTrue(WeakSpot.onSilhouette(0.38, 1.8, -0.6, 0, 1, W, H), "the edge of the head, within an arrow's width");
        assertFalse(WeakSpot.onSilhouette(0.0, 2.2, -0.6, 0, 1, W, H), "over the head, in vanilla's 0.3 margin");
        assertFalse(WeakSpot.onSilhouette(0.55, 1.8, -0.6, 0, 1, W, H), "beside the head");
        // the same offset across a diagonal flight
        double d = Math.sqrt(0.5);
        assertTrue(WeakSpot.onSilhouette(-0.2, 1.8, -0.2, d, d, W, H), "on the centre line of a 45 degree path");
        assertFalse(WeakSpot.onSilhouette(0.5, 1.8, -0.5, d, d, W, H), "0.71 across a 45 degree path");
    }

    @Test
    void noneNeverHits() {
        assertFalse(WeakSpot.NONE.hit(0, 1.8, 0, 0, W, H));
    }

    @Test
    void boxesRotateWithTheEntity() {
        // a weak spot on the entity's back half only (local z from 0 to 0.5)
        WeakSpot back = new WeakSpot(List.of(new WeakSpot.Box(0, 0, 0, 1, 1, 0.5)), 2.0);
        // yaw 0 faces +Z: the back is at -Z
        assertTrue(back.hit(0, 1, -0.2, 0, W, H));
        assertFalse(back.hit(0, 1, 0.2, 0, W, H));
        // yaw 180 faces -Z: the back is at +Z
        assertTrue(back.hit(0, 1, 0.2, 180, W, H));
        assertFalse(back.hit(0, 1, -0.2, 180, W, H));
        // yaw 90 faces -X: the back is at +X
        assertTrue(back.hit(0.2, 1, 0, 90, W, H));
        assertFalse(back.hit(-0.2, 1, 0, 90, W, H));
    }
}
