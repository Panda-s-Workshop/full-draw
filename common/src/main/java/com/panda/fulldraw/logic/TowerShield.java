package com.panda.fulldraw.logic;

/**
 * The Shieldbearer's tower shield, as numbers: which projectiles it stops, how long a hit on it keeps it down, and how
 * many Shieldbearers join a raid's wave.
 */
public final class TowerShield {
    /** The shield covers this far either side of the way its bearer faces. */
    public static final double HALF_ARC_DEGREES = 60.0;
    /** A projectile coming down at least this steeply drops in over the top. */
    public static final double OVER_THE_TOP_DEGREES = 35.0;
    /** A Blunt arrow on the shield knocks it aside for 1.5 s. */
    public static final int STAGGER_TICKS = 30;
    /** A bolt from a shield-piercing weapon, stopped by the shield, cracks it down as long as it lowers a player's shield. */
    public static final int CRACK_TICKS = Math.round(BoltTraits.SHIELD_CRACK_SECONDS * 20.0F);
    /**
     * How fast a fighting bearer turns its shield toward what it watches, in degrees a tick (50 a second, 90 degrees in
     * under 2 s): a sprinting player out-circles it within about 6 blocks.
     */
    public static final float TURN_DEGREES = 2.5F;
    /** A player's sprint, in blocks a second. */
    public static final double SPRINT_SPEED = 5.612;
    /** The share of a pillager outpost's spawns that are Shieldbearers (still at most one about it at a time). */
    public static final int OUTPOST_SHARE_IN = 10;
    /** The first raid wave Shieldbearers join. */
    public static final int FIRST_WAVE = 3;

    private TowerShield() {}

    /**
     * Whether a raised shield on a bearer facing {@code bodyYaw} (Minecraft's degrees: 0 faces +Z, 90 faces −X) stops a
     * projectile moving by ({@code x}, {@code y}, {@code z}): it comes from within the arc and isn't dropping in over the
     * top. A projectile falling straight down always gets past.
     */
    public static boolean stops(float bodyYaw, double x, double y, double z) {
        double flat = Math.sqrt(x * x + z * z);
        if (flat < 1.0E-6) return false;
        if (Math.toDegrees(Math.atan2(-y, flat)) >= OVER_THE_TOP_DEGREES) return false;
        double yaw = Math.toRadians(bodyYaw);
        double facingX = -Math.sin(yaw), facingZ = Math.cos(yaw);
        // it comes from the way it's moving away from
        double cos = (-x * facingX - z * facingZ) / flat;
        return cos >= Math.cos(Math.toRadians(HALF_ARC_DEGREES)) - 1.0E-9;
    }

    /** The farthest a sprinting player can circle a fighting bearer and still gain on its shield, in blocks. */
    public static double outCircleRadius() {
        return SPRINT_SPEED / Math.toRadians(TURN_DEGREES * 20.0);
    }

    /**
     * How many Shieldbearers join a raid's wave: none before {@link #FIRST_WAVE}, then one on Easy, one or two on Normal
     * (by {@code roll}, 0 to 1), two on Hard. {@code difficulty} is vanilla's id: 1 Easy, 2 Normal, 3 Hard.
     */
    public static int perWave(int wave, int difficulty, double roll) {
        if (wave < FIRST_WAVE || difficulty < 1) return 0;
        return switch (difficulty) {
            case 1 -> 1;
            case 2 -> roll < 0.5 ? 1 : 2;
            default -> 2;
        };
    }
}
