package com.panda.fulldraw.entity;

import com.panda.fulldraw.logic.LurkerRules;
import com.panda.fulldraw.registry.ModEntities;
import com.panda.fulldraw.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A Bog Lurker's glob of swamp slime: it hurts a little and leaves Slowness I behind. It is a mob's projectile, so
 * Projectile Protection and anything else that answers projectiles answers it.
 */
public class BogSpit extends ThrowableItemProjectile {
    public BogSpit(EntityType<? extends BogSpit> type, Level level) {
        super(type, level);
    }

    public BogSpit(Level level, LivingEntity owner, Vec3 from) {
        super(ModEntities.BOG_SPIT.get(), from.x, from.y, from.z, level, new net.minecraft.world.item.ItemStack(Items.SLIME_BALL));
        setOwner(owner);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.SLIME_BALL;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            if (tickCount % 2 == 0) level().addParticle(ParticleTypes.ITEM_SLIME, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
        } else if (isInWater()) {
            // a glob that falls short into the water is lost in it
            ((ServerLevel) level()).sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.1, getZ(), 4, 0.1, 0.0, 0.1, 0.0);
            discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        Entity entity = hit.getEntity();
        if (!(level() instanceof ServerLevel level) || !(entity instanceof LivingEntity target)) return;
        LivingEntity owner = getOwner() instanceof LivingEntity living ? living : null;
        if (target.hurtServer(level, damageSources().mobProjectile(this, owner), LurkerRules.SPIT_DAMAGE))
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, LurkerRules.SPIT_SLOW_TICKS, 0), this);
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (level() instanceof ServerLevel level) {
            // a small splat: struck in the face, a big one would hide what the player was aiming at
            level.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY(), getZ(), 4, 0.1, 0.1, 0.1, 0.03);
            level.playSound(null, getX(), getY(), getZ(), ModSounds.LURKER_SPIT_HIT.get(), getSoundSource(), 1.0F, 0.9F + random.nextFloat() * 0.2F);
            discard();
        }
    }
}
