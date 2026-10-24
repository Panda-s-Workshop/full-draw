package com.panda.fulldraw.shot;

import com.panda.fulldraw.logic.WeakSpot;
import java.util.Map;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * Each entity type's weak spot: the data overrides from {@code fulldraw/weak_spot}, else the default head
 * zone from the entity's eye height.
 */
public final class WeakSpots {
    private static volatile Map<EntityType<?>, WeakSpot> overrides = Map.of();

    private WeakSpots() {}

    public static WeakSpot of(LivingEntity entity) {
        WeakSpot spot = overrides.get(entity.getType());
        return spot != null ? spot : WeakSpot.head(entity.getEyeHeight(), entity.getBbHeight());
    }

    /** Replaces the data overrides (the reload listener calls this). */
    public static void set(Map<EntityType<?>, WeakSpot> loaded) {
        overrides = Map.copyOf(loaded);
    }

    public static Map<EntityType<?>, WeakSpot> overrides() {
        return overrides;
    }
}
