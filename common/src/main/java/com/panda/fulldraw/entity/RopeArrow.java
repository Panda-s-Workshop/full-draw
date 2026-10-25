package com.panda.fulldraw.entity;

import com.panda.fulldraw.logic.ArrowTraits;
import com.panda.fulldraw.registry.ModBlocks;
import com.panda.fulldraw.registry.ModEntities;
import com.panda.fulldraw.registry.ModItems;
import com.panda.fulldraw.registry.ModSounds;
import com.panda.fulldraw.registry.ModTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A weak hit on a creature. Struck into the side or the underside of a block, it ties a rope there that falls to the
 * ground, and the arrow is spent: the rope is what comes back. Struck into the top of a block, or where nothing would
 * hold a rope, it stays an arrow to pick up. A Multishot copy (never paid for, picked up by no one) flies as a plain
 * arrow, and a rope goes up only where its shooter may build: not in spawn protection, past the world border or for a
 * player in Adventure, and from a dispenser only while mobGriefing is on, as vanilla's arrows treat blocks.
 */
public class RopeArrow extends FullDrawArrow {
    public RopeArrow(EntityType<? extends RopeArrow> type, Level level) {
        super(type, level);
    }

    public RopeArrow(Level level, LivingEntity owner, ItemStack pickup, @Nullable ItemStack weapon) {
        super(ModEntities.ROPE_ARROW.get(), owner, level, pickup, weapon);
    }

    public RopeArrow(Level level, double x, double y, double z, ItemStack pickup) {
        super(ModEntities.ROPE_ARROW.get(), x, y, z, level, pickup);
    }

    @Override
    public ArrowTraits traits() {
        return ArrowTraits.ROPE;
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        if (!(level() instanceof ServerLevel level) || !paidFor()) return;
        if (hit.getDirection() == Direction.UP) {
            slipped(level, hit);
            return;
        }
        BlockPos knot = hit.getBlockPos().relative(hit.getDirection());
        if (!mayInteract(level, knot) || getOwner() instanceof Player player && !player.mayBuild()) {
            slipped(level, hit);
            return;
        }
        // under a ceiling it hangs from it; on a wall's side it hangs against that wall
        Direction tied = hit.getDirection() == Direction.DOWN ? Direction.UP : hit.getDirection().getOpposite();
        int hung = ModBlocks.ROPE.get().hang(level, knot, tied);
        if (hung == 0) {
            slipped(level, hit);
            return;
        }
        level.playSound(null, knot, ModSounds.ROPE_PLACE.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(GameEvent.BLOCK_PLACE, knot, GameEvent.Context.of(this, level.getBlockState(knot)));
        if (getOwner() instanceof ServerPlayer player) ModTriggers.EVENT.get().trigger(player, "rope_trick", hung);
        discard();
    }

    /**
     * A Rope Arrow whose rope can't hold (a floor, a fence, a taken spot, where the shooter may not build): a dull knock
     * and a small puff where it struck, so it reads as a failed rope and not a plain arrow. It stays as an arrow.
     */
    private void slipped(ServerLevel level, BlockHitResult hit) {
        // a tenth of a block out from the face, so the puff isn't lit (black) from inside the block
        var at = hit.getLocation().add(hit.getDirection().getUnitVec3().scale(0.1));
        level.playSound(null, at.x, at.y, at.z, ModSounds.ROPE_SLIP.get(), SoundSource.BLOCKS, 1.0F, 0.9F + random.nextFloat() * 0.2F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, at.x, at.y, at.z, 3, 0.05, 0.05, 0.05, 0.01);
    }

    /** An arrow its shooter spent (one that can be picked up), or any arrow from a player who builds for free. */
    private boolean paidFor() {
        return pickup == Pickup.ALLOWED || getOwner() instanceof Player player && player.hasInfiniteMaterials();
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(ModItems.ROPE_ARROW.get());
    }
}
