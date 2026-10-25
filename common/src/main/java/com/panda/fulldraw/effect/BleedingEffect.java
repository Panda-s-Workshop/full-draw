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
 * Like poison it keeps vanilla's damage cooldown, so a tick that lands right after a harder hit is lost. Each tick
 * drips: dark-red dust falls from the wound and a thick drop sounds, so a bleed reads apart from any other hit.
 */
public class BleedingEffect extends MobEffect {
    /** The drip: falling dust in the colour of a redstone block's, which reads as blood. */
    private static final net.minecraft.core.particles.BlockParticleOption DRIP = new net.minecraft.core.particles.BlockParticleOption(
            net.minecraft.core.particles.ParticleTypes.FALLING_DUST, net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState());

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
        // on the body's surface, a little out from it, between waist and chest: inside the body they'd be hidden
        double out = entity.getBbWidth() * 0.5 + 0.08;
        for (int i = 0; i < 6; i++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2;
            level.sendParticles(DRIP, entity.getX() + Math.cos(angle) * out, entity.getY(0.45 + level.getRandom().nextDouble() * 0.3),
                    entity.getZ() + Math.sin(angle) * out, 1, 0.0, 0.0, 0.0, 0.0);
        }
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), com.panda.fulldraw.registry.ModSounds.BLEED.get(), entity.getSoundSource(),
                0.7F, 0.9F + level.getRandom().nextFloat() * 0.2F);
        return true;
    }
}
