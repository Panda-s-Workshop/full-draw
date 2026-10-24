package com.panda.fulldraw;

import com.mojang.logging.LogUtils;
import com.panda.fulldraw.registry.ModComponents;
import com.panda.fulldraw.registry.ModItems;
import com.panda.fulldraw.registry.ModSounds;
import com.panda.fulldraw.registry.ModTabs;
import com.panda.fulldraw.registry.ModTriggers;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

/** The mod's common side. Each loader module has its own entry point, which calls {@link #init()} first. */
public final class FullDrawMod {
    public static final String MODID = "fulldraw";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** The classes whose static DeferredRegisters hold the mod's content. */
    private static final List<Class<?>> CONTENT = List.of(ModComponents.class, ModSounds.class, ModTriggers.class, ModItems.class, ModTabs.class);

    private FullDrawMod() {}

    /** Loads the content classes so every registration is queued before the loader registers them. */
    public static void init() {
        for (Class<?> c : CONTENT) {
            try {
                Class.forName(c.getName(), true, c.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
        LOGGER.info("Full Draw booting on {}", com.panda.fulldraw.platform.Services.PLATFORM.loaderName());
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
