package com.panda.fulldraw.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.panda.fulldraw.client.DrawJuice;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The bow shakes in the hand under tremble, on top of vanilla's own full-draw shake. The offset is applied at
 * the head of the arm's submission and taken back at its tail, so nothing leaks to the other hand.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class FirstPersonHandsMixin {
    @Unique
    private float[] fulldraw$shake;

    @Inject(method = "submitArmWithItem", at = @At("HEAD"))
    private void fulldraw$shakeOn(PlayerRenderState player, FirstPersonHandsAndItemsRenderState state, float partialTick, float pitch,
                                  InteractionHand hand, float attack, ItemStack item, float height, PoseStack pose,
                                  SubmitNodeCollector collector, int light, CallbackInfo ci) {
        fulldraw$shake = null;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !p.isUsingItem() || p.getUsedItemHand() != hand) return;
        float[] s = DrawJuice.shake(p, partialTick);
        if (s[0] == 0 && s[1] == 0) return;
        fulldraw$shake = s;
        pose.translate(s[0], s[1], 0);
        DrawJuice.shakenFrames++;
    }

    @Inject(method = "submitArmWithItem", at = @At("TAIL"))
    private void fulldraw$shakeOff(PlayerRenderState player, FirstPersonHandsAndItemsRenderState state, float partialTick, float pitch,
                                   InteractionHand hand, float attack, ItemStack item, float height, PoseStack pose,
                                   SubmitNodeCollector collector, int light, CallbackInfo ci) {
        if (fulldraw$shake != null) pose.translate(-fulldraw$shake[0], -fulldraw$shake[1], 0);
        fulldraw$shake = null;
    }
}
