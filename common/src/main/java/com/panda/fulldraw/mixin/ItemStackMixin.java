package com.panda.fulldraw.mixin;

import com.panda.fulldraw.shot.BowBash;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bow Bash: vanilla calls {@code hurtEnemy} on the server once a melee hit has landed. */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Inject(method = "hurtEnemy", at = @At("HEAD"))
    private void fulldraw$bowBash(LivingEntity target, LivingEntity attacker, CallbackInfoReturnable<Boolean> cir) {
        BowBash.onHit((ItemStack) (Object) this, target, attacker);
    }
}
