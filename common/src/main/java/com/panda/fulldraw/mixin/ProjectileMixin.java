package com.panda.fulldraw.mixin;

import com.panda.fulldraw.shot.ShotHolder;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A deflected arrow is no longer the shot that was fired: a Breeze, a world border or a punch sends
 * it somewhere the archer didn't aim, so it drops its record and lands as a plain vanilla arrow.
 */
@Mixin(Projectile.class)
public abstract class ProjectileMixin {
    @Inject(method = "onDeflection", at = @At("HEAD"))
    private void fulldraw$dropShot(boolean byAttack, CallbackInfo ci) {
        if (this instanceof ShotHolder holder) holder.fulldraw$setShot(null);
    }
}
