package com.panda.fulldraw.mixin;

import com.panda.fulldraw.quiver.Quivers;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** When a weapon has drawn arrows that came from a Quiver, the Quiver keeps what's left. */
@Mixin(ProjectileWeaponItem.class)
public abstract class ProjectileWeaponItemMixin {
    @Inject(method = "draw", at = @At("RETURN"))
    private static void fulldraw$quiverWriteBack(ItemStack weapon, ItemStack ammo, LivingEntity shooter, CallbackInfoReturnable<List<ItemStack>> cir) {
        Quivers.afterDraw(shooter, ammo);
    }
}
