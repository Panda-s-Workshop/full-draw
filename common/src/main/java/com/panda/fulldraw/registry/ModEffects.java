package com.panda.fulldraw.registry;

import com.panda.fulldraw.FullDrawMod;
import com.panda.fulldraw.effect.BleedingEffect;
import com.panda.fulldraw.platform.registry.DeferredHolder;
import com.panda.fulldraw.platform.registry.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, FullDrawMod.MODID);

    public static final DeferredHolder<MobEffect, BleedingEffect> BLEEDING = EFFECTS.register("bleeding", BleedingEffect::new);

    /** The bleed's damage (data: through armour, no knockback). */
    public static final ResourceKey<DamageType> BLEEDING_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, FullDrawMod.id("bleeding"));

    /** Creatures with no blood to lose: a broadhead hits them but they don't bleed. */
    public static final TagKey<EntityType<?>> CANNOT_BLEED = TagKey.create(Registries.ENTITY_TYPE, FullDrawMod.id("cannot_bleed"));

    private ModEffects() {}
}
