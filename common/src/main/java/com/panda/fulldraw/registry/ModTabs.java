package com.panda.fulldraw.registry;

import com.panda.fulldraw.FullDrawMod;
import com.panda.fulldraw.platform.Services;
import com.panda.fulldraw.platform.registry.DeferredRegister;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ModTabs {
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FullDrawMod.MODID);

    public static final Supplier<CreativeModeTab> MAIN = TABS.register("main", () -> Services.PLATFORM.tabBuilder()
            .title(Component.translatable("itemGroup.fulldraw"))
            .icon(() -> new ItemStack(Items.BOW))
            .displayItems((params, output) -> ModItems.ITEMS.getEntries().forEach(h -> output.accept(h.get())))
            .build());

    private ModTabs() {}
}
