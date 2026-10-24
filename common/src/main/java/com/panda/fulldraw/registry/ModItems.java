package com.panda.fulldraw.registry;

import com.panda.fulldraw.FullDrawMod;
import com.panda.fulldraw.item.FullDrawBowItem;
import com.panda.fulldraw.item.QuiverItem;
import com.panda.fulldraw.logic.WeaponStats;
import com.panda.fulldraw.platform.registry.DeferredItem;
import com.panda.fulldraw.platform.registry.DeferredRegister;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.UseEffects;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FullDrawMod.MODID);

    /** Fast draw, light arrows, and you keep running while you draw. */
    public static final DeferredItem<FullDrawBowItem> SHORTBOW = ITEMS.registerItem("shortbow", p -> new FullDrawBowItem(p, WeaponStats.SHORTBOW),
            p -> p.durability(256).enchantable(1).component(DataComponents.USE_EFFECTS, new UseEffects(true, true, 1.0F)));

    /** Four kinds of arrows anywhere in the inventory, R picks which one the bow draws. */
    public static final DeferredItem<QuiverItem> QUIVER = ITEMS.registerItem("quiver", QuiverItem::new, p -> p.stacksTo(1));

    private ModItems() {}
}
