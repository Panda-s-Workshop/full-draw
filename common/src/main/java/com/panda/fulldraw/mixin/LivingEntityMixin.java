package com.panda.fulldraw.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.panda.fulldraw.item.FullDrawCrossbowItem;
import com.panda.fulldraw.logic.BoltTraits;
import com.panda.fulldraw.registry.ModSounds;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A heavy crossbow's bolt goes through part of the armour: only the share its weapon leaves counts, and the bolt clanks
 * as it bites through.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @WrapOperation(method = "getDamageAfterArmorAbsorb", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/damagesource/CombatRules;getDamageAfterAbsorb(Lnet/minecraft/world/entity/LivingEntity;FLnet/minecraft/world/damagesource/DamageSource;FF)F"))
    private float fulldraw$boltArmour(LivingEntity entity, float damage, DamageSource source, float armour, float toughness, Operation<Float> original) {
        BoltTraits bolts = source.getDirectEntity() instanceof AbstractArrow arrow ? FullDrawCrossbowItem.boltsOf(arrow.getWeaponItem()) : null;
        if (bolts == null || bolts.armourKept() >= 1.0) return original.call(entity, damage, source, armour, toughness);
        if (bolts.clanks(damage, armour) && source.getDirectEntity() instanceof AbstractArrow bolt)
            FullDrawCrossbowItem.boltSound(bolt, entity, ModSounds.BOLT_ARMOUR.get(), 0.6F);
        return original.call(entity, damage, source, (float) (armour * bolts.armourKept()), toughness);
    }
}
