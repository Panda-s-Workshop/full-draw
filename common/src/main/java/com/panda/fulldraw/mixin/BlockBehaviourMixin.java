package com.panda.fulldraw.mixin;

import com.panda.fulldraw.fletching.FletchingMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.class)
public abstract class BlockBehaviourMixin {
    /**
     * Vanilla's Fletching Table is a plain block that does nothing when used: it opens the Fletching menu, as a crafting
     * table opens its grid (with or without an item in hand; sneaking with one places it, as anywhere). A data pack that
     * removes every Fletching recipe gives the table back to vanilla.
     */
    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
    private void fulldraw$fletchingTable(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit,
                                         CallbackInfoReturnable<InteractionResult> cir) {
        if (!state.is(Blocks.FLETCHING_TABLE)) return;
        if (level instanceof ServerLevel server) {
            if (!FletchingMenu.hasRecipes(server)) return;
            player.openMenu(FletchingMenu.provider(level, pos));
        }
        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}
