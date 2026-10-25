package com.panda.fulldraw.registry;

import com.panda.fulldraw.FullDrawMod;
import com.panda.fulldraw.block.FlareLightBlock;
import com.panda.fulldraw.block.RopeBlock;
import com.panda.fulldraw.platform.registry.DeferredBlock;
import com.panda.fulldraw.platform.registry.DeferredRegister;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FullDrawMod.MODID);

    /** What a Rope Arrow hangs: climbed like a ladder, walked through, off in a moment by hand. */
    public static final DeferredBlock<RopeBlock> ROPE = BLOCKS.registerBlock("rope", RopeBlock::new,
            p -> p.mapColor(MapColor.WOOL).noCollision().noOcclusion().strength(0.3F).sound(RopeBlock.SOUND).ignitedByLava()
                    .pushReaction(PushReaction.POPPED));

    /**
     * Where a Flare Arrow burns: invisible, light 15, gone after 30 s; replaced by whatever goes there, water included.
     * It breaks at a touch, so a piston pops it and an explosion passes through it; its empty shape keeps players from
     * targeting it.
     */
    public static final DeferredBlock<FlareLightBlock> FLARE_LIGHT = BLOCKS.registerBlock("flare_light", FlareLightBlock::new,
            p -> p.mapColor(MapColor.NONE).replaceable().noCollision().noOcclusion().noLootTable().instabreak()
                    .lightLevel(state -> 15).pushReaction(PushReaction.POPPED));

    private ModBlocks() {}
}
