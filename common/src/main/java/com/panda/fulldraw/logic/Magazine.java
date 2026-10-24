package com.panda.fulldraw.logic;

/**
 * A crossbow that loads several shots in one crank and fires them one use at a time. Pure data, like {@link WeaponStats}.
 *
 * @param shots     shots one crank loads (each takes one arrow, as a vanilla crossbow's load does)
 * @param shotTicks ticks between two shots
 */
public record Magazine(int shots, int shotTicks) {
    /**
     * Five shots 12 ticks apart. Not closer: a creature that was hurt can't be hurt again by an equal hit while more
     * than 10 of its 20 ticks of damage cooldown are left, so bolts 8 ticks apart would land only every other one on a
     * single target. 12 leaves 2 ticks for a target walking into the bolts.
     */
    public static final Magazine REPEATING_CROSSBOW = new Magazine(5, 12);
}
