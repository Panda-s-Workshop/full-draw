package com.panda.fulldraw.entity;

import com.panda.fulldraw.logic.LurkerRules;
import com.panda.fulldraw.registry.ModSounds;
import com.panda.fulldraw.shot.ShotHolder;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A mossy, frog-like ambusher of swamp water. It lies with only its two glowing eyes above the surface, watching, and
 * spits a slowing glob at a player 8 to 16 blocks off, its throat swelling and gurgling first. A player who comes within
 * 4 blocks sends it under: it sinks and swims away, and comes back up further off. Its eyes are its weak spot; a hit
 * on its body under the water does half. Stranded on land it makes for the nearest water.
 */
public class BogLurker extends Mob implements Enemy {
    public enum Phase {
        LURK, TELL, FLEE;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(BogLurker.class, EntityDataSerializers.BYTE);
    /** A player it takes notice of: in sight, not hidden by invisibility. */
    private static final TargetingConditions SPOT = TargetingConditions.forCombat().range(LurkerRules.SPIT_MAX + 8.0);
    /** A player it keeps watching, or keeps away from, when out of sight a moment. */
    private static final TargetingConditions KEEP = TargetingConditions.forCombat().range(LurkerRules.SPIT_MAX + 16.0).ignoreLineOfSight();
    /** Its first spit comes this soon after it notices a player in range. */
    private static final int FIRST_SPIT = 20;
    /** Directions it tries when it swims away, turned from straight away from the player (degrees). */
    private static final int[] ESCAPE_TURNS = {0, 30, -30, 60, -60, 90, -90, 120, -120};
    /** What water leaves of a mob's motion each tick, across and up and down alike (vanilla's). */
    private static final double WATER_DRAG = 0.8;
    /** How far up it looks for the top of the water it lies in. */
    private static final int SURFACE_SEARCH = 64;

    private int phaseTicks;
    private int nextSpit = FIRST_SPIT;
    private Vec3 swimDir = Vec3.ZERO;
    /** On a client: how far its throat is swollen for a spit, 0 to 1, eased; and its value a tick ago. */
    public float throat, throatO;

    public BogLurker(EntityType<? extends BogLurker> type, Level level) {
        super(type, level);
        this.xpReward = 6;
        // it turns itself to what it watches; vanilla's look control would turn only the head, which it doesn't have apart
        this.lookControl = new LookControl(this) {
            @Override
            public void tick() {
            }
        };
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 18.0)
                .add(Attributes.FOLLOW_RANGE, LurkerRules.SPIT_MAX + 16.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PHASE, (byte) Phase.LURK.ordinal());
    }

    public Phase phase() {
        return Phase.values()[entityData.get(PHASE)];
    }

    /** Ticks spent in the current phase (on the server). */
    public int phaseTicks() {
        return phaseTicks;
    }

    /** Ticks until its next spit may start (on the server). */
    public int nextSpit() {
        return nextSpit;
    }

