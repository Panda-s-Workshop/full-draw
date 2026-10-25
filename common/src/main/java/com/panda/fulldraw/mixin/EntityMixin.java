package com.panda.fulldraw.mixin;

import com.panda.fulldraw.shot.ShotHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * An arrow through a portal or an end gateway is measured from where it comes out: its range starts again there, so a
 * Long Shot can't count the blocks the portal skipped. Across dimensions vanilla makes a new arrow, which is the one
 * returned.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "teleport", at = @At("RETURN"))
    private void fulldraw$rebaseShot(TeleportTransition transition, CallbackInfoReturnable<Entity> cir) {
        if (cir.getReturnValue() instanceof ShotHolder holder && holder.fulldraw$shot() != null)
            holder.fulldraw$setShot(holder.fulldraw$shot().withOrigin(cir.getReturnValue().position()));
    }
}
