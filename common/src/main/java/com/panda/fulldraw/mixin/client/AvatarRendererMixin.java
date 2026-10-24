package com.panda.fulldraw.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.panda.fulldraw.item.FullDrawCrossbowItem;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A player holding a loaded Full Draw crossbow raises it as a loaded vanilla crossbow, which vanilla checks by item. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @WrapOperation(method = "getArmPose(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z"))
    private static boolean fulldraw$crossbowHold(ItemStack stack, Object item, Operation<Boolean> original) {
        return original.call(stack, item) || item == Items.CROSSBOW && stack.getItem() instanceof FullDrawCrossbowItem;
    }
}
