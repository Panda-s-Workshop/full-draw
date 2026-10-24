package com.panda.fulldraw.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.panda.fulldraw.item.FullDrawCrossbowItem;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** In first person, a loaded Full Draw crossbow takes the hands a loaded vanilla crossbow does, which vanilla picks by item. */
@Mixin(FirstPersonHandsAndItems.class)
public abstract class FirstPersonHandsAndItemsMixin {
    @WrapOperation(method = {"evaluateWhichHandsToRender", "isChargedCrossbow"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z"))
    private static boolean fulldraw$crossbowHands(ItemStack stack, Object item, Operation<Boolean> original) {
        return original.call(stack, item) || item == Items.CROSSBOW && stack.getItem() instanceof FullDrawCrossbowItem;
    }
}
