package com.panda.fulldraw.entity;

import com.panda.fulldraw.FullDrawConfig;
import com.panda.fulldraw.logic.MarksmanRules;
import com.panda.fulldraw.logic.WeaponStats;
import com.panda.fulldraw.registry.ModEnchantments;
import com.panda.fulldraw.registry.ModItems;
import com.panda.fulldraw.registry.ModSounds;
import com.panda.fulldraw.shot.ShotHolder;
import com.panda.fulldraw.shot.ShotRecord;
import com.panda.fulldraw.shot.Shots;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A hooded skeletal archer who keeps the top of a Ranger's Watchtower. It keeps watch from a crenel's gap, in plain view.
 * It fights from the crenels: it steps up into one's gap, draws with a loud creak (the warning), and looses a Perfect
 * shot led ahead of a moving foe, then ducks behind the parapet and moves to another. Ducked behind the parapet only its
 * head shows over it, and a blow from beyond the parapet lands only there; one from the battlements behind it lands
 * anywhere. A foe who reaches it on the battlements gets the bow's end. Below half its health it draws faster, and a foe
 * who hides is lit up with a Flare Arrow and then shelled with Blast Arrows. Knocked off its tower, it is soon back at its
 * post. A boss bar shows its health to everyone near it.
 */
public class Marksman extends AbstractSkeleton {
    /** Crouched behind the parapet: low enough that only the head clears a wall a block high. */
    private static final EntityDimensions DUCKED = EntityDimensions.scalable(0.6F, 1.35F).withEyeHeight(1.15F);
    /** A projectile its cover turns away: it falls off the stone, with a knock and chips. */
    private static final ProjectileDeflection PARAPET = (projectile, entity, random, power) -> {
        if (entity instanceof Marksman marksman) marksman.turnAway(projectile);
    };
    /** The gold glint at its arrowhead while it draws. */
    private static final DustParticleOptions GLINT = new DustParticleOptions(0xFFC83C, 2.0F);
    /** How far from home it looks for crenels. */
    private static final int CRENEL_REACH = 8;
    /** How many of its last shots it remembers (for tests). */
    private static final int LOOSED_KEPT = 16;

    private final ServerBossEvent bossEvent = new ServerBossEvent(getUUID(), getDisplayName(), BossEvent.BossBarColor.WHITE,
            BossEvent.BossBarOverlay.NOTCHED_10);
    @Nullable
    private BlockPos home;
    /** The crenels about home: where it stands to shoot, and which way is out. */
    private final List<Crenel> crenels = new ArrayList<>();
    private boolean crenelsFound;
    /** Whether the second phase has been announced (saved, so a reload doesn't sound it again). */
    private boolean phaseTwoAnnounced;
    /** The projectile allowed through its cover because it struck the head. */
    @Nullable
    private Projectile throughCover;
    /** Ticks it has been off its tower. */
    private int stranded;
    /** Its last shots, in order, with the game time of each (for tests). */
    public final List<Loosed> loosed = new ArrayList<>();
    /** How many projectiles its cover has turned away, each with the parapet's knock and chips (for tests). */
    public int turnedAway;

    public record Loosed(MarksmanRules.Shot shot, long gameTime, int drawTicks) {}

    public record Crenel(BlockPos stand, Direction out) {}

