package com.panda.fulldraw.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.panda.fulldraw.FullDrawMod;
import com.panda.fulldraw.network.FullDrawNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** The mod's key mappings. Each loader makes the category its own way and registers the mappings. */
public final class FullDrawKeys {
    public static final Identifier CATEGORY_ID = FullDrawMod.id("main");

    /** Cycle the Quiver's arrows. R: vanilla leaves it free. */
    public static KeyMapping cycleQuiver;

    private FullDrawKeys() {}

    /** Creates the mappings in the loader's category; call once, before registering them. */
    public static KeyMapping[] create(KeyMapping.Category category) {
        cycleQuiver = new KeyMapping("key.fulldraw.cycle_quiver", InputConstants.KEY_R, category);
        return new KeyMapping[] {cycleQuiver};
    }

    /** After each client tick. */
    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (cycleQuiver == null) return;
        while (cycleQuiver.consumeClick()) {
            if (mc.player != null && mc.gui.screen() == null) FullDrawNetwork.toServer.accept(new FullDrawNetwork.CycleQuiver(1));
        }
    }
}
