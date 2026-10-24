package com.panda.fulldraw.logic;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The bows against melee: what the point-blank penalty is for. */
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
}