    public Marksman(EntityType<? extends Marksman> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        bossEvent.setDarkenScreen(false);
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MarksmanRules.MAX_HEALTH)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, MarksmanRules.SIGHT)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                // an explosion its cover screens mustn't still push it out of the crenel's gap and off the tower
                .add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0)
                // the bow's end, for a foe on the battlements with it
                .add(Attributes.ATTACK_DAMAGE, MarksmanRules.BASH_DAMAGE)
                .add(Attributes.ATTACK_KNOCKBACK, 1.2);
    }

    @Override
    protected int getBaseExperienceReward(ServerLevel level) {
        return 50;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new DuelGoal());
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** It fights with its own goal, never vanilla's bow or sword goals. */
    @Override
    public void reassessWeaponGoal() {}

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        // the Recurve it drops, Deadeye II and named, so the prize is the bow it fights with (its shots keep a vanilla
        // bow's numbers: MarksmanRules.BOW)
        ItemStack bow = new ItemStack(ModItems.RECURVE_BOW.get());
        bow.enchant(registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ModEnchantments.DEADEYE), 2);
        bow.set(DataComponents.ITEM_NAME, Component.translatable("item.fulldraw.marksmans_recurve"));
        setItemSlot(EquipmentSlot.MAINHAND, bow);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                                        @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        // no pumpkins or random enchantments: it always carries its own bow
        setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        populateDefaultEquipmentSlots(random, difficulty);
        setHome(blockPosition());
        applyTuning();
        return result;
    }

    public void setHome(BlockPos pos) {
        home = pos.immutable();
        setHomeTo(home, CRENEL_REACH);
        crenels.clear();
        crenelsFound = false;
    }

    @Nullable
    public BlockPos home() {
        return home;
    }

    /** The crenels about home, found the first time they're asked for. */
    public List<Crenel> crenels() {
        if (!crenelsFound && home != null) {
            crenelsFound = true;
            crenels.addAll(findCrenels(level(), home));
        }
        return crenels;
    }

    /**
     * Gaps in a parapet at the height of home's floor: a solid block with two open ones over it and open sky beyond, with
     * a place to stand on its inner side, nearer home.
     */
    static List<Crenel> findCrenels(Level level, BlockPos home) {
        List<Crenel> found = new ArrayList<>();
        for (int dx = -CRENEL_REACH; dx <= CRENEL_REACH; dx++) {
            for (int dz = -CRENEL_REACH; dz <= CRENEL_REACH; dz++) {
                BlockPos wall = home.offset(dx, 0, dz);
                if (!solid(level, wall) || !open(level, wall.above()) || !open(level, wall.above(2))) continue;
                for (Direction out : Direction.Plane.HORIZONTAL) {
                    BlockPos stand = wall.relative(out.getOpposite());
                    if (stand.distSqr(home) >= wall.distSqr(home)) continue;
                    if (open(level, stand) && open(level, stand.above()) && solid(level, stand.below()) && open(level, wall.relative(out).above()))
                        found.add(new Crenel(stand, out));
                }
            }
        }
        return found;
    }

    /** Where it stands to shoot from a crenel: on the parapet, in the gap. */
    public static BlockPos shootingSpot(Crenel crenel) {
        return crenel.stand().relative(crenel.out()).above();
    }

    private static boolean solid(Level level, BlockPos pos) {
        return level.getBlockState(pos).isCollisionShapeFullBlock(level, pos);
    }

    private static boolean open(Level level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /** At a crenel, ducked, the parapet still there: its cover screens it. In the open it has none. */
    public boolean isCovered() {
        return coverAt() != null;
    }

    @Nullable
    private Crenel coverAt() {
        if (getPose() != Pose.CROUCHING) return null;
        for (Crenel crenel : crenels())
            if (crenel.stand().equals(blockPosition()) && solid(level(), crenel.stand().relative(crenel.out()))) return crenel;
        return null;
    }

    /** How far a vector points out through the crenel. */
    private static double outward(Crenel crenel, Vec3 v) {
        return v.x * crenel.out().getStepX() + v.z * crenel.out().getStepZ();
    }

    public boolean isDrawing() {
        return isUsingItem();
    }

    public boolean phaseTwo() {
        return MarksmanRules.phaseTwo(getHealth(), getMaxHealth());
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        return pose == Pose.CROUCHING ? DUCKED : super.getDefaultDimensions(pose);
    }

    // ---- its cover -----------------------------------------------------------------------------------------------

    /**
     * Ducked at a crenel, it turns away a projectile coming in over the parapet unless the projectile strikes its head,
     * which shows over it.
     */
    @Override
    public ProjectileDeflection deflection(Projectile projectile) {
        Crenel cover = coverAt();
        if (cover == null || !isAlive()) return ProjectileDeflection.NONE;
        Vec3 from = projectile.position(), motion = projectile.getDeltaMovement();
        if (!MarksmanRules.screens(true, false, -outward(cover, motion))) return ProjectileDeflection.NONE;
        // where it meets the box itself, if it does: the margin about it would read a shot onto its head from above as
        // passing over it
        Vec3 to = from.add(motion);
        Vec3 at = getBoundingBox().clip(from, to).or(() -> getBoundingBox().inflate(0.3).clip(from, to)).orElse(from);
        if (Shots.strikesWeakSpot(this, at, motion)) {
            throughCover = projectile;
            return ProjectileDeflection.NONE;
        }
        return PARAPET;
    }

    /** A projectile turned away by the stone: it drops where it struck. */
    private void turnAway(Projectile projectile) {
        Vec3 motion = projectile.getDeltaMovement();
        projectile.setDeltaMovement(-motion.x * 0.05, 0.1, -motion.z * 0.05);
        projectile.needsSync = true;
        if (!(level() instanceof ServerLevel level)) return;
        turnedAway++;
        level.playSound(null, projectile.getX(), projectile.getY(), projectile.getZ(), ModSounds.MARKSMAN_PARAPET.get(), getSoundSource(), 1.0F,
                0.9F + random.nextFloat() * 0.2F);
        Crenel cover = coverAt();
        BlockState stone = cover == null ? Blocks.STONE_BRICKS.defaultBlockState() : level.getBlockState(cover.stand().relative(cover.out()));
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, stone), projectile.getX(), projectile.getY(), projectile.getZ(), 6, 0.1, 0.1,
                0.1, 0.1);
    }

    /**
     * How far beyond the parapet a blow came from: for a projectile, how fast it came in across the parapet; for
     * anything else, how far past the parapet's inner face its source was; -1 from nowhere.
     */
    private static double beyond(Crenel cover, DamageSource source) {
        if (source.getDirectEntity() instanceof Projectile projectile) return -outward(cover, projectile.getDeltaMovement());
        Vec3 from = source.getSourcePosition();
        if (from == null) return -1.0;
        return outward(cover, from.subtract(Vec3.atBottomCenterOf(cover.stand()))) - 0.5;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean head = throughCover != null && source.getDirectEntity() == throughCover;
        throughCover = null;
        // whoever struck it takes its eye, from wherever they shot (the hurt-by goal never looks past its battlements)
        if (source.getEntity() instanceof LivingEntity attacker && attacker != this && attacker.isAlive() && attacker != getTarget()
                && level.getDifficulty() != Difficulty.PEACEFUL && !(attacker instanceof Player p && (p.isCreative() || p.isSpectator())))
            setTarget(attacker);
        Crenel cover = coverAt();
        if (cover != null && !head && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && MarksmanRules.screens(true, false, beyond(cover, source)))
            return false;
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && !phaseTwoAnnounced && phaseTwo() && isAlive()) {
            phaseTwoAnnounced = true;
            playSound(ModSounds.MARKSMAN_PHASE_TWO.get(), 2.0F, 1.0F);
        }
        return hurt;
    }

    // ---- the duel ------------------------------------------------------------------------------------------------

    /**
     * What it is doing in the duel: watching from a crenel, ducked and moving between crenels, stepping up into the
     * crenel's gap to shoot, or drawing.
     */
    enum Step { WATCH, COVER, RISE, DRAW }

    /**
     * The whole fight: with no foe it watches from one crenel's gap and then another's; with one, it ducks and moves to
     * the crenel that faces them best, then steps up into its gap, draws and looses, and ducks again. A foe beside it
     * gets the bow's end instead.
     */
    final class DuelGoal extends Goal {
        private Step step = Step.WATCH;
        private int ticks;
        @Nullable
        private Crenel at;
        @Nullable
        private Crenel last;
        @Nullable
        private LivingEntity foe;
        private Vec3 lastSeen = Vec3.ZERO;
        private int hidden;
        private MarksmanRules.Shot shot = MarksmanRules.Shot.NONE;
        @Nullable
        private BlockPos walking;
        @Nullable
        private Vec3 foeWas;
        /** Its foe's horizontal speed, blocks a tick, smoothed: what it leads its arrow by. */
        private Vec3 foeSpeed = Vec3.ZERO;
        private int bashCooldown;

        DuelGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return true;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            stand();
        }

        @Override
        public void tick() {
            ticks++;
            updateFoe();
            if (foe == null) {
                watch();
                return;
            }
            Vec3 now = foe.position();
            if (foeWas != null) foeSpeed = foeSpeed.scale(0.5).add(now.subtract(foeWas).multiply(0.5, 0.0, 0.5));
            foeWas = now;
            if (bashCooldown > 0) bashCooldown--;
            if (beside(foe)) {
                bash();
                return;
            }
            boolean seen = seesFoe();
            if (seen) {
                lastSeen = foe.getEyePosition();
                hidden = 0;
            } else hidden++;
            switch (step) {
                case WATCH -> duck();
                case COVER -> cover(seen);
                case RISE -> rise();
                case DRAW -> draw(seen);
            }
        }

        private void updateFoe() {
            LivingEntity target = getTarget();
            boolean peaceful = level().getDifficulty() == Difficulty.PEACEFUL;
            if (peaceful && target != null) setTarget(null);
            else if (target != null && target.isAlive() && target != foe) {
                // a new foe: it knows where they are as it takes them on, seen or not
                foe = target;
                lastSeen = foe.getEyePosition();
                hidden = 0;
                foeWas = null;
                foeSpeed = Vec3.ZERO;
            }
            if (foe != null && (peaceful || !foe.isAlive() || foe.isRemoved() || foe.level() != level()
                    || foe.distanceTo(Marksman.this) > MarksmanRules.SIGHT + 16 && !inReach(getEyePosition(), foe.getEyePosition()) || foe instanceof Player p && (p.isCreative() || p.isSpectator())
                    || hidden > 600)) {
                foe = null;
                hidden = 0;
                if (step != Step.WATCH) {
                    stand();
                    step = Step.WATCH;
                    ticks = 0;
                }
            }
        }

        /**
         * No foe: stand in a crenel's gap looking out and down (from there it sees the ground about its tower, as it
         * couldn't from behind the parapet), and every 6 s walk to another.
         */
        private void watch() {
            List<Crenel> all = crenels();
            if (all.isEmpty()) return;
            if (at == null || ticks > 120) {
                Crenel next = all.get(random.nextInt(all.size()));
                if (all.size() > 1 && next.equals(at)) next = all.get((all.indexOf(next) + 1) % all.size());
                at = next;
                ticks = 0;
            }
            BlockPos gap = shootingSpot(at);
            if (!blockPosition().equals(gap)) walkTo(gap, 0.8);
            else {
                getNavigation().stop();
                Vec3 out = Vec3.atCenterOf(gap.relative(at.out(), 12));
                getLookControl().setLookAt(out.x, getEyeY() - 8.0, out.z);
            }
        }

        /** Its foe on the battlements within the bow's reach. */
        private boolean beside(LivingEntity foe) {
            return Math.abs(foe.getY() - getY()) < 2.0 && Math.hypot(foe.getX() - getX(), foe.getZ() - getZ()) <= MarksmanRules.BASH_REACH;
        }

        /** The bow's end: a blow and a shove, once a second; it ducks again when the foe is off it. */
        private void bash() {
            getNavigation().stop();
            setPose(Pose.STANDING);
            stopUsingItem();
            setAggressive(true);
            getLookControl().setLookAt(foe, 30.0F, 30.0F);
            if (bashCooldown == 0 && level() instanceof ServerLevel level) {
                swingForAttack(InteractionHand.MAIN_HAND);
                doHurtTarget(level, foe);
                bashCooldown = MarksmanRules.BASH_COOLDOWN;
            }
            step = Step.WATCH;
        }

        private void duck() {
            setPose(Pose.CROUCHING);
            stopUsingItem();
            setAggressive(false);
            step = Step.COVER;
            ticks = 0;
            at = bestCrenel();
        }

        /** Ducked, it moves to the crenel that faces its foe best; when it has waited long enough there, it stands to shoot. */
        private void cover(boolean seen) {
            getLookControl().setLookAt(lastSeen.x, lastSeen.y, lastSeen.z);
            if (at == null || ticks % 20 == 0) at = bestCrenel();
            boolean there = at == null || blockPosition().equals(at.stand());
            if (!there && ticks > MarksmanRules.STUCK_TICKS) {
                // its way to that crenel is blocked: it shoots from where it is
                at = null;
                there = true;
            }
            if (!there) walkTo(at.stand(), 0.6);
            else getNavigation().stop();
            if (!there || ticks < MarksmanRules.COVER_TICKS) return;
            boolean glowing = foe.hasEffect(MobEffects.GLOWING);
            shot = MarksmanRules.shot(phaseTwo(), seen, glowing);
            if (shot == MarksmanRules.Shot.NONE || !seen && hidden < MarksmanRules.HIDDEN_TICKS) return;
            setPose(Pose.STANDING);
            if (at == null) beginDraw();
            else {
                step = Step.RISE;
                ticks = 0;
            }
        }

        /**
         * Up onto the parapet in the crenel's gap, where it can see down to the foot of its tower (from behind a parapet
         * one block high, nothing nearer than about 30 blocks below shows) and where it stands in plain view; then it draws.
         */
        private void rise() {
            setAggressive(true);
            BlockPos gap = shootingSpot(at);
            getNavigation().stop();
            Vec3 centre = Vec3.atBottomCenterOf(gap);
            getMoveControl().setWantedPosition(centre.x, centre.y, centre.z, 1.0);
            getLookControl().setLookAt(lastSeen.x, lastSeen.y, lastSeen.z);
            if (blockPosition().equals(gap) && onGround() || ticks > MarksmanRules.RISE_TICKS) beginDraw();
        }

        private void beginDraw() {
            setAggressive(true);
            if (getMainHandItem().getItem() instanceof net.minecraft.world.item.BowItem) startUsingItem(InteractionHand.MAIN_HAND);
            playSound(ModSounds.MARKSMAN_DRAW.get(), 3.0F, 0.9F + random.nextFloat() * 0.1F);
            step = Step.DRAW;
            ticks = 0;
        }

        private void draw(boolean seen) {
            // knocked out of its draw (a Blunt Arrow lets its bow down): that shot is lost, and it ducks to draw again
            if (ticks > 1 && !isUsingItem() && getMainHandItem().getItem() instanceof net.minecraft.world.item.BowItem) {
                duck();
                return;
            }
            getNavigation().stop();
            Vec3 aim = shot == MarksmanRules.Shot.ARROW && seen ? foe.getEyePosition() : lastSeen;
            getLookControl().setLookAt(aim.x, aim.y, aim.z, 30.0F, 30.0F);
            yBodyRot = yHeadRot;
            // a gold glint at the arrowhead while it draws, seen from the ground where the creak can't say which gap
            if (ticks % 3 == 0 && level() instanceof ServerLevel level) {
                Vec3 tip = getEyePosition().add(getLookAngle().scale(0.9)).subtract(0.0, 0.25, 0.0);
                level.sendParticles(GLINT, true, false, tip.x, tip.y, tip.z, 3, 0.04, 0.04, 0.04, 0.0);
            }
            if (ticks < FullDrawConfig.marksman().drawTicks(phaseTwo())) return;
            loose(shot, shot == MarksmanRules.Shot.BLAST ? foe.position() : shot == MarksmanRules.Shot.FLARE ? lastSeen.subtract(0, 1.5, 0)
                    : seen ? lead(foe.getEyePosition().subtract(0, 0.5, 0)) : lastSeen.subtract(0, 0.5, 0));
            last = at;
            duck();
        }

        /** Where a moving foe will be when the arrow gets there: the mark moved on by their speed for the arrow's flight. */
        private Vec3 lead(Vec3 mark) {
            Vec3 eye = getEyePosition();
            double flat = Math.hypot(mark.x - eye.x, mark.z - eye.z);
            double pitch = MarksmanRules.pitch(flat, mark.y - eye.y, MarksmanRules.ARROW_SPEED, MarksmanRules.ARROW_GRAVITY);
            int ticks = Double.isNaN(pitch) ? -1 : MarksmanRules.flightTicks(flat, pitch, MarksmanRules.ARROW_SPEED);
            return ticks < 0 ? mark : mark.add(foeSpeed.scale(ticks));
        }

        /**
         * Into the very cell it means to stand in: a path that ends in it (a plain move stops a block short), and the last
         * step straight in.
         */
        private void walkTo(BlockPos stand, double speed) {
            Vec3 centre = Vec3.atBottomCenterOf(stand);
            if (position().distanceToSqr(centre) < 1.5 * 1.5) {
                getNavigation().stop();
                getMoveControl().setWantedPosition(centre.x, centre.y, centre.z, speed);
                walking = null;
            } else if (!stand.equals(walking) || getNavigation().isDone()) {
                walking = stand;
                getNavigation().moveTo(getNavigation().createPath(stand, 0), speed);
            }
        }

        /** Its foe in sight from where it would stand to shoot: in the crenel's gap, or where it is with no crenel. */
        private boolean seesFoe() {
            Crenel crenel = at != null ? at : bestCrenel();
            Vec3 eye = (crenel == null ? position() : Vec3.atBottomCenterOf(shootingSpot(crenel))).add(0, 1.74, 0);
            if (foe.getEyePosition().distanceTo(eye) > MarksmanRules.SIGHT && !inReach(eye, foe.getEyePosition())) return false;
            return level().clip(new ClipContext(eye, foe.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, Marksman.this))
                    .getType() == HitResult.Type.MISS;
        }

        /** The crenel that looks most toward its foe, not the one it last shot from if another faces them nearly as well. */
        @Nullable
        private Crenel bestCrenel() {
            Crenel best = null;
            double bestScore = -2;
            for (Crenel crenel : crenels()) {
                Vec3 to = lastSeen.subtract(Vec3.atCenterOf(crenel.stand()));
                double score = (to.x * crenel.out().getStepX() + to.z * crenel.out().getStepZ()) / Math.max(1e-6, Math.hypot(to.x, to.z));
                if (crenel.equals(last)) score -= 0.3;
                if (score > bestScore) {
                    bestScore = score;
                    best = crenel;
                }
            }
            return best;
        }
    }

    private void stand() {
        setPose(Pose.STANDING);
        stopUsingItem();
        setAggressive(false);
    }

    /**
     * Looses at a point: a Perfect arrow from its bow, carrying the shot record a player's would (so a hit is scored as a
     * Perfect one, headshots included), or a Flare or Blast Arrow; each on the flatter arc that drops onto the point.
     */
    /** Its arrow reaches from one point to the other: a foe it has taken on stays seen, and kept, as far as it can shoot. */
    private static boolean inReach(Vec3 from, Vec3 to) {
        return !Double.isNaN(MarksmanRules.pitch(Math.hypot(to.x - from.x, to.z - from.z), to.y - from.y, MarksmanRules.ARROW_SPEED, MarksmanRules.ARROW_GRAVITY));
    }

    void loose(MarksmanRules.Shot kind, Vec3 point) {
        if (!(level() instanceof ServerLevel level) || kind == MarksmanRules.Shot.NONE) return;
        ItemStack bow = getMainHandItem();
        Vec3 eye = getEyePosition();
        double dx = point.x - eye.x, dz = point.z - eye.z, flat = Math.hypot(dx, dz);
        double pitch = MarksmanRules.pitch(flat, point.y - eye.y, MarksmanRules.ARROW_SPEED, MarksmanRules.ARROW_GRAVITY);
        if (Double.isNaN(pitch) || flat < 1e-3) return;
        AbstractArrow arrow = switch (kind) {
            case FLARE -> new FlareArrow(level, this, new ItemStack(ModItems.FLARE_ARROW.get()), bow);
            case BLAST -> new BlastArrow(level, this, new ItemStack(ModItems.BLAST_ARROW.get()), bow);
            default -> ProjectileUtil.getMobArrow(this, new ItemStack(Items.ARROW), 1.0F, bow);
        };
        if (kind == MarksmanRules.Shot.ARROW && arrow instanceof ShotHolder holder) {
            WeaponStats stats = MarksmanRules.BOW;
            holder.fulldraw$setShot(new ShotRecord(true, eye, stats.damageMult(), stats.gravityMult(), 0.5,
                    BuiltInRegistries.ITEM.getKey(bow.isEmpty() ? Items.BOW : bow.getItem())));
            // a plain arrow's damage, not a mob's (which grows with the difficulty: the difficulty already scales what a
            // player takes)
            arrow.setBaseDamage(FullDrawConfig.marksman().arrowBaseDamage());
            arrow.setCritArrow(true);
        }
        arrow.setPos(eye.x, eye.y - 0.1, eye.z);
        double cos = Math.cos(pitch);
        arrow.shoot(dx / flat * cos, Math.sin(pitch), dz / flat * cos, (float) MarksmanRules.ARROW_SPEED, 0.5F);
        level.addFreshEntity(arrow);
        if (loosed.size() >= LOOSED_KEPT) loosed.removeFirst();
        loosed.add(new Loosed(kind, level.getGameTime(), getTicksUsingItem()));
        playSound(ModSounds.TWANG.get(), 1.5F, 0.9F);
        if (kind == MarksmanRules.Shot.ARROW) Shots.perfectRelease(this);
    }

    // ---- its post -------------------------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        // placed without finalizeSpawn (a command, `place template`): where it first stands is home
        if (home == null) {
            setHome(blockPosition());
            applyTuning();
        }
        // it always carries its bow (an arrow loosed from an empty hand is refused outright)
        if (getMainHandItem().isEmpty()) populateDefaultEquipmentSlots(random, level.getCurrentDifficultyAt(blockPosition()));
        // the parapet can be broken: look again for its crenels now and then
        if (tickCount % 200 == 0) {
            crenels.clear();
            crenelsFound = false;
        }
        boolean off = home != null && (getY() < home.getY() - 2.5 || Math.hypot(getX() - home.getX() - 0.5, getZ() - home.getZ() - 0.5) > CRENEL_REACH + 2);
        stranded = off ? stranded + 1 : 0;
        if (stranded >= MarksmanRules.STRANDED_TICKS) backToPost(level);
        super.customServerAiStep(level);
    }

    /**
     * The server's numbers for a new one: its health and its blow. One already met keeps its own. A new one at full
     * health stays full; one a command summoned hurt keeps what it was given.
     */
    private void applyTuning() {
        MarksmanRules.Tuning tuning = FullDrawConfig.marksman();
        boolean full = getHealth() >= getMaxHealth();
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(tuning.health());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(tuning.bashDamage());
        if (full) setHealth(getMaxHealth());
    }

    /** Off its tower with no way up: it is back at its post, with a puff where it was and where it is. */
    private void backToPost(ServerLevel level) {
        level.sendParticles(ParticleTypes.POOF, getX(), getY() + 1.0, getZ(), 12, 0.3, 0.6, 0.3, 0.02);
        getNavigation().stop();
        teleportTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
        level.sendParticles(ParticleTypes.POOF, getX(), getY() + 1.0, getZ(), 12, 0.3, 0.6, 0.3, 0.02);
        stranded = 0;
    }

    // ---- its boss bar --------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel level) updateBossBar(level);
    }

    /** Its health, red in its second phase, shown to everyone near it who isn't a spectator. */
    private void updateBossBar(ServerLevel level) {
        bossEvent.setProgress(getHealth() / getMaxHealth());
        bossEvent.setColor(phaseTwo() ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.WHITE);
        if (tickCount % 10 != 0) return;
        for (ServerPlayer stale : new ArrayList<>(bossEvent.getPlayers()))
            if (stale.isRemoved() || !stale.isAlive() || stale.level() != level) bossEvent.removePlayer(stale);
        for (ServerPlayer player : level.players()) {
            boolean near = player.isAlive() && !player.isSpectator() && distanceToSqr(player) < MarksmanRules.BAR_RANGE * MarksmanRules.BAR_RANGE;
            if (near) bossEvent.addPlayer(player); else bossEvent.removePlayer(player);
        }
    }

    /** Who sees its boss bar (for tests). */
    public List<ServerPlayer> bossBarPlayers() {
        return List.copyOf(bossEvent.getPlayers());
    }

    public BossEvent.BossBarColor bossBarColor() {
        return bossEvent.getColor();
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        bossEvent.setName(getDisplayName());
    }

    /** A player who stops tracking it (gone through a portal, teleported away) loses the bar at once, as the wither's do. */
    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    /** Any removal, an unloaded chunk's too (which never calls {@link #remove}), takes the bar off every screen. */
    @Override
    public void onRemoval(RemovalReason reason) {
        super.onRemoval(reason);
        bossEvent.removeAllPlayers();
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        bossEvent.removeAllPlayers();
    }

    // ---- the rest ------------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (home != null) output.store("Home", BlockPos.CODEC, home);
        output.putBoolean("PhaseTwo", phaseTwoAnnounced);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        input.read("Home", BlockPos.CODEC).ifPresent(this::setHome);
        phaseTwoAnnounced = input.getBooleanOr("PhaseTwo", false);
        if (hasCustomName()) bossEvent.setName(getDisplayName());
        // a reload ends a draw or a duck
        setPose(Pose.STANDING);
    }

    @Override
    public boolean removeWhenFarAway(double distanceSqr) {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.MARKSMAN_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.MARKSMAN_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.MARKSMAN_DEATH.get();
    }

    @Override
    protected SoundEvent getStepSound() {
        return SoundEvents.SKELETON_STEP;
    }
}
