package com.panda.fulldraw.registry;

import com.panda.fulldraw.FullDrawMod;
import com.panda.fulldraw.platform.registry.DeferredRegister;
import com.panda.fulldraw.quiver.QuiverContents;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;

public final class ModComponents {
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, FullDrawMod.MODID);

    /** What a Quiver holds and which arrow it hands the bow (synced: the tooltip and the HUD read it). */
    public static final Supplier<DataComponentType<QuiverContents>> QUIVER_CONTENTS = COMPONENTS.registerComponentType("quiver_contents",
            b -> b.persistent(QuiverContents.CODEC).networkSynchronized(QuiverContents.STREAM_CODEC));

    /**
     * How many projectiles one shot of a loaded magazine crossbow fires: what one draw of ammo gave when it was loaded
     * (1, or Multishot's 3). Kept from the load so the shots stay as loaded if the enchantments change; synced, as the
     * sprite counts shots by it.
     */
    public static final Supplier<DataComponentType<Integer>> SHOT_SIZE = COMPONENTS.registerComponentType("shot_size",
            b -> b.persistent(com.mojang.serialization.Codec.intRange(1, 64)).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT));

    private ModComponents() {}
}
