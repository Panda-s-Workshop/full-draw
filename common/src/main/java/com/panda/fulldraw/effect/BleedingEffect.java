package com.panda.fulldraw.effect;

import com.panda.fulldraw.logic.ArrowTraits;
import com.panda.fulldraw.registry.ModEffects;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * A broadhead's wound: {@link ArrowTraits#BLEED_DAMAGE} a second, through armour, the first a second after the hit.
 * Like poison it keeps vanilla's damage cooldown, so a tick that lands right after a harder hit is lost.
 */
public class BleedingEffect extends MobEffect {
    public BleedingEffect() {
        super(MobEffectCategory.HARMFUL, 0x8E1B1B);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        // the duration counts down: 61, 41, 21 and 1 of 80 are 1, 2, 3 and 4 seconds after the hit
        return duration % ArrowTraits.BLEED_INTERVAL == 1;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        var type = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ModEffects.BLEEDING_DAMAGE);
        entity.hurtServer(level, new DamageSource(type), ArrowTraits.BLEED_DAMAGE * (amplifier + 1));
        return true;
    }
}
