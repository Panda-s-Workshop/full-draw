package com.panda.fulldraw.entity;

import com.panda.fulldraw.logic.ArrowTraits;
import com.panda.fulldraw.registry.ModEffects;
import com.panda.fulldraw.registry.ModEntities;
import com.panda.fulldraw.registry.ModItems;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Hits hard on flesh, weakly on plate, and leaves the target bleeding: less through armour, not through plate, and none
 * for those in {@code fulldraw:cannot_bleed}.
 */
public class BroadheadArrow extends FullDrawArrow {
    public BroadheadArrow(EntityType<? extends BroadheadArrow> type, Level level) {
        super(type, level);
    }

    public BroadheadArrow(Level level, LivingEntity owner, ItemStack pickup, @Nullable ItemStack weapon) {
        super(ModEntities.BROADHEAD_ARROW.get(), owner, level, pickup, weapon);
    }

    public BroadheadArrow(Level level, double x, double y, double z, ItemStack pickup) {
        super(ModEntities.BROADHEAD_ARROW.get(), x, y, z, level, pickup);
    }

    @Override
    public ArrowTraits traits() {
        return ArrowTraits.BROADHEAD;
    }

    @Override
    protected void doPostHurtEffects(LivingEntity target) {
        super.doPostHurtEffects(target);
        if (target.getType().builtInRegistryHolder().is(ModEffects.CANNOT_BLEED)) return;
        MobEffectInstance open = target.getEffect(ModEffects.BLEEDING.holder());
        int remaining = open == null ? 0 : open.getDuration();
        int ticks = ArrowTraits.bleedTicks(remaining, target.getArmorValue());
        if (ticks <= remaining) return;
        target.addEffect(wound(ticks), getEffectSource());
    }

    /**
     * A wound's Bleeding: none of an effect's swirl (it filled the bleeder's own view; the effect's drips show the wound)
     * and not ambient (that would frame its icon as a beacon's), the icon shown.
     */
    public static MobEffectInstance wound(int ticks) {
        return new MobEffectInstance(ModEffects.BLEEDING.holder(), ticks, 0, false, false, true);
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(ModItems.BROADHEAD_ARROW.get());
    }
}
