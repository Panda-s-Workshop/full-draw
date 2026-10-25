package com.panda.fulldraw.entity;

import com.panda.fulldraw.logic.ArrowTraits;
import com.panda.fulldraw.registry.ModEntities;
import com.panda.fulldraw.registry.ModItems;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** A little lighter than an arrow, but most of the target's armour doesn't count against it. */
public class BodkinArrow extends FullDrawArrow {
    public BodkinArrow(EntityType<? extends BodkinArrow> type, Level level) {
        super(type, level);
    }

    public BodkinArrow(Level level, LivingEntity owner, ItemStack pickup, @Nullable ItemStack weapon) {
        super(ModEntities.BODKIN_ARROW.get(), owner, level, pickup, weapon);
    }

    public BodkinArrow(Level level, double x, double y, double z, ItemStack pickup) {
        super(ModEntities.BODKIN_ARROW.get(), x, y, z, level, pickup);
    }

    @Override
    public ArrowTraits traits() {
        return ArrowTraits.BODKIN;
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(ModItems.BODKIN_ARROW.get());
    }
}
