package com.panda.fulldraw.logic;

/**
 * The Gale Harrier's flight, as numbers: how high and how wide it circles, how long it shrieks before a dive, the dive's
 * straight line and speed, how long it stays down when an archer grounds it, and how long between dives.
 */
public final class HarrierFlight {
    /** It circles this far above the ground under the one it hunts, at least... */
    public static final int CIRCLE_HEIGHT_MIN = 20;
    /** ...and at most. */
    public static final int CIRCLE_HEIGHT_MAX = 30;
    /** The circle's radius, in blocks. */
    public static final double CIRCLE_RADIUS = 10.0;
    /** Circling speed, in blocks a tick (9 m/s). */
    public static final double CIRCLE_SPEED = 0.45;
    /** The tell before a dive: a shriek, the wings folded, hanging still for a second. */
    public static final int TELL_TICKS = 20;
    /** Diving speed, in blocks a tick (22 m/s): a dive from the circle reaches the ground in a little over a second. */
    public static final double DIVE_SPEED = 1.1;
    /** A dive that has met nothing by now gives up and climbs. */
    public static final int DIVE_MAX_TICKS = 60;
    /** Climbing speed, in blocks a tick. */
    public static final double CLIMB_SPEED = 0.55;
    /** Ticks from one tell to the next, at least... */
    public static final int DIVE_GAP_MIN = 120;
    /** ...and at most (6 to 10 s). */
    public static final int DIVE_GAP_MAX = 200;
    /** A Perfect shot or a headshot in a dive grounds it for 3 s on the ground... */
    public static final int GROUNDED_TICKS = 60;
    /** ...counted from when it lands, or from this many ticks after it was hit if it falls that long. */
    public static final int GROUNDED_FALL_MAX = 100;
    /** ...when melee hits it twice as hard. */
    public static final float GROUNDED_MELEE_MULTIPLIER = 2.0F;
    /** It hunts players within this many blocks across... */
    public static final double HUNT_RANGE = 48.0;
    /** It tells only when it is within this many blocks of its circle's height, so every dive is a dive from the circle. */
    public static final double CIRCLE_SLACK = 3.0;
    /**
     * In a dive its head leads along the line: a hit counts as a headshot when it lands in the leading part of its box,
     * at least this share of the way from the middle to the box's front along the dive.
     */
    public static final double DIVE_HEAD_SHARE = 0.3;

    private HarrierFlight() {}

    /** Ticks until the next dive, from a roll between 0 and 1. */
    public static int diveGap(double roll) {
        return DIVE_GAP_MIN + (int) Math.min(DIVE_GAP_MAX - DIVE_GAP_MIN, Math.floor(roll * (DIVE_GAP_MAX - DIVE_GAP_MIN + 1)));
    }

    /** The dive's line: a unit vector from where it starts toward where the target was when it set off. */
    public static double[] direction(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
        double x = toX - fromX, y = toY - fromY, z = toZ - fromZ;
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < 1.0E-6) return new double[] {0.0, -1.0, 0.0};
        return new double[] {x / length, y / length, z / length};
    }

    /** Whether a diver at a point, flying along {@code dir}, has gone past the target: it now lies behind. */
    public static boolean passed(double x, double y, double z, double[] dir, double targetX, double targetY, double targetZ) {
        return (targetX - x) * dir[0] + (targetY - y) * dir[1] + (targetZ - z) * dir[2] < 0.0;
    }

    /** How steeply a dive goes down, in degrees below level. */
    public static double divePitch(double[] dir) {
        return Math.toDegrees(Math.atan2(-dir[1], Math.sqrt(dir[0] * dir[0] + dir[2] * dir[2])));
    }

    /** Ticks a dive takes to cover this many blocks. */
    public static double diveTicks(double distance) {
        return distance / DIVE_SPEED;
    }

    /** A point on the circle round an anchor at an angle in radians, at the anchor's height. */
    public static double[] circlePoint(double anchorX, double anchorY, double anchorZ, double angle) {
        return new double[] {anchorX + Math.cos(angle) * CIRCLE_RADIUS, anchorY, anchorZ + Math.sin(angle) * CIRCLE_RADIUS};
    }

    /** How far round the circle it goes in a tick, in radians. */
    public static double circleStep() {
        return CIRCLE_SPEED / CIRCLE_RADIUS;
    }

    /**
     * Whether a hit at (dx, dy, dz) from the middle of a diver's box (half {@code halfWidth} across, half
     * {@code halfHeight} up) is on its head: the leading part of the box along the dive's line {@code dir}.
     */
    public static boolean diveHeadshot(double dx, double dy, double dz, double[] dir, double halfWidth, double halfHeight) {
        double front = halfWidth * (Math.abs(dir[0]) + Math.abs(dir[2])) + halfHeight * Math.abs(dir[1]);
        return front > 0.0 && dx * dir[0] + dy * dir[1] + dz * dir[2] >= DIVE_HEAD_SHARE * front;
    }

    /** Whether a hit in a dive grounds it: a Perfect Release or a headshot, only while diving. */
    public static boolean grounds(boolean diving, boolean perfect, boolean precision) {
        return diving && (perfect || precision);
    }
}
