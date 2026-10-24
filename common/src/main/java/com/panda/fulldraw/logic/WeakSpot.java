package com.panda.fulldraw.logic;

import java.util.List;

/**
 * Where an entity takes precision damage. Boxes are in entity-local fractions of the bounding box: x and z
 * from 0 to 1 across the width with the entity facing +Z, y from 0 at the feet to 1 at the top. Points outside 0..1
 * are allowed, because arrows hit the box inflated by 0.3 blocks.
 *
 * @param boxes      the weak boxes; empty means the entity has none
 * @param multiplier the precision multiplier, or 0 to use the config's default for mobs or players
 */
public record WeakSpot(List<Box> boxes, double multiplier) {
    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        boolean contains(double x, double y, double z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
    }

    /**
     * The share of the height below the eyes that still counts as the head: 0.15 puts the line at the neck of vanilla's
     * humanoids (zombie 1.45 m against a head from 1.46 m; player 1.35 m, exactly its head). 
     */
    public static final double HEAD_MARGIN = 0.15;

    public static final WeakSpot NONE = new WeakSpot(List.of(), 0);

    /** The default head zone: from {@link #HEAD_MARGIN} of the height below the eyes up, across the full width and beyond. */
    public static WeakSpot head(double eyeHeight, double height) {
        double from = (eyeHeight - HEAD_MARGIN * height) / height;
        return new WeakSpot(List.of(new Box(-1, from, -1, 2, 2, 2)), 0);
    }

    /** How far outside its bounding box a hit still counts as on the creature (an arrow's own width). */
    public static final double SILHOUETTE_EDGE = 0.1;

    /**
     * Whether an impact lands on the creature as seen along the arrow's flight, not merely in vanilla's hit margin,
     * which grows to 0.3 blocks around the box after 12 ticks of flight. Only the height and the
     * offset across the arrow's path are checked: the depth along it is where the arrow met the inflated box.
     *
     * @param dx,dy,dz the impact point minus the entity's position (feet, centre)
     * @param dirX,dirZ the arrow's horizontal direction (any length; zero for a vertical shot)
     */
    public static boolean onSilhouette(double dx, double dy, double dz, double dirX, double dirZ, double width, double height) {
        if (dy < -SILHOUETTE_EDGE || dy > height + SILHOUETTE_EDGE) return false;
        double len = Math.hypot(dirX, dirZ);
        double across = len < 1e-6 ? Math.max(Math.abs(dx), Math.abs(dz)) : Math.abs(dx * dirZ - dz * dirX) / len;
        return across <= width / 2 + SILHOUETTE_EDGE;
    }

    /**
     * Whether a world-space impact point hits a weak box.
     *
     * @param dx,dy,dz the impact point minus the entity's position (feet, centre)
     * @param yawDeg   the entity's body yaw in Minecraft degrees (0 faces +Z, 90 faces -X)
     */
    public boolean hit(double dx, double dy, double dz, double yawDeg, double width, double height) {
        if (boxes.isEmpty()) return false;
        double r = Math.toRadians(yawDeg);
        double cos = Math.cos(r), sin = Math.sin(r);
        // rotate the world offset into the entity's frame (inverse of the yaw rotation)
        double localX = dx * cos + dz * sin;
        double localZ = -dx * sin + dz * cos;
        double fx = localX / width + 0.5, fz = localZ / width + 0.5, fy = dy / height;
        for (Box b : boxes) if (b.contains(fx, fy, fz)) return true;
        return false;
    }
}