    private void setPhase(Phase phase) {
        entityData.set(PHASE, (byte) phase.ordinal());
        phaseTicks = 0;
        swimDir = Vec3.ZERO;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            throatO = throat;
            throat = Mth.approach(throat, phase() == Phase.TELL ? 1.0F : 0.0F, 0.15F);
            // a spit about to come: bubbles rise round its mouth and the water churns round its eyes
            if (phase() == Phase.TELL && isInWater()) {
                Vec3 mouth = mouth();
                if (tickCount % 3 == 0) level().addParticle(ParticleTypes.BUBBLE_POP, mouth.x, mouth.y + 0.1, mouth.z, 0.0, 0.02, 0.0);
                double surface = surface(level());
                if (!Double.isNaN(surface) && tickCount % 2 == 0)
                    for (int i = 0; i < 3; i++) {
                        double a = random.nextDouble() * Mth.TWO_PI;
                        level().addParticle(ParticleTypes.SPLASH, getX() + Math.cos(a) * 0.55, surface + 0.05, getZ() + Math.sin(a) * 0.55, 0.0, 0.1, 0.0);
                    }
            }
        }
    }

    /** How far its throat is swollen for drawing, 0 to 1. */
    public float swollen(float partialTick) {
        return Mth.lerp(partialTick, throatO, throat);
    }

    /** Its body always faces where it looks. */
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
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        phaseTicks++;
        LivingEntity target = watchTarget(level);
        double surface = surface(level);
        boolean water = !Double.isNaN(surface) && isInWater();
        setNoGravity(water);
        if (water) getNavigation().stop();
        switch (phase()) {
            case LURK -> lurk(level, target, water ? surface : Double.NaN);
            case TELL -> tell(level, target, water ? surface : Double.NaN);
            case FLEE -> flee(level, target, water ? surface : Double.NaN);
        }
    }

    /** The player it watches: kept while still fair game, else the nearest one it can see. */
    private @Nullable LivingEntity watchTarget(ServerLevel level) {
        LivingEntity target = getTarget();
        if (target != null && !watchable(level, target, KEEP)) {
            setTarget(null);
            target = null;
        }
        if (target == null && (tickCount + getId()) % 10 == 0) {
            Player nearest = null;
            double best = Double.MAX_VALUE;
            for (Player player : level.players()) {
                if (!watchable(level, player, SPOT)) continue;
                double d = player.distanceToSqr(this);
                if (d < best) {
                    best = d;
                    nearest = player;
                }
            }
            if (nearest != null) {
                setTarget(nearest);
                nextSpit = Math.max(nextSpit, FIRST_SPIT);
            }
            target = nearest;
        }
        return target;
    }

    /** It watches players only; a target given it otherwise (a command, a test) is kept on the same terms. */
    private boolean watchable(ServerLevel level, LivingEntity target, TargetingConditions conditions) {
        if (target.level() != level || target instanceof Player player && (player.isCreative() || player.isSpectator())) return false;
        return conditions.test(level, this, target);
    }

    /** Eyes up at the surface, turned to its target: backing off from one too close to spit at, spitting at one in range. */
    private void lurk(ServerLevel level, @Nullable LivingEntity target, double surface) {
        Vec3 drift = Vec3.ZERO;
        if (target != null) {
            turnTo(target.position(), 12.0F);
            switch (LurkerRules.act(distanceTo(target))) {
                case FLEE -> {
                    startFlee(level);
                    return;
                }
                case BACK_OFF -> {
                    Vec3 away = escape(level, target, surface);
                    drift = away.scale(LurkerRules.DRIFT_SPEED);
                }
                case SPIT -> {
                    if (nextSpit > 0) nextSpit--;
                    if (nextSpit <= 0 && hasLineOfSight(target)) {
                        startTell();
                        nextSpit = LurkerRules.spitGap(random.nextDouble());
                    }
                }
                case WATCH -> {
                }
            }
            // a player who comes into range gets its first spit's notice again
            if (LurkerRules.act(distanceTo(target)) != LurkerRules.Act.SPIT) nextSpit = Math.max(nextSpit, FIRST_SPIT);
        }
        if (Double.isNaN(surface)) {
            onLand(level, target);
            return;
        }
        hold(drift, LurkerRules.lurkY(surface, getBbHeight()), 0.3);
    }

    /** The throat swells and gurgles, then the glob; held back if the target leaves its range or its sight first. */
    private void tell(ServerLevel level, @Nullable LivingEntity target, double surface) {
        if (target == null) {
            setPhase(Phase.LURK);
            return;
        }
        turnTo(target.position(), 20.0F);
        LurkerRules.Act act = LurkerRules.act(distanceTo(target));
        if (act == LurkerRules.Act.FLEE) {
            startFlee(level);
            return;
        }
        if (act != LurkerRules.Act.SPIT || !hasLineOfSight(target)) {
            setPhase(Phase.LURK);
            nextSpit = FIRST_SPIT;
            return;
        }
        if (!Double.isNaN(surface)) hold(Vec3.ZERO, LurkerRules.lurkY(surface, getBbHeight()), 0.3);
        if (phaseTicks >= LurkerRules.SPIT_TELL_TICKS) {
            spit(level, target);
            setPhase(Phase.LURK);
        }
    }

    /** Starts the tell before a spit: the throat swells and gurgles. Public so tests and pictures can start one. */
    public void startTell() {
        setPhase(Phase.TELL);
        playSound(ModSounds.LURKER_GURGLE.get(), 1.2F, 0.9F + random.nextFloat() * 0.2F);
    }

    /** Spits a glob at the target's chest, aimed high enough for its drop. Public so tests can make it spit. */
    public void spit(ServerLevel level, LivingEntity target) {
        Vec3 mouth = mouth();
        BogSpit glob = new BogSpit(level, this, mouth);
        double dx = target.getX() - mouth.x, dz = target.getZ() - mouth.z;
        double dy = target.getY(0.6) - mouth.y;
        double flat = Math.sqrt(dx * dx + dz * dz);
        glob.shoot(dx, dy + LurkerRules.spitLift(Math.sqrt(flat * flat + dy * dy)), dz, (float) LurkerRules.SPIT_SPEED, 3.0F);
        level.addFreshEntity(glob);
        playSound(ModSounds.LURKER_SPIT.get(), 1.5F, 0.9F + random.nextFloat() * 0.2F);
    }

    /** Where a glob leaves it: over the front of its head, clear of the water it lies in. */
    private Vec3 mouth() {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(getX() - Mth.sin(yaw) * 0.45, getY() + getBbHeight() + 0.15, getZ() + Mth.cos(yaw) * 0.45);
    }

    private void startFlee(ServerLevel level) {
        setPhase(Phase.FLEE);
        level.playSound(null, getX(), getY(), getZ(), ModSounds.LURKER_SINK.get(), getSoundSource(), 1.0F, 1.0F);
        if (isInWater()) level.sendParticles(ParticleTypes.BUBBLE, getX(), getY() + getBbHeight(), getZ(), 12, 0.4, 0.1, 0.4, 0.05);
    }

    /**
     * Under and away from the player, until it is far enough off or has swum long enough; then it comes back up. Cornered
     * (the player still within reach when its time is up) it stays under.
     */
    private void flee(ServerLevel level, @Nullable LivingEntity target, double surface) {
        double distance = target == null ? Double.MAX_VALUE : distanceTo(target);
        boolean tired = phaseTicks > LurkerRules.FLEE_MAX_TICKS && distance >= LurkerRules.FLEE_WITHIN;
        if (distance >= LurkerRules.FLEE_UNTIL || tired) {
            setPhase(Phase.LURK);
            return;
        }
        Vec3 away = escape(level, target, surface);
        if (away.lengthSqr() > 0.0) turnTo(position().add(away), 25.0F);
        if (Double.isNaN(surface)) {
            // on land it just runs
            if (getNavigation().isDone() || phaseTicks % 10 == 0) {
                Vec3 to = position().add(away.scale(6.0));
                getNavigation().moveTo(to.x, to.y, to.z, 1.4);
            }
            return;
        }
        hold(away.scale(LurkerRules.SWIM_SPEED), LurkerRules.sunkY(surface, getBbHeight()), 0.35);
        if (phaseTicks % 4 == 0) level.sendParticles(ParticleTypes.BUBBLE, getX(), getY() + getBbHeight(), getZ(), 2, 0.2, 0.1, 0.2, 0.02);
    }

    /**
     * The way away from the target it can swim (or walk): straight away if that stays in water, else the nearest turn
     * from it that does. Zero when every way is blocked.
     */
    private Vec3 escape(ServerLevel level, LivingEntity target, double surface) {
        Vec3 away = new Vec3(getX() - target.getX(), 0.0, getZ() - target.getZ());
        if (away.lengthSqr() < 1.0E-6) away = new Vec3(1.0, 0.0, 0.0);
        away = away.normalize();
        if (Double.isNaN(surface)) return away;
        // keep turning the same way it turned last time, so it doesn't dither between two banks
        for (int turn : ESCAPE_TURNS) {
            Vec3 dir = away.yRot(turn * Mth.DEG_TO_RAD);
            if (swimDir.lengthSqr() > 0.0 && dir.dot(swimDir) < -0.2) continue;
            if (swimmable(level, dir)) {
                swimDir = dir;
                return dir;
            }
        }
        return Vec3.ZERO;
    }

    /** Whether the water goes on 1.5 blocks that way at its middle's height, with nothing solid in it. */
    private boolean swimmable(ServerLevel level, Vec3 dir) {
        BlockPos ahead = BlockPos.containing(position().add(dir.scale(1.5)).add(0.0, getBbHeight() * 0.5, 0.0));
        return level.getFluidState(ahead).is(FluidTags.WATER) && level.getBlockState(ahead).getCollisionShape(level, ahead).isEmpty();
    }

    /**
     * Moves along {@code flat} while rising or sinking toward {@code y}, easing from its last motion by {@code turn} a
     * tick. The water's drag on what it set last tick is taken back out first, so it holds the speeds it is given.
     */
    private void hold(Vec3 flat, double y, double turn) {
        double vy = Mth.clamp((y - getY()) * 0.5, -LurkerRules.DEPTH_SPEED, LurkerRules.DEPTH_SPEED);
        Vec3 want = new Vec3(flat.x, vy, flat.z);
        Vec3 now = getDeltaMovement().scale(1.0 / WATER_DRAG);
        setDeltaMovement(now.add(want.subtract(now).scale(turn)));
    }

    /** Stranded: it makes for the nearest water it can find, and otherwise sits where it is. */
    private void onLand(ServerLevel level, @Nullable LivingEntity target) {
        if (!getNavigation().isDone() || phaseTicks % 20 != 0) return;
        BlockPos water = nearestWater(level, blockPosition(), 12);
        if (water != null) getNavigation().moveTo(water.getX() + 0.5, water.getY(), water.getZ() + 0.5, 1.2);
    }

    private static @Nullable BlockPos nearestWater(ServerLevel level, BlockPos from, int radius) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(from.offset(-radius, -3, -radius), from.offset(radius, 2, radius))) {
            if (!level.getFluidState(p).is(FluidTags.WATER)) continue;
            double d = p.distSqr(from);
            if (d < bestD) {
                bestD = d;
                best = p.immutable();
            }
        }
        return best;
    }

    /**
     * The height of the water surface over it, or NaN when it isn't in water: the top of the column of water blocks
     * from its feet up.
     */
    public double surface(LevelReader level) {
        BlockPos at = BlockPos.containing(getX(), getY() + 0.1, getZ());
        if (!level.getFluidState(at).is(FluidTags.WATER)) {
            at = at.below();
            if (!level.getFluidState(at).is(FluidTags.WATER)) return Double.NaN;
        }
        for (int up = 0; up < SURFACE_SEARCH; up++) {
            BlockPos above = at.above();
            if (!level.getFluidState(above).is(FluidTags.WATER)) break;
            at = above;
        }
        FluidState top = level.getFluidState(at);
        return at.getY() + top.getHeight(level, at);
    }

    /** Whether a point is under the water it lies in: a hit there goes through the water to its body. */
    public boolean belowSurface(Vec3 at) {
        double surface = surface(level());
        return !Double.isNaN(surface) && at.y < surface;
    }

    private void turnTo(Vec3 at, float maxStep) {
        double dx = at.x - getX(), dz = at.z - getZ();
        if (dx * dx + dz * dz < 1.0E-6) return;
        float want = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(Mth.approachDegrees(getYRot(), want, maxStep));
        yBodyRot = getYRot();
        yHeadRot = getYRot();
    }

    /**
     * Its body under the water takes half from any projectile but a player's shot (which the shot pipeline weighs at the
     * point it struck): whether it went through the water is read from where the projectile is when it strikes.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        Entity direct = source.getDirectEntity();
        boolean shot = direct instanceof ShotHolder holder && holder.fulldraw$shot() != null;
        if (direct instanceof Projectile && !shot && belowSurface(direct.position())) amount *= (float) LurkerRules.SUBMERGED_BODY_MULT;
        return super.hurtServer(level, source, amount);
    }

    /** It spawns in water. */
    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return level.isUnobstructed(this);
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    /** A boat would carry it off its water for ever. */
    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return phase() == Phase.LURK ? ModSounds.LURKER_AMBIENT.get() : null;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 200;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.LURKER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.LURKER_DEATH.get();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("Phase", phase().id());
        output.putInt("PhaseTicks", phaseTicks);
        output.putInt("NextSpit", nextSpit);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        Phase saved = Phase.LURK;
        String id = input.getStringOr("Phase", "lurk");
        for (Phase p : Phase.values()) if (p.id().equals(id)) saved = p;
        // a spit's tell doesn't survive a reload
        setPhase(saved == Phase.TELL ? Phase.LURK : saved);
        phaseTicks = input.getIntOr("PhaseTicks", 0);
        nextSpit = input.getIntOr("NextSpit", FIRST_SPIT);
    }
}
