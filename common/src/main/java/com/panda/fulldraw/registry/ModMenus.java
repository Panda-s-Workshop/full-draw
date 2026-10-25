package com.panda.fulldraw.registry;

import com.panda.fulldraw.FullDrawMod;
import com.panda.fulldraw.fletching.FletchingMenu;
import com.panda.fulldraw.platform.registry.DeferredHolder;
import com.panda.fulldraw.platform.registry.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, FullDrawMod.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<FletchingMenu>> FLETCHING = MENUS.register("fletching",
            () -> new MenuType<>(FletchingMenu::new, FeatureFlags.VANILLA_SET));

    private ModMenus() {}
}
