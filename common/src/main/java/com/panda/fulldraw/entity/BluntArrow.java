package com.panda.fulldraw.entity;

import com.panda.fulldraw.logic.ArrowTraits;
import com.panda.fulldraw.network.FullDrawNetwork;
import com.panda.fulldraw.registry.ModEntities;
import com.panda.fulldraw.registry.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Always 1 damage, a hard shove, and whatever attack the target was winding up starts over. */
public class BluntArrow extends FullDrawArrow {
    public BluntArrow(EntityType<? extends BluntArrow> type, Level level) {
        super(type, level);
    }

    public BluntArrow(Level level, LivingEntity owner, ItemStack pickup, @Nullable ItemStack weapon) {
        super(ModEntities.BLUNT_ARROW.get(), owner, level, pickup, weapon);
    }

    public BluntArrow(Level level, double x, double y, double z, ItemStack pickup) {
        super(ModEntities.BLUNT_ARROW.get(), x, y, z, level, pickup);
    }

    @Override
    public ArrowTraits traits() {
        return ArrowTraits.BLUNT;
    }

    @Override
    protected void doPostHurtEffects(LivingEntity target) {
        super.doPostHurtEffects(target);
        interrupt(target);
    }

    /** A 1-damage hit is the one most often turned away by a damage cooldown: the shove and the interrupt land anyway. */
    @Override
    public void onTurnedAway(LivingEntity target, DamageSource source) {
        doKnockback(target, source);
        interrupt(target);
    }

    /**
     * Knocks a mob's attack back to its start: a swelling creeper's fuse goes out with a fizz (one lit with flint and
     * steel stays lit), a melee mob waits out its whole swing again, an archer lets its draw go, and a brain-driven
     * mob (a piglin, a hoglin) cools down as after an attack. Mobs with attacks of their own making (a warden, a
     * breeze, a blaze, an evoker, a ghast) only take the shove.
     */
    public static void interrupt(LivingEntity target) {
        if (target instanceof Creeper creeper && !creeper.isIgnited() && creeper.swell > 0) {
            creeper.swell = 0;
            creeper.setSwellDir(-1);
            if (creeper.level() instanceof ServerLevel level) {
                level.playSound(null, creeper.getX(), creeper.getY(), creeper.getZ(), SoundEvents.GENERIC_EXTINGUISH_FIRE, creeper.getSoundSource(), 0.5F, 1.6F);
                level.sendParticles(ParticleTypes.SMOKE, creeper.getX(), creeper.getY() + 1.2, creeper.getZ(), 8, 0.2, 0.2, 0.2, 0.02);
                // the client counts its own fuse; tell it the fuse is out, or it shows a creeper still about to blow
                FullDrawNetwork.fuseOut(level, creeper);
            }
        }
        if (target instanceof Mob mob) {
            for (WrappedGoal goal : mob.getGoalSelector().getAvailableGoals())
                if (goal.isRunning() && goal.getGoal() instanceof MeleeAttackGoal melee) melee.resetAttackCooldown();
            if (mob.isUsingItem()) mob.stopUsingItem();
        }
        if (target.getBrain().checkMemory(MemoryModuleType.ATTACK_COOLING_DOWN, MemoryStatus.REGISTERED))
            target.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, ArrowTraits.INTERRUPT_TICKS);
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(ModItems.BLUNT_ARROW.get());
    }
}
