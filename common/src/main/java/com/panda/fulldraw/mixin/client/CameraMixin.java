package com.panda.fulldraw.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.panda.fulldraw.client.FocusClient;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The tremble's sway and the release kick, once per frame. */
@Mixin(Camera.class)
abstract class CameraMixin {
    /** Before the camera takes the player's rotation. */
    @Inject(method = "alignWithEntity", at = @At("HEAD"))
    private void fulldraw$sway(float partialTick, CallbackInfo ci) {
        FocusClient.applySway(partialTick);
    }

    /** After the camera's own easing, so the kick is a sharp punch rather than a slower zoom-out. */
    @ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
    private float fulldraw$kick(float fov, float partialTick) {
        return fov * FocusClient.kick(partialTick);
    }
}
