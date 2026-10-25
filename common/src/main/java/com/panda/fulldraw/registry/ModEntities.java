package com.panda.fulldraw.registry;

import com.panda.fulldraw.FullDrawMod;
import com.panda.fulldraw.entity.BluntArrow;
import com.panda.fulldraw.entity.BodkinArrow;
import com.panda.fulldraw.entity.BroadheadArrow;
import com.panda.fulldraw.entity.FullDrawArrow;
import com.panda.fulldraw.platform.registry.DeferredHolder;
import com.panda.fulldraw.platform.registry.DeferredRegister;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(FullDrawMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<BroadheadArrow>> BROADHEAD_ARROW = arrow("broadhead_arrow", BroadheadArrow::new);
    public static final DeferredHolder<EntityType<?>, EntityType<BodkinArrow>> BODKIN_ARROW = arrow("bodkin_arrow", BodkinArrow::new);
    public static final DeferredHolder<EntityType<?>, EntityType<BluntArrow>> BLUNT_ARROW = arrow("blunt_arrow", BluntArrow::new);

    private ModEntities() {}

    /** Sized, tracked and updated as vanilla's arrow. */
    private static <E extends FullDrawArrow> DeferredHolder<EntityType<?>, EntityType<E>> arrow(String name, EntityType.EntityFactory<E> factory) {
        return ENTITIES.registerEntityType(name, factory, MobCategory.MISC,
                b -> b.noLootTable().sized(0.5F, 0.5F).eyeHeight(0.13F).clientTrackingRange(4).updateInterval(20));
    }
}
