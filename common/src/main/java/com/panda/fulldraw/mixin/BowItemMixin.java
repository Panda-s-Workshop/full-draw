package com.panda.fulldraw.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.panda.fulldraw.shot.Shots;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The shot pipeline on every bow (vanilla's, ours, other mods' subclasses): the release is timed, and each arrow is
 * launched with the pipeline's speed and spread. Vanilla's own logic, and the loaders' events inside it, still run.
 */
@Mixin(BowItem.class)
public abstract class BowItemMixin {
    @Inject(method = "releaseUsing", at = @At("HEAD"))
    private void fulldraw$beginRelease(ItemStack stack, Level level, LivingEntity entity, int timeLeft, CallbackInfoReturnable<Boolean> cir) {
        Shots.beginRelease(entity, stack, ((BowItem) (Object) this).getUseDuration(stack, entity) - timeLeft);
    }

    @Inject(method = "releaseUsing", at = @At("RETURN"))
    private void fulldraw$endRelease(ItemStack stack, Level level, LivingEntity entity, int timeLeft, CallbackInfoReturnable<Boolean> cir) {
        Shots.endRelease();
        com.panda.fulldraw.quiver.Quivers.clearPending();
    }

    /**
     * The bow's power follows its own draw time, Quickdraw counted: a bow drawn in 14 ticks is at full power after 14,
     * as vanilla's is after 20.
     */
    @WrapOperation(method = "releaseUsing", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/BowItem;getPowerForTime(I)F"))
    private float fulldraw$power(int ticks, Operation<Float> original, @Local(argsOnly = true) ItemStack stack,
                                 @Local(argsOnly = true) LivingEntity entity) {
        Shots.Release release = Shots.release();
        boolean ours = release != null && release.shooter() == entity && release.weapon() == stack;
        return original.call(ours ? release.stats().toVanillaTicks(release.ticksHeld()) : ticks);
    }

    @WrapOperation(method = "shootProjectile", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/Projectile;shootFromRotation(Lnet/minecraft/world/entity/Entity;FFFFF)V"))
    private void fulldraw$launch(Projectile projectile, Entity shooter, float xRot, float yRot, float roll, float velocity, float inaccuracy,
                                 Operation<Void> original) {
        if (shooter instanceof LivingEntity living) {
            velocity = Shots.velocity(living, projectile, velocity);
            inaccuracy = Shots.spread(living, projectile, inaccuracy);
        }
        original.call(projectile, shooter, xRot, yRot, roll, velocity, inaccuracy);
    }
}
