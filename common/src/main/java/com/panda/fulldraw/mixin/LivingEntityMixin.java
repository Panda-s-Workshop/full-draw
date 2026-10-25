package com.panda.fulldraw.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.panda.fulldraw.item.FullDrawCrossbowItem;
import com.panda.fulldraw.entity.FullDrawArrow;
import com.panda.fulldraw.logic.ArrowTraits;
import com.panda.fulldraw.logic.BoltTraits;
import com.panda.fulldraw.registry.ModSounds;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A heavy crossbow's bolt and an armour-piercing head go through part of the armour: only the share they leave counts
 * (both: the product), and the hit clanks as it bites through.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @WrapOperation(method = "getDamageAfterArmorAbsorb", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/damagesource/CombatRules;getDamageAfterAbsorb(Lnet/minecraft/world/entity/LivingEntity;FLnet/minecraft/world/damagesource/DamageSource;FF)F"))
    private float fulldraw$armourShare(LivingEntity entity, float damage, DamageSource source, float armour, float toughness, Operation<Float> original) {
        if (!(source.getDirectEntity() instanceof AbstractArrow arrow)) return original.call(entity, damage, source, armour, toughness);
        BoltTraits bolts = FullDrawCrossbowItem.boltsOf(arrow.getWeaponItem());
        ArrowTraits head = FullDrawArrow.traitsOf(arrow);
        double kept = (bolts == null ? 1.0 : bolts.armourKept()) * (head == null ? 1.0 : head.armourKept());
        if (kept >= 1.0) return original.call(entity, damage, source, armour, toughness);
        if (BoltTraits.clanks(kept, damage, armour)) FullDrawCrossbowItem.boltSound(arrow, entity, ModSounds.BOLT_ARMOUR.get(), 0.6F);
        return original.call(entity, damage, source, (float) (armour * kept), toughness);
    }
}
