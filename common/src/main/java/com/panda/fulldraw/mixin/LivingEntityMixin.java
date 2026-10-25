package com.panda.fulldraw.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.panda.fulldraw.item.FullDrawCrossbowItem;
import com.panda.fulldraw.FullDrawConfig;
import com.panda.fulldraw.entity.FullDrawArrow;
import com.panda.fulldraw.entity.StuckArrows;
import com.panda.fulldraw.logic.ArrowTraits;
import com.panda.fulldraw.logic.BoltTraits;
import com.panda.fulldraw.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A heavy crossbow's bolt and an armour-piercing head go through part of the armour: only the share they leave counts
 * (both: the product), and the hit clanks as it bites through. Also keeps the paid-for arrows stuck in a creature and
 * drops each back by its chance when a mob dies.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin implements StuckArrows {
    @Unique private final List<Stuck> fulldraw$stuck = new ArrayList<>();

    @Override
    public void fulldraw$stick(Stuck arrow) {
        fulldraw$stuck.add(arrow);
    }

    @Override
    public List<Stuck> fulldraw$stuck() {
        return fulldraw$stuck;
    }

    /** Arrows fall out of a creature one by one (vanilla lowers the count): the oldest go first. */
    @Inject(method = "setArrowCount", at = @At("HEAD"))
    private void fulldraw$arrowsFallOut(int count, CallbackInfo ci) {
        while (fulldraw$stuck.size() > Math.max(0, count)) fulldraw$stuck.removeFirst();
    }

    /**
     * When a mob dies, each paid-for arrow in it comes back by its head's chance, scaled by the config's plain-arrow
     * chance. They drop with its equipment, not its loot table: they're the shooter's, so a baby (which drops no loot)
     * gives them back too, but nothing does with {@code doMobLoot} off. A player's don't: they would land among the
     * player's own dropped inventory.
     */
    @Inject(method = "dropAllDeathLoot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;dropEquipment(Lnet/minecraft/server/level/ServerLevel;)V"))
    private void fulldraw$dropStuckArrows(ServerLevel level, DamageSource source, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (fulldraw$stuck.isEmpty() || self instanceof Player) return;
        if (!level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.MOB_DROPS)) {
            fulldraw$stuck.clear();
            return;
        }
        double plain = FullDrawConfig.arrowRecoveryChance();
        List<ItemStack> back = new ArrayList<>();
        for (Stuck stuck : fulldraw$stuck) {
            if (stuck.arrow().isEmpty() || self.getRandom().nextDouble() >= ArrowTraits.recoveryChance(stuck.recovery(), plain)) continue;
            ItemStack same = back.stream()
                    .filter(s -> ItemStack.isSameItemSameComponents(s, stuck.arrow()) && s.getCount() < s.getMaxStackSize())
                    .findFirst().orElse(null);
            if (same != null) same.grow(1);
            else back.add(stuck.arrow().copyWithCount(1));
        }
        fulldraw$stuck.clear();
        for (ItemStack stack : back) self.spawnAtLocation(level, stack);
    }

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
