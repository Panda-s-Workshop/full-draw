package com.panda.fulldraw.client;

import com.mojang.serialization.MapCodec;
import com.panda.fulldraw.item.RepeatingCrossbowItem;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** The item model property {@code fulldraw:magazine}: shots left in a magazine crossbow, so its sprite can show them. */
public record MagazineShots() implements RangeSelectItemModelProperty {
    public static final MapCodec<MagazineShots> MAP_CODEC = MapCodec.unit(new MagazineShots());

    @Override
    public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        return RepeatingCrossbowItem.shotsLoaded(stack);
    }

    @Override
    public MapCodec<MagazineShots> type() {
        return MAP_CODEC;
    }
}
