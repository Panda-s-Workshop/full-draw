package com.panda.fulldraw.block;

import com.panda.fulldraw.FullDrawConfig;
import com.panda.fulldraw.logic.ArrowTraits;
import com.panda.fulldraw.registry.ModSounds;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A hanging rope: climbable and walked through. Its knot is tied under a block (and hangs in the middle) or to a wall
 * beside it (and the whole rope hangs against that wall); every rope below hangs from the one above. Breaking one takes
 * every rope hanging from it too (down to another knot, which hangs on its own), all into the one drop; a rope in hand
 * used on a rope adds one at the bottom. It burns as wool does.
 */
public class RopeBlock extends Block {
    /** The top of a rope, tied off, rather than hanging from more rope. */
    public static final BooleanProperty KNOT = BooleanProperty.create("knot");
    /** What the rope is tied to: {@code up} under a block, or the side of the wall it hangs against. */
    public static final EnumProperty<Direction> TIED = EnumProperty.create("tied", Direction.class,
            Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST);

    private static final VoxelShape HANGING = Block.column(4.0, 0.0, 16.0);
    private static final Map<Direction, VoxelShape> ON_WALL = Shapes.rotateHorizontal(Block.box(6.0, 0.0, 1.0, 10.0, 16.0, 5.0));

    /** Wool's sounds, with a lead's knot when a rope is tied. */
    public static final SoundType SOUND = new SoundType(1.0F, 1.0F, SoundEvents.WOOL_BREAK, SoundEvents.WOOL_STEP, SoundEvents.WOOL_PLACE,
            SoundEvents.WOOL_HIT, SoundEvents.WOOL_FALL) {
        @Override
        public SoundEvent getPlaceSound() {
            return ModSounds.ROPE_PLACE.get();
        }
    };

    public RopeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(KNOT, true).setValue(TIED, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(KNOT, TIED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction tied = state.getValue(TIED);
        return tied == Direction.UP ? HANGING : ON_WALL.get(tied);
    }

    /**
     * Below a rope it hangs from it; otherwise, as a ladder, it ties to the first thing that holds it in the order the
     * player looks: the wall they face, or the block above.
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        LevelReader level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState above = level.getBlockState(pos.above());
        if (above.is(this)) return defaultBlockState().setValue(KNOT, false).setValue(TIED, above.getValue(TIED));
        for (Direction look : context.getNearestLookingDirections()) {
            if (look == Direction.DOWN) continue;
            BlockState state = defaultBlockState().setValue(TIED, look);
            if (state.canSurvive(level, pos)) return state;
        }
        return null;
    }

    /** A knot needs what it's tied to: a block above that holds a hanging lantern, or a solid wall; the rest, rope above. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (!state.getValue(KNOT)) return level.getBlockState(pos.above()).is(this);
        return holds(level, pos, state.getValue(TIED));
    }

    private static boolean holds(LevelReader level, BlockPos pos, Direction tied) {
        if (tied == Direction.UP) return Block.canSupportCenter(level, pos.above(), Direction.DOWN);
        BlockPos wall = pos.relative(tied);
        return level.getBlockState(wall).isFaceSturdy(level, wall, tied.getOpposite());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
        return state.canSurvive(level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            int below = 0;
            for (BlockState next; (next = level.getBlockState(pos.below(below + 1))).is(this) && !next.getValue(KNOT); ) below++;
            // from the bottom up, so each still hangs from the one above while it goes and none drops on its own
            for (int i = below; i >= 1; i--) level.setBlock(pos.below(i), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            if (below > 0 && !player.preventsBlockDrops()) popResource(level, pos, new ItemStack(this, below));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (!stack.is(asItem())) return super.useItemOn(stack, state, level, pos, player, hand, hit);
        BlockPos end = pos;
        while (level.getBlockState(end.below()).is(this)) end = end.below();
        BlockPos next = end.below();
        if (level.isOutsideBuildHeight(next) || !level.getBlockState(next).isAir() || !player.mayBuild() || !level.mayInteract(player, next))
            return InteractionResult.FAIL;
        if (!level.isClientSide()) {
            BlockState rope = level.getBlockState(end).setValue(KNOT, false);
            level.setBlock(next, rope, Block.UPDATE_ALL);
            level.gameEvent(GameEvent.BLOCK_PLACE, next, GameEvent.Context.of(player, rope));
            level.playSound(null, next, ModSounds.ROPE_PLACE.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            stack.consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Ties a rope at {@code top} to what's on its {@code tied} side ({@code up} for the block above) and lets it fall
     * through open air, down to the server's {@code ropeArrowLength} (24 unless set). Returns how many ropes hang, 0 when {@code top}
     * isn't open or nothing there would hold a rope.
     */
    public int hang(Level level, BlockPos top, Direction tied) {
        if (!level.getBlockState(top).isAir() || !holds(level, top, tied)) return 0;
        BlockState rope = defaultBlockState().setValue(TIED, tied);
        int length = ArrowTraits.ropeLength(i -> !level.isOutsideBuildHeight(top.below(i)) && level.getBlockState(top.below(i)).isAir(),
                FullDrawConfig.ropeArrowLength());
        for (int i = 0; i < length; i++) level.setBlock(top.below(i), rope.setValue(KNOT, i == 0), Block.UPDATE_ALL);
        return length;
    }
}
