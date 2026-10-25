package com.panda.fulldraw.entity;

import com.panda.fulldraw.logic.ArrowTraits;
import com.panda.fulldraw.registry.ModBlocks;
import com.panda.fulldraw.registry.ModEntities;
import com.panda.fulldraw.registry.ModItems;
import com.panda.fulldraw.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A weak hit, burning in flight. Where it lands it lights the spot (light 15 for 30 s, sparks and smoke), above any
 * grass, flowers or snow it fell into, where its shooter may build. Every mob within 8 blocks glows for 10 s, seen
 * through walls; a mob's flare sets the players around it glowing instead. Spent on landing: nothing to pick up.
 */
public class FlareArrow extends FullDrawArrow {
    /** Burnt out: a hit on a creature has already discarded the arrow by the time it burns, so this, not isRemoved. */
    private boolean spent;

    public FlareArrow(EntityType<? extends FlareArrow> type, Level level) {
        super(type, level);
    }

    public FlareArrow(Level level, LivingEntity owner, ItemStack pickup, @Nullable ItemStack weapon) {
        super(ModEntities.FLARE_ARROW.get(), owner, level, pickup, weapon);
    }

    public FlareArrow(Level level, double x, double y, double z, ItemStack pickup) {
        super(ModEntities.FLARE_ARROW.get(), x, y, z, level, pickup);
    }

    @Override
    public ArrowTraits traits() {
        return ArrowTraits.FLARE;
    }

    @Override
    public void tick() {
        super.tick();
        // a trail only in flight: one hanging still (a display, a summoned arrow) would bury itself in flame
        if (level().isClientSide() && !isInGround() && tickCount > 1 && getDeltaMovement().lengthSqr() > 0.01)
            level().addParticle(ParticleTypes.SMALL_FLAME, getX(), getY(), getZ(), 0, 0, 0);
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        if (level() instanceof ServerLevel level) {
            BlockPos at = blockPosition();
            burn(level, aboveCover(level, level.getBlockState(at).isAir() ? at : hit.getBlockPos().relative(hit.getDirection())));
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        // burnt out whatever the hit did: a hit turned away slowly would otherwise also drop the arrow to pick up
        pickup = Pickup.DISALLOWED;
        super.onHitEntity(hit);
        if (level() instanceof ServerLevel level) burn(level, aboveCover(level, blockPosition()));
    }

    /**
     * An arrow passes through grass, flowers and thin snow and stops on the block beneath, inside them: the flare burns
     * on top of them instead (a tall plant's two blocks at most), not in water.
     */
    private static BlockPos aboveCover(ServerLevel level, BlockPos at) {
        for (int up = 0; up < 2 && covers(level.getBlockState(at)); up++) at = at.above();
        return at;
    }

    private static boolean covers(BlockState state) {
        return !state.isAir() && !state.is(ModBlocks.FLARE_LIGHT.get()) && state.canBeReplaced() && state.getFluidState().isEmpty();
    }

    /**
     * Lights the spot (where the shooter may change blocks), sets the creatures around glowing, and burns out. A player's
     * flare, or a dispenser's, shows the mobs; a mob's shows the players.
     */
    private void burn(ServerLevel level, BlockPos at) {
        if (spent) return;
        spent = true;
        boolean mayBuild = !(getOwner() instanceof Player player) || player.mayBuild();
        if (mayBuild && mayInteract(level, at) && ModBlocks.FLARE_LIGHT.get().light(level, at))
            level.gameEvent(GameEvent.BLOCK_PLACE, at, GameEvent.Context.of(this, level.getBlockState(at)));
        Vec3 centre = Vec3.atCenterOf(at);
        double r = ArrowTraits.FLARE_GLOW_RADIUS;
        boolean mobsFlare = getOwner() instanceof Mob;
        for (LivingEntity seen : level.getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(r),
                e -> e.distanceToSqr(centre) <= r * r && (mobsFlare ? e instanceof Player p && !p.isSpectator() : e instanceof Mob)))
            seen.addEffect(new MobEffectInstance(MobEffects.GLOWING, ArrowTraits.FLARE_GLOW_TICKS), this);
        level.playSound(null, at, ModSounds.FLARE.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.FLAME, centre.x, centre.y, centre.z, 12, 0.15, 0.15, 0.15, 0.05);
        discard();
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(ModItems.FLARE_ARROW.get());
    }
}
