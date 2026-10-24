package com.panda.fulldraw.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.panda.fulldraw.client.FocusClient;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The archer's FOV: bow zoom for every bow, Steady Aim's narrowing and the release kick. */
@Mixin(AbstractClientPlayer.class)
abstract class AbstractClientPlayerMixin {
    @ModifyReturnValue(method = "getFieldOfViewModifier", at = @At("RETURN"))
    private float fulldraw$fov(float vanilla, boolean firstPerson, float effectScale) {
        return (Object) this instanceof LocalPlayer p ? FocusClient.fovModifier(p, vanilla, effectScale) : vanilla;
    }
}
