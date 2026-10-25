package com.panda.fulldraw.client;

import com.mojang.serialization.MapCodec;
import com.panda.fulldraw.shot.Weapons;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The item model property {@code fulldraw:draw}: how far a bow is drawn, 1 at full draw, by the bow's own draw time with
 * Quickdraw counted, so its sprite reaches full draw when the bow does.
 */
public record DrawProgress() implements RangeSelectItemModelProperty {
    public static final MapCodec<DrawProgress> MAP_CODEC = MapCodec.unit(new DrawProgress());

    @Override
    public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        LivingEntity entity = owner == null ? null : owner.asLivingEntity();
        if (entity == null || entity.getUseItem() != stack) return 0.0F;
        return entity.getTicksUsingItem() / (float) Weapons.stats(stack).drawTicks();
    }

    @Override
    public MapCodec<DrawProgress> type() {
        return MAP_CODEC;
    }
}
