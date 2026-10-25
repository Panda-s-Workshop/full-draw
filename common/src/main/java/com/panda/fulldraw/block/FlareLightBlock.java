package com.panda.fulldraw.block;

import com.panda.fulldraw.logic.ArrowTraits;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Where a Flare Arrow burns: an invisible light of 15 that sputters sparks and goes out on its own after
 * {@link ArrowTraits#FLARE_LIGHT_TICKS}. Nothing to see, hit or collect; anything placed there, or water, takes its place.
 * It counts down in {@link #STEPS} steps, so another flare landing there tops it up to the full time again.
 */
public class FlareLightBlock extends Block {
    public static final int STEPS = 10;
    public static final IntegerProperty BURN = IntegerProperty.create("burn", 1, STEPS);
    private static final int STEP_TICKS = ArrowTraits.FLARE_LIGHT_TICKS / STEPS;

    public FlareLightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BURN, STEPS));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BURN);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!old.is(this)) level.scheduleTick(pos, this, STEP_TICKS);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int burn = state.getValue(BURN);
        if (burn <= 1) {
            level.removeBlock(pos, false);
            return;
        }
        level.setBlock(pos, state.setValue(BURN, burn - 1), Block.UPDATE_CLIENTS);
        level.scheduleTick(pos, this, STEP_TICKS);
    }

    /** A burning flare: a flame and sparks most ticks, now and then a wisp of smoke and a crackle. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5, y = pos.getY() + 0.3, z = pos.getZ() + 0.5;
        level.addParticle(ParticleTypes.SMALL_FLAME, x + random.nextGaussian() * 0.05, y, z + random.nextGaussian() * 0.05, 0, 0.01, 0);
        if (random.nextInt(2) == 0)
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y + 0.1, z, random.nextGaussian() * 0.08, 0.05 + random.nextDouble() * 0.05, random.nextGaussian() * 0.08);
        if (random.nextInt(6) == 0) level.addParticle(ParticleTypes.SMOKE, x, y + 0.3, z, 0, 0.03, 0);
        if (random.nextInt(12) == 0) level.playLocalSound(pos, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.5F, 1.4F, false);
    }

    /** Lights {@code pos} if it's open air, or tops up a flare already burning there; true when it did. */
    public boolean light(Level level, BlockPos pos) {
        BlockState there = level.getBlockState(pos);
        if (!there.isAir() && !there.is(this)) return false;
        return level.setBlock(pos, defaultBlockState(), Block.UPDATE_ALL);
    }
}
