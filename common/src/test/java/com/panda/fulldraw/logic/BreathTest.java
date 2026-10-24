package com.panda.fulldraw.logic;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Steady Aim's breath: 2.5 s (+1 s per Steady level), one breath per draw, refills in 4 s between draws,
 * running out spends the archer for the rest of the draw.
 */
class BreathTest {
    private static Breath run(Breath b, boolean focus, int ticks, int steady) {
        for (int i = 0; i < ticks; i++) b = b.tick(focus, true, steady);
        return b;
    }

    private static Breath rest(Breath b, int ticks) {
        for (int i = 0; i < ticks; i++) b = b.tick(false, false, 0);
        return b;
    }

    @Test
    void focusLastsTwoAndAHalfSeconds() {
        Breath b = run(Breath.FULL, true, 49, 0);
        assertTrue(b.focusing(), "still holding at 49 ticks");
        b = b.tick(true, true, 0);
        assertFalse(b.focusing());
        assertTrue(b.exhausted(), "out of breath at 50 ticks");
        assertEquals(0, b.exhaustedTicks());
        assertEquals(5, run(b, true, 5, 0).exhaustedTicks(), "the exhausted clock runs while the focus is held");
    }

    @Test
    void steadyAddsASecondPerLevel() {
        assertTrue(run(Breath.FULL, true, 89, 2).focusing());
        assertTrue(run(Breath.FULL, true, 90, 2).exhausted());
    }

    @Test
    void spentForTheRestOfTheDraw() {
        Breath spent = run(Breath.FULL, true, 50, 0);
        Breath stood = run(spent, false, 20, 0);
        assertTrue(stood.exhausted(), "standing up doesn't end the exhaustion while the bow stays drawn");
        assertEquals(20, stood.exhaustedTicks());
        assertFalse(run(stood, true, 5, 0).focusing(), "sneaking again in the same draw gets no new breath");
    }

    @Test
    void noRefillWhileTheBowIsDrawn() {
        Breath part = run(Breath.FULL, true, 25, 0);
        Breath stood = run(part, false, 80, 0);
        assertEquals(0.5, stood.fill(), 1e-9, "standing at full draw keeps the breath where it was");
        assertFalse(stood.focusing());
        assertTrue(run(stood, true, 24, 0).focusing(), "and sneaking again uses what's left");
        assertTrue(run(stood, true, 25, 0).exhausted());
    }

    @Test
    void breathRefillsInFourSecondsBetweenDraws() {
        Breath empty = run(Breath.FULL, true, 60, 0);
        Breath half = rest(empty, 40);
        assertFalse(half.exhausted(), "lowering the bow ends the exhaustion");
        assertEquals(0.5, half.fill(), 1e-9);
        assertEquals(Breath.FULL, rest(empty, 80));
        assertEquals(Breath.FULL, rest(empty, 200), "never above full");
    }

    @Test
    void aNewBreathNeedsAFifthOfTheLungs() {
        Breath low = rest(run(Breath.FULL, true, 60, 0), 15);
        assertEquals(0.1875, low.fill(), 1e-9);
        assertFalse(run(low, true, 3, 0).focusing(), "too little breath to steady");
        Breath enough = rest(low, 2);
        assertTrue(enough.tick(true, true, 0).focusing(), "a little over 0.2 is enough");
    }

    @Test
    void eventsMarkInhaleAndExhale() {
        Breath held = Breath.FULL.tick(true, true, 0);
        assertEquals(Breath.Event.INHALE, Breath.event(Breath.FULL, held));
        Breath last = run(Breath.FULL, true, 49, 0);
        assertEquals(Breath.Event.EXHALE, Breath.event(last, last.tick(true, true, 0)));
        assertEquals(Breath.Event.NONE, Breath.event(held, held.tick(true, true, 0)));
    }
}
