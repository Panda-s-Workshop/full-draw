package com.panda.fulldraw.entity;

import com.panda.fulldraw.logic.HarrierFlight;
import com.panda.fulldraw.registry.ModSounds;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A large raptor of the windswept heights. It circles {@link HarrierFlight#CIRCLE_HEIGHT_MIN} to
 * {@link HarrierFlight#CIRCLE_HEIGHT_MAX} blocks above the player it hunts, out of reach, and every 6 to 10 s it shrieks,
 * folds its wings for a second and dives at where the player stood, in a straight line. Diving it can be shot; a Perfect
 * Release or a headshot in the dive brings it down, and on the ground it lies stunned for 3 s, taking double from melee.
 * After a dive it climbs straight back up.
 */
public class GaleHarrier extends Mob implements Enemy {
    public enum Phase {
        CIRCLE, TELL, DIVE, CLIMB, GROUNDED;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(GaleHarrier.class, EntityDataSerializers.BYTE);
    /** A player it can take up the hunt for: in sight, and not hidden by invisibility. */
    private static final TargetingConditions SPOT = TargetingConditions.forCombat().range(64.0);
    /** A player it keeps hunting: out of sight for a moment (a tree, a ledge) doesn't lose it. */
    private static final TargetingConditions KEEP = TargetingConditions.forCombat().range(64.0).ignoreLineOfSight();
    /** No ambient cry this close to a dive, so the shriek that tells it stands alone. */
    private static final int QUIET_BEFORE_DIVE = 40;

    private int phaseTicks;
    private int nextDive = HarrierFlight.DIVE_GAP_MIN;
    private boolean landed;
    private double angle;
    private double clockwise = 1.0;
    private int circleHeight = HarrierFlight.CIRCLE_HEIGHT_MIN;
    private @Nullable Vec3 anchor;
    private Vec3 diveDir = new Vec3(0.0, -1.0, 0.0);
    /** On a client: how far the wings are folded for the tell and the dive, 0 to 1, eased; and its value a tick ago. */
    public float fold, foldO;

    public GaleHarrier(EntityType<? extends GaleHarrier> type, Level level) {
        super(type, level);
        this.xpReward = 8;
        setNoGravity(true);
        // it steers itself every tick; vanilla's look control would only turn the head away from where it flies
        this.lookControl = new LookControl(this) {
            @Override
            public void tick() {
            }
        };
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PHASE, (byte) Phase.CIRCLE.ordinal());
    }

    public Phase phase() {
        return Phase.values()[entityData.get(PHASE)];
    }

    /** Ticks spent in the current phase (on the server); grounded, ticks since it landed. */
    public int phaseTicks() {
        return phaseTicks;
    }

    /** Ticks until its next tell (on the server); it counts down while it has a target. */
    public int nextDive() {
        return nextDive;
    }

    /** Whether a grounded harrier has reached the ground (its 3 s count from then). */
    public boolean landed() {
        return landed;
    }

    /** The centre of its circle, or null before it has one. */
    public @Nullable Vec3 anchor() {
        return anchor;
    }

    private void setPhase(Phase phase) {
        entityData.set(PHASE, (byte) phase.ordinal());
        phaseTicks = 0;
        setNoGravity(phase != Phase.GROUNDED);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            foldO = fold;
            fold = Mth.approach(fold, phase() == Phase.TELL || phase() == Phase.DIVE ? 1.0F : 0.0F, 0.2F);
            // a dive leaves a thick streak of torn air behind it, filled in along each tick's move so it reads from the
            // ground 20 blocks below as one line (and past the usual particle range: it may be 30 up)
            if (phase() == Phase.DIVE)
                for (int i = 0; i < 3; i++) {
                    double t = i / 3.0;
                    level().addParticle(ParticleTypes.CLOUD, true, true, Mth.lerp(t, xo, getX()), Mth.lerp(t, yo, getY()) + getBbHeight() * 0.5,
                            Mth.lerp(t, zo, getZ()), (random.nextDouble() - 0.5) * 0.04, (random.nextDouble() - 0.5) * 0.04,
                            (random.nextDouble() - 0.5) * 0.04);
                }
        }
    }

    /** How far the wings are folded for drawing, 0 (spread) to 1. */
    public float folded(float partialTick) {
        return Mth.lerp(partialTick, foldO, fold);
    }

    /** Its body always faces the way it flies, and its head with it. */
    @Override
    protected BodyRotationControl createBodyControl() {
        return new BodyRotationControl(this) {
            @Override
            public void clientTick() {
                yHeadRot = getYRot();
                yBodyRot = getYRot();
            }
        };
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                                                  @Nullable SpawnGroupData group) {
        circleHeight = HarrierFlight.CIRCLE_HEIGHT_MIN + random.nextInt(HarrierFlight.CIRCLE_HEIGHT_MAX - HarrierFlight.CIRCLE_HEIGHT_MIN + 1);
        clockwise = random.nextBoolean() ? 1.0 : -1.0;
        angle = random.nextDouble() * Math.PI * 2.0;
        // two that spawn together don't dive together
        nextDive = HarrierFlight.diveGap(random.nextDouble());
        // a natural spawn on a peak takes off into the air at once
        if (reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION) setPos(getX(), getY() + 6.0, getZ());
        return super.finalizeSpawn(level, difficulty, reason, group);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        phaseTicks++;
        LivingEntity target = huntTarget(level);
        // the time to the next tell runs whatever it is doing, so tells come 6 to 10 s apart
        if (target != null && phase() != Phase.GROUNDED && nextDive > 0) nextDive--;
        switch (phase()) {
            case CIRCLE -> circle(level, target);
            case TELL -> tell(target);
            case DIVE -> dive(level, target);
            case CLIMB -> climb(level);
            case GROUNDED -> grounded(level);
        }
        // in the tell it faces its target; on the ground it lies level
        if (phase() == Phase.GROUNDED) setXRot(0.0F);
        else if (phase() != Phase.TELL) face(getDeltaMovement());
    }

    /** The player it hunts: kept while it is still fair game and in range, else the nearest one it can see. */
    private @Nullable LivingEntity huntTarget(ServerLevel level) {
        LivingEntity target = getTarget();
        if (target != null && !huntable(level, target, KEEP)) {
            setTarget(null);
            target = null;
        }
        if (target == null && (tickCount + getId()) % 20 == 0) {
            Player nearest = null;
            double best = Double.MAX_VALUE;
            for (Player player : level.players()) {
                if (!huntable(level, player, SPOT)) continue;
                double d = player.distanceToSqr(this);
                if (d < best) {
                    best = d;
                    nearest = player;
                }
            }
            if (nearest != null) setTarget(nearest);
            target = nearest;
        }
        return target;
    }

    /** It seeks out players only; a target given it otherwise (a command, a test) is kept on the same terms. */
    private boolean huntable(ServerLevel level, LivingEntity target, TargetingConditions conditions) {
        if (target.level() != level || target instanceof Player player && (player.isCreative() || player.isSpectator())) return false;
        if (!conditions.test(level, this, target)) return false;
        double dx = target.getX() - getX(), dz = target.getZ() - getZ();
        return dx * dx + dz * dz <= HarrierFlight.HUNT_RANGE * HarrierFlight.HUNT_RANGE && getY() - target.getY() > -16.0;
    }

    /** Round and round above its target (or where it took off), until its next tell. */
    private void circle(ServerLevel level, @Nullable LivingEntity target) {
        if (target != null) anchor = anchorOver(level, target.blockPosition(), target.getY(), target.getX(), target.getZ());
        else if (anchor == null) anchor = anchorOver(level, blockPosition(), getY(), getX(), getZ());
        angle += clockwise * HarrierFlight.circleStep();
        double[] point = HarrierFlight.circlePoint(anchor.x, anchor.y, anchor.z, angle);
        Vec3 to = new Vec3(point[0], point[1], point[2]).subtract(position());
        // well below the circle it climbs harder; near ground below or ahead (a peak, a cliff face) it lifts
        double speed = to.length() > 6.0 ? HarrierFlight.CLIMB_SPEED : HarrierFlight.CIRCLE_SPEED;
        Vec3 want = to.lengthSqr() < 1.0E-6 ? Vec3.ZERO : to.normalize().scale(speed);
        if (terrainClose(level)) want = want.add(0.0, horizontalCollision ? 0.4 : 0.25, 0.0);
        steer(want, 0.2);
        if (target == null) return;
        if (nextDive <= 0 && getY() >= anchor.y - HarrierFlight.CIRCLE_SLACK && hasLineOfSight(target)) startTell(level);
    }

    /** Starts the tell before a dive: the shriek, with the wings snapped shut. Public so tests can start one. */
    public void startTell(ServerLevel level) {
        setPhase(Phase.TELL);
        nextDive = HarrierFlight.diveGap(random.nextDouble());
        playSound(ModSounds.HARRIER_SHRIEK.get(), 4.0F, 0.95F + random.nextFloat() * 0.1F);
        // the shriek's puff: a burst of cloud as the wings snap shut, seen from the ground as well as heard
        level.sendParticles(ParticleTypes.CLOUD, true, true, getX(), getY() + getBbHeight() * 0.5, getZ(), 18, 0.7, 0.3, 0.7, 0.06);
    }

    /**
     * Where it circles over a spot: {@code circleHeight} above whatever stands highest there (the ground, or a roof the
     * player has gone under, so it waits over the roof rather than coming down to them) while it flies under open sky;
     * under a roof itself (a cave, the Nether) as high as the room above allows, so it never presses against a ceiling.
     */
    private Vec3 anchorOver(ServerLevel level, BlockPos foot, double baseY, double x, double z) {
        boolean open = !level.dimensionType().hasCeiling() && level.canSeeSky(blockPosition().above());
        if (open) return new Vec3(x, Math.max(level.getHeight(Heightmap.Types.MOTION_BLOCKING, foot.getX(), foot.getZ()), baseY) + circleHeight, z);
        return new Vec3(x, baseY + headroom(level, foot, circleHeight), z);
    }

    /** How far up from a spot it can fly, at most {@code max}: two blocks short of the first solid block above. */
    private static int headroom(ServerLevel level, BlockPos from, int max) {
        for (int up = 2; up <= max; up++) {
            if (solid(level, from.above(up))) return Math.max(1, up - 2);
        }
        return max;
    }

    private static boolean solid(ServerLevel level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /** Ground within 4 blocks below, or rising 3 blocks ahead of it, or a wall it has flown into; not under a low roof. */
    private boolean terrainClose(ServerLevel level) {
        BlockPos at = blockPosition();
        if (solid(level, at.above(1)) || solid(level, at.above(2))) return false;
        if (horizontalCollision) return true;
        for (int down = 1; down <= 4; down++) if (solid(level, at.below(down))) return true;
        Vec3 flat = new Vec3(getDeltaMovement().x, 0.0, getDeltaMovement().z);
        if (flat.lengthSqr() < 1.0E-4) return false;
        BlockPos ahead = BlockPos.containing(position().add(flat.normalize().scale(3.0)));
        for (int down = 0; down <= 2; down++) if (solid(level, ahead.below(down))) return true;
        return false;
    }

    /** A Blunt arrow's knock: a dive being told is broken off, and it circles again for its full gap. */
    public void breakOffTell() {
        if (phase() == Phase.TELL) setPhase(Phase.CIRCLE);
    }

    /** The tell: wings folded, it all but stops in the air for a second, then sets off at where the target is. */
    private void tell(@Nullable LivingEntity target) {
        if (target == null) {
            setPhase(Phase.CIRCLE);
            return;
        }
        Vec3 aim = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
        setDeltaMovement(getDeltaMovement().scale(0.7));
        face(aim.subtract(position()));
        if (phaseTicks >= HarrierFlight.TELL_TICKS) startDive(aim);
    }

    /** Sets off down a straight line at this point. Public so tests can start a dive. */
    public void startDive(Vec3 at) {
        double[] dir = HarrierFlight.direction(getX(), getY(), getZ(), at.x, at.y, at.z);
        diveDir = new Vec3(dir[0], dir[1], dir[2]);
        setPhase(Phase.DIVE);
        setDeltaMovement(diveDir.scale(HarrierFlight.DIVE_SPEED));
        playSound(ModSounds.HARRIER_DIVE.get(), 3.0F, 1.0F);
    }

    /** Straight down the line until it strikes, goes past, meets the ground or runs out of dive. */
    private void dive(ServerLevel level, @Nullable LivingEntity target) {
        setDeltaMovement(diveDir.scale(HarrierFlight.DIVE_SPEED));
        if (target != null && getBoundingBox().inflate(0.3).intersects(target.getBoundingBox())) {
            doHurtTarget(level, target);
            endDive();
            return;
        }
        boolean passed = target != null && HarrierFlight.passed(getX(), getY(), getZ(), new double[] {diveDir.x, diveDir.y, diveDir.z},
                target.getX(), target.getY(0.5), target.getZ()) && distanceTo(target) > 1.5;
        if (passed || horizontalCollision || verticalCollision || onGround() || phaseTicks > HarrierFlight.DIVE_MAX_TICKS) endDive();
    }

    private void endDive() {
        setPhase(Phase.CLIMB);
        playSound(ModSounds.HARRIER_FLAP.get(), 1.2F, 1.0F);
    }

    /** Up and away, out of melee reach, back to its circle. */
    private void climb(ServerLevel level) {
        Vec3 ahead = new Vec3(getDeltaMovement().x, 0.0, getDeltaMovement().z);
        ahead = ahead.lengthSqr() < 1.0E-4 ? Vec3.ZERO : ahead.normalize().scale(0.5);
        steer(ahead.add(0.0, 1.0, 0.0).normalize().scale(HarrierFlight.CLIMB_SPEED), 0.3);
        if (phaseTicks % 8 == 0) playSound(ModSounds.HARRIER_FLAP.get(), 0.8F, 1.0F + random.nextFloat() * 0.1F);
        double ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, getBlockX(), getBlockZ());
        boolean high = anchor != null ? getY() >= anchor.y - 3.0 : getY() >= ground + HarrierFlight.CIRCLE_HEIGHT_MIN;
        // a roof stops the climb; the ground it takes off from doesn't
        if (high || (verticalCollision && !verticalCollisionBelow) || phaseTicks > 200) setPhase(Phase.CIRCLE);
    }

    /**
     * Down: it falls until it lands (or for {@link HarrierFlight#GROUNDED_FALL_MAX} ticks at most), then lies stunned for
     * {@link HarrierFlight#GROUNDED_TICKS} ticks before it takes off.
     */
    private void grounded(ServerLevel level) {
        setDeltaMovement(getDeltaMovement().multiply(0.6, 1.0, 0.6));
        if (!landed) {
            boolean down = onGround() || isInWater();
            if (!down && phaseTicks < HarrierFlight.GROUNDED_FALL_MAX) return;
            landed = true;
            phaseTicks = 0;
            if (down) {
                level.playSound(null, getX(), getY(), getZ(), ModSounds.HARRIER_LAND.get(), getSoundSource(), 1.5F, 1.0F);
                level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.1, getZ(), 10, 0.5, 0.05, 0.5, 0.03);
            }
            return;
        }
        // stunned: stars about its head
        if (phaseTicks % 6 == 0) level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 0.9, getZ(), 2, 0.25, 0.05, 0.25, 0.0);
        if (phaseTicks >= HarrierFlight.GROUNDED_TICKS) {
            setPhase(Phase.CLIMB);
            playSound(ModSounds.HARRIER_FLAP.get(), 1.2F, 0.9F);
        }
    }

    private void steer(Vec3 want, double turn) {
        Vec3 now = getDeltaMovement();
        setDeltaMovement(now.add(want.subtract(now).scale(turn)));
    }

    /** Turns its body and pitch to a direction (its yaw and pitch, as an arrow's). */
    private void face(Vec3 dir) {
        double flat = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        if (flat > 1.0E-4) setYRot((float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F);
        if (dir.lengthSqr() > 1.0E-6) setXRot((float) (-Mth.atan2(dir.y, Math.max(flat, 1.0E-4)) * Mth.RAD_TO_DEG));
        yBodyRot = getYRot();
        yHeadRot = getYRot();
    }

    @Override
    public void travel(Vec3 input) {
        if (phase() == Phase.GROUNDED) super.travel(input);
        else travelFlying(input, 0.02F);
    }

    /**
     * An arrow from a player's shot struck it: a Perfect Release or a headshot while it dives brings it down. Called by
     * the shot pipeline once the hit has dealt damage. Whether the shot was one that brings it down, even if it also
     * killed it.
     */
    public boolean struckByShot(boolean perfect, boolean precision) {
        if (!HarrierFlight.grounds(phase() == Phase.DIVE, perfect, precision)) return false;
        if (isAlive()) ground();
        return true;
    }

    /**
     * Whether a hit at this point of a diving harrier is on its head: the leading part of its box along the dive, which
     * in a steep dive is its underside as much as its front. Read on the server, where the dive's line is known.
     */
    public boolean diveHeadshot(Vec3 at) {
        Vec3 middle = getBoundingBox().getCenter();
        return HarrierFlight.diveHeadshot(at.x - middle.x, at.y - middle.y, at.z - middle.z,
                new double[] {diveDir.x, diveDir.y, diveDir.z}, getBbWidth() * 0.5, getBbHeight() * 0.5);
    }

    /** Brought down: it falls, wings splayed, for its stun on the ground. Public so tests can ground it directly. */
    public void ground() {
        setPhase(Phase.GROUNDED);
        landed = false;
        setDeltaMovement(getDeltaMovement().multiply(0.3, 0.0, 0.3).add(0.0, -0.4, 0.0));
        if (level() instanceof ServerLevel level) {
            level.playSound(null, getX(), getY(), getZ(), ModSounds.HARRIER_GROUNDED.get(), getSoundSource(), 2.0F, 1.0F);
            level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.4, getZ(), 12, 0.4, 0.2, 0.4, 0.02);
        }
    }

    /** Down on the ground, a weapon or a claw strikes twice as hard; arrows, blasts and everything else as usual. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean melee = source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO);
        if (phase() == Phase.GROUNDED && melee) amount *= HarrierFlight.GROUNDED_MELEE_MULTIPLIER;
        return super.hurtServer(level, source, amount);
    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
    }

    @Override
    public boolean onClimbable() {
        return false;
    }

    /** A boat or a cart would hold it for ever, grounded or not. */
    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return phase() == Phase.CIRCLE && (getTarget() == null || nextDive > QUIET_BEFORE_DIVE) ? ModSounds.HARRIER_AMBIENT.get() : null;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 160;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.HARRIER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.HARRIER_DEATH.get();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("Phase", phase().id());
        output.putInt("PhaseTicks", phaseTicks);
        output.putInt("NextDive", nextDive);
        output.putBoolean("Landed", landed);
        output.putInt("CircleHeight", circleHeight);
        output.storeNullable("Anchor", Vec3.CODEC, anchor);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        Phase saved = Phase.CIRCLE;
        String id = input.getStringOr("Phase", "circle");
        for (Phase p : Phase.values()) if (p.id().equals(id)) saved = p;
        // a dive or its tell doesn't survive a reload: it climbs back to its circle
        setPhase(saved == Phase.TELL || saved == Phase.DIVE ? Phase.CLIMB : saved);
        phaseTicks = input.getIntOr("PhaseTicks", 0);
        nextDive = input.getIntOr("NextDive", HarrierFlight.DIVE_GAP_MIN);
        landed = input.getBooleanOr("Landed", false);
        circleHeight = input.getIntOr("CircleHeight", HarrierFlight.CIRCLE_HEIGHT_MIN);
        anchor = input.read("Anchor", Vec3.CODEC).orElse(null);
    }
}
