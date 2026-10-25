package com.panda.fulldraw.registry;

import com.panda.fulldraw.FullDrawMod;
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

    private ModBlocks() {}
}
