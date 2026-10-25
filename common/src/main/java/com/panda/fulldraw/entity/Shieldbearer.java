package com.panda.fulldraw.entity;

import com.panda.fulldraw.logic.TowerShield;
import com.panda.fulldraw.registry.ModSounds;
import com.panda.fulldraw.shot.Weapons;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.util.GoalUtils;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A pillager's cousin behind a tall iron-banded tower shield. The shield stops every projectile that comes from within
 * {@link TowerShield#HALF_ARC_DEGREES} of the way it faces, piercing or not, unless it drops in over the top; in a fight
 * it turns toward what it watches slowly, so an archer can get round it. A Blunt arrow on the shield knocks it aside
 * for a moment, and a bolt from a shield-piercing weapon cracks it down for longer; while it's down the bearer stands
 * dazed and open. Up close it bashes with the shield. Fireballs and wind charges burst on it as on anything else.
 */
public class Shieldbearer extends AbstractIllager {
    /** The shield's state: {@link #RAISED}, {@link #KNOCKED} aside by a Blunt arrow, or {@link #CRACKED} by a bolt. */
    private static final EntityDataAccessor<Byte> SHIELD = SynchedEntityData.defineId(Shieldbearer.class, EntityDataSerializers.BYTE);
    public static final byte RAISED = 0, KNOCKED = 1, CRACKED = 2;
    /** The shield's answer to a projectile it stops (on the server only, once for each projectile). */
    private static final ProjectileDeflection STOPPED = (projectile, entity, random, power) -> {
        if (entity instanceof Shieldbearer bearer) bearer.stop(projectile);
    };

    private int loweredTicks;
    /** On a client: how far the shield is down, 0 to 1, eased over a few ticks, and its value a tick ago. */
    public float lower, lowerO;

    public Shieldbearer(EntityType<? extends Shieldbearer> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 16.0)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SHIELD, RAISED);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Creaking.class, 8.0F, 1.0, 1.2));
        goalSelector.addGoal(3, new RaiderOpenDoorGoal(this));
        goalSelector.addGoal(4, new Raider.HoldGroundAttackGoal(this, 10.0F));
        goalSelector.addGoal(5, new ShieldWallGoal());
        targetSelector.addGoal(1, new HurtByTargetGoal(this, Raider.class).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
        goalSelector.addGoal(8, new RandomStrollGoal(this, 0.6));
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F));
        goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 8.0F));
    }

    /**
     * In a fight (or dazed), the shield, and so the body, turns toward where the bearer looks at
     * {@link TowerShield#TURN_DEGREES} a tick, moving or not. Otherwise it walks as any mob, facing the way it goes.
     */
    @Override
    protected BodyRotationControl createBodyControl() {
        return new BodyRotationControl(this) {
            @Override
            public void clientTick() {
                if (!isAggressive() && !isLowered()) {
                    super.clientTick();
                    return;
                }
                yBodyRot = Mth.approachDegrees(yBodyRot, yHeadRot, TowerShield.TURN_DEGREES);
                yHeadRot = Mth.rotateIfNecessary(yHeadRot, yBodyRot, getMaxHeadYRot());
            }
        };
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        if (!isNoAi() && GoalUtils.hasGroundPathNavigation(this)) getNavigation().setCanOpenDoors(level.isRaided(blockPosition()));
        // dazed while the shield is down: it stands where it was hit
        if (isLowered()) getNavigation().stop();
        super.customServerAiStep(level);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            lowerO = lower;
            lower = Mth.approach(lower, isLowered() ? 1.0F : 0.0F, 0.25F);
        } else if (loweredTicks > 0 && --loweredTicks == 0) {
            entityData.set(SHIELD, RAISED);
        }
    }

    /** How far the shield is down for drawing, 0 (raised) to 1. */
    public float lowered(float partialTick) {
        return Mth.lerp(partialTick, lowerO, lower);
    }

    public boolean isLowered() {
        return entityData.get(SHIELD) != RAISED;
    }

    /** Whether the shield is down from a bolt's crack (rather than knocked aside). */
    public boolean isCracked() {
        return entityData.get(SHIELD) == CRACKED;
    }

    /** Ticks until the shield comes back up; 0 while it's raised. */
    public int loweredTicks() {
        return loweredTicks;
    }

    /**
     * Lowers the shield for at least this many ticks, {@link #KNOCKED} or {@link #CRACKED} (a longer knock already running
     * keeps its time, and a crack stays a crack until the shield is back up).
     */
    public void lowerShield(int ticks, byte how) {
        loweredTicks = Math.max(loweredTicks, ticks);
        entityData.set(SHIELD, (byte) Math.max(entityData.get(SHIELD), how));
    }

    /**
     * Whether the raised shield stops this projectile, by the way it moves. Fireballs and wind charges, which fly on
     * without gravity, aren't stopped: they burst on the bearer as they would on anything.
     */
    public boolean shieldStops(Projectile projectile) {
        if (isLowered() || !isAlive() || projectile instanceof AbstractHurtingProjectile) return false;
        Vec3 motion = projectile.getDeltaMovement();
        return TowerShield.stops(yBodyRot, motion.x, motion.y, motion.z);
    }

    @Override
    public ProjectileDeflection deflection(Projectile projectile) {
        return shieldStops(projectile) ? STOPPED : ProjectileDeflection.NONE;
    }

    /**
     * A projectile the shield stopped: it falls at the bearer's feet. A bolt from a shield-piercing weapon cracks the
     * shield down (with a heavy bolt's clank), a Blunt arrow knocks it aside; anything else thuds off it.
     */
    private void stop(Projectile projectile) {
        Vec3 motion = projectile.getDeltaMovement();
        projectile.setDeltaMovement(-motion.x * 0.08, 0.12, -motion.z * 0.08);
        projectile.needsSync = true;
        if (!(level() instanceof ServerLevel level)) return;
        boolean crack = projectile instanceof AbstractArrow arrow && arrow.getWeaponItem() != null && arrow.getWeaponItem().is(Weapons.SHIELD_PIERCING);
        SoundEvent sound;
        if (crack) {
            lowerShield(TowerShield.CRACK_TICKS, CRACKED);
            level.playSound(null, getX(), getY(), getZ(), ModSounds.BOLT_ARMOUR.get(), getSoundSource(), 1.0F, 0.8F);
            sound = ModSounds.SHIELDBEARER_CRACK.get();
        } else if (projectile instanceof BluntArrow) {
            lowerShield(TowerShield.STAGGER_TICKS, KNOCKED);
            sound = ModSounds.SHIELDBEARER_STAGGER.get();
        } else {
            sound = ModSounds.SHIELDBEARER_BLOCK.get();
        }
        level.playSound(null, getX(), getY(), getZ(), sound, getSoundSource(), 1.0F, 0.9F + random.nextFloat() * 0.2F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SPRUCE_PLANKS.defaultBlockState()), projectile.getX(), projectile.getY(),
                projectile.getZ(), crack ? 14 : 5, 0.1, 0.1, 0.1, 0.1);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit) playSound(ModSounds.SHIELDBEARER_BASH.get(), 1.0F, 0.9F + random.nextFloat() * 0.2F);
        return hit;
    }

    @Override
    public IllagerArmPose getArmPose() {
        return isCelebrating() ? IllagerArmPose.CELEBRATING : IllagerArmPose.NEUTRAL;
    }

    @Override
    public void applyRaidBuffs(ServerLevel level, int wave, boolean isCaptain) {
    }

    /** Both hands are on the shield, so it never carries a banner: patrols and raids keep a pillager or vindicator captain. */
    @Override
    public boolean canBeLeader() {
        return false;
    }

    @Override
    public SoundEvent getCelebrateSound() {
        return SoundEvents.PILLAGER_CELEBRATE;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PILLAGER_DEATH;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (loweredTicks > 0) {
            output.putInt("ShieldDownTicks", loweredTicks);
            if (isCracked()) output.putBoolean("ShieldCracked", true);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        loweredTicks = input.getIntOr("ShieldDownTicks", 0);
        entityData.set(SHIELD, loweredTicks <= 0 ? RAISED : input.getBooleanOr("ShieldCracked", false) ? CRACKED : KNOCKED);
    }

    /** Vanilla's melee, but not while the shield is down: a dazed bearer neither walks nor bashes. */
    private final class ShieldWallGoal extends MeleeAttackGoal {
        ShieldWallGoal() {
            super(Shieldbearer.this, 1.0, false);
        }

        @Override
        public boolean canUse() {
            return !isLowered() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !isLowered() && super.canContinueToUse();
        }
    }
}
