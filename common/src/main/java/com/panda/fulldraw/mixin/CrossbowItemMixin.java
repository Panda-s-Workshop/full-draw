package com.panda.fulldraw.mixin;

import com.panda.fulldraw.item.FullDrawCrossbowItem;
import com.panda.fulldraw.shot.Weapons;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A Full Draw crossbow loads in its own time. Vanilla reads the load time from this one static method (the use
 * duration, the power curve, the loading sounds and the item model's pull), so changing it here changes all of them;
 * Quick Charge still takes its quarter seconds off.
 */
@Mixin(CrossbowItem.class)
public abstract class CrossbowItemMixin {
    @Inject(method = "getChargeDuration", at = @At("HEAD"), cancellable = true)
    private static void fulldraw$chargeDuration(ItemStack stack, LivingEntity entity, CallbackInfoReturnable<Integer> cir) {
        if (stack.getItem() instanceof FullDrawCrossbowItem crossbow && Weapons.inPipeline(stack))
            cir.setReturnValue(Mth.floor(EnchantmentHelper.modifyCrossbowChargingTime(stack, entity, crossbow.stats().drawTicks() / 20.0F) * 20.0F));
    }
}
