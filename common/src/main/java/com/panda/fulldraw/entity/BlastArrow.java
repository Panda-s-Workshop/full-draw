package com.panda.fulldraw.entity;

import com.panda.fulldraw.FullDrawConfig;
import com.panda.fulldraw.logic.ArrowTraits;
import com.panda.fulldraw.registry.ModEntities;
import com.panda.fulldraw.registry.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A weak hit, then an explosion where it strikes (power 1.5, half a creeper's). The explosion hurts creatures half what a
 * vanilla one would ({@link ArrowTraits#blastDamage}) and throws back whatever is near, the shooter included; the
 * creature it struck takes the arrow's hit and then the explosion in full, so a hit, and a head hit most, beats a shot at
 * the feet. By default it breaks no blocks. With the server's {@code blastArrowBreaksBlocks} it breaks them as TNT does,
 * only where the {@code tnt_explodes} game rule allows, and a mob's only under {@code mobGriefing}. A Multishot copy
 * flies as a plain arrow, and a Repeating Crossbow loads a Blast Arrow alone. Spent on impact.
 */
public class BlastArrow extends FullDrawArrow {
    /** Gone off: a hit on a creature has already discarded the arrow by the time it blows, so this, not isRemoved. */
    private boolean spent;

    public BlastArrow(EntityType<? extends BlastArrow> type, Level level) {
        super(type, level);
    }

    public BlastArrow(Level level, LivingEntity owner, ItemStack pickup, @Nullable ItemStack weapon) {
        super(ModEntities.BLAST_ARROW.get(), owner, level, pickup, weapon);
    }

    public BlastArrow(Level level, double x, double y, double z, ItemStack pickup) {
        super(ModEntities.BLAST_ARROW.get(), x, y, z, level, pickup);
    }

    @Override
    public ArrowTraits traits() {
        return ArrowTraits.BLAST;
    }

    @Override
    public void tick() {
        super.tick();
        // a fuse's smoke only in flight, as the flare's trail
        if (level().isClientSide() && !isInGround() && tickCount > 1 && getDeltaMovement().lengthSqr() > 0.01)
            level().addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0, 0, 0);
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        if (level() instanceof ServerLevel level && !isCopy()) blast(level, null);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        if (isCopy()) {
            super.onHitEntity(hit);
            return;
        }
        // spent whatever the hit did: a hit turned away slowly would otherwise also drop the arrow to pick up
        pickup = Pickup.DISALLOWED;
        super.onHitEntity(hit);
        // the arrow's hit started the creature's damage cooldown, which would swallow the explosion: it lands on top
        if (hit.getEntity() instanceof LivingEntity struck) struck.damageCooldownTime = 0;
        if (level() instanceof ServerLevel level) blast(level, hit.getEntity());
    }

    /** A Multishot copy (Creative-only pickup from a shooter who pays for arrows): it flies as a plain arrow. */
    private boolean isCopy() {
        return pickup == Pickup.CREATIVE_ONLY && !(getOwner() instanceof Player player && player.hasInfiniteMaterials());
    }

    private void blast(ServerLevel level, @Nullable Entity struck) {
        if (spent) return;
        spent = true;
        discard();
        level.explode(this, null, new BlastDamage(struck), getX(), getY(), getZ(), ArrowTraits.BLAST_POWER, false, interaction(level));
    }

    /** Blocks stay unless the server lets Blast Arrows break them, and then only where TNT, or a mob's griefing, may. */
    private Level.ExplosionInteraction interaction(ServerLevel level) {
        if (!FullDrawConfig.blastArrowBreaksBlocks()) return Level.ExplosionInteraction.NONE;
        if (getOwner() instanceof Mob) return Level.ExplosionInteraction.MOB;
        return level.getGameRules().get(GameRules.TNT_EXPLODES) ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE;
    }

    /** The explosion's damage to creatures: {@link ArrowTraits#blastDamage}, in full to the one the arrow struck. */
    private static final class BlastDamage extends ExplosionDamageCalculator {
        @Nullable
        private final Entity struck;

        private BlastDamage(@Nullable Entity struck) {
            this.struck = struck;
        }

        @Override
        public float getEntityDamageAmount(Explosion explosion, Entity entity, float seen) {
            if (entity == struck) return (float) ArrowTraits.blastDamage(0.0, 1.0);
            return (float) ArrowTraits.blastDamage(Math.sqrt(entity.distanceToSqr(explosion.center())), seen);
        }
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(ModItems.BLAST_ARROW.get());
    }
}
