package com.panda.fulldraw.mixin;

import com.panda.fulldraw.shot.Weapons;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Deadeye, Quickdraw and Steady work only through the shot rules, so they aren't offered on a bow the rules leave
 * alone (another mod's bow that fires its own way, or one tagged out): not at the table, the anvil or in loot.
 */
@Mixin(Enchantment.class)
public abstract class EnchantmentMixin {
    @Inject(method = {"isSupportedItem", "canEnchant"}, at = @At("HEAD"), cancellable = true)
    private void fulldraw$onlyInPipeline(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.getItem() instanceof ProjectileWeaponItem && Weapons.worksThroughShotRules((Enchantment) (Object) this) && !Weapons.inPipeline(stack))
            cir.setReturnValue(false);
    }
}
