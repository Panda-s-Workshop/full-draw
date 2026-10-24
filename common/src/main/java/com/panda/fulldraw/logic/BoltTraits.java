package com.panda.fulldraw.logic;

/**
 * What a heavy crossbow's bolts do on top of their damage. Pure data, like {@link WeaponStats}.
 *
 * @param pierce     extra mobs a bolt passes through, added to Piercing's (2: it hits three in a row)
 * @param armourKept the share of the target's armour points that still counts (0.5: half is ignored)
 * @param knockback  knockback added to the weapon's own, in Punch levels
 */
public record BoltTraits(int pierce, double armourKept, float knockback) {
    public static final BoltTraits ARBALEST = new BoltTraits(2, 0.5, 2.0F);

    /** How long a shield-cracking weapon's bolt knocks a raised shield down, as an axe does (an axe: 5 s). */
    public static final float SHIELD_CRACK_SECONDS = 3.0F;
}
