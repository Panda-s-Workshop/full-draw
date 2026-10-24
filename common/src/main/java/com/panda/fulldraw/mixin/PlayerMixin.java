package com.panda.fulldraw.mixin;

import com.panda.fulldraw.quiver.Quivers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bows and crossbows draw from the Quiver's selected arrows, after arrows held in a hand. */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "getProjectile", at = @At("HEAD"), cancellable = true)
    private void fulldraw$quiverAmmo(ItemStack weapon, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack fromQuiver = Quivers.projectileFor((Player) (Object) this, weapon);
        if (fromQuiver != null) cir.setReturnValue(fromQuiver);
    }
}
