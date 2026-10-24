package com.panda.fulldraw.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.panda.fulldraw.item.FullDrawCrossbowItem;
import com.panda.fulldraw.item.RepeatingCrossbowItem;
import com.panda.fulldraw.logic.Magazine;
import com.panda.fulldraw.registry.ModComponents;
import com.panda.fulldraw.shot.Weapons;
import java.util.ArrayList;
import java.util.List;
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
 * A Full Draw crossbow loads in its own time, and a magazine crossbow loads all its shots in one crank. Vanilla reads
 * the load time from one static method (the use duration, the power curve, the loading sounds and the item model's
 * pull), so changing it there changes all of them; Quick Charge still takes its quarter seconds off.
 */
@Mixin(CrossbowItem.class)
public abstract class CrossbowItemMixin {
    /**
     * A magazine crossbow loads all its shots in one crank: vanilla draws one load of ammo (one arrow, Multishot's
     * three) when the crank completes, so the rest are drawn here, each from whatever ammo is next, until the magazine
     * is full or the ammo runs out.
     */
    @WrapOperation(method = "tryLoadProjectiles", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/CrossbowItem;draw(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)Ljava/util/List;"))
    private static List<ItemStack> fulldraw$magazine(ItemStack weapon, ItemStack ammo, LivingEntity shooter, Operation<List<ItemStack>> original) {
        List<ItemStack> first = original.call(weapon, ammo, shooter);
        Magazine magazine = RepeatingCrossbowItem.magazineOf(weapon);
        if (magazine == null || first.isEmpty()) return first;
        weapon.set(ModComponents.SHOT_SIZE.get(), first.size());
        List<ItemStack> all = new ArrayList<>(first);
        for (int shot = 1; shot < magazine.shots(); shot++) {
            ItemStack next = shooter.getProjectile(weapon);
            List<ItemStack> more = next.isEmpty() ? List.of() : original.call(weapon, next, shooter);
            if (more.isEmpty()) break;
            all.addAll(more);
        }
        return all;
    }

    @Inject(method = "getChargeDuration", at = @At("HEAD"), cancellable = true)
    private static void fulldraw$chargeDuration(ItemStack stack, LivingEntity entity, CallbackInfoReturnable<Integer> cir) {
        if (stack.getItem() instanceof FullDrawCrossbowItem crossbow && Weapons.inPipeline(stack))
            cir.setReturnValue(Mth.floor(EnchantmentHelper.modifyCrossbowChargingTime(stack, entity, crossbow.stats().drawTicks() / 20.0F) * 20.0F));
    }
}
