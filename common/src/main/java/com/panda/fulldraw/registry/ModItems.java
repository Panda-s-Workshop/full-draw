package com.panda.fulldraw.registry;

import com.panda.fulldraw.FullDrawMod;
import com.panda.fulldraw.item.FullDrawBowItem;
import com.panda.fulldraw.item.FullDrawCrossbowItem;
import com.panda.fulldraw.item.QuiverItem;
import com.panda.fulldraw.logic.BoltTraits;
import com.panda.fulldraw.logic.WeaponStats;
import com.panda.fulldraw.platform.registry.DeferredItem;
import com.panda.fulldraw.platform.registry.DeferredRegister;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.UseEffects;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FullDrawMod.MODID);

    /** Fast draw, light arrows, and you keep running while you draw. */
    public static final DeferredItem<FullDrawBowItem> SHORTBOW = ITEMS.registerItem("shortbow", p -> new FullDrawBowItem(p, WeaponStats.SHORTBOW),
            p -> p.durability(256).enchantable(1).component(DataComponents.USE_EFFECTS, new UseEffects(true, true, 1.0F)));

    /** The sniper's bow: a long draw for hard, true, flat shots; you all but stand still to draw it, and not from a run. */
    public static final DeferredItem<FullDrawBowItem> LONGBOW = ITEMS.registerItem("longbow", p -> new FullDrawBowItem(p, WeaponStats.LONGBOW, false),
            p -> p.durability(448).enchantable(1).component(DataComponents.USE_EFFECTS, new UseEffects(false, true, 0.1F)));

    /** The forgiving bow: a wider Perfect window and a little more punch than vanilla's. */
    public static final DeferredItem<FullDrawBowItem> RECURVE_BOW = ITEMS.registerItem("recurve_bow", p -> new FullDrawBowItem(p, WeaponStats.RECURVE_BOW),
            p -> p.durability(512).enchantable(1));

    /**
     * The siege crossbow: a long crank you can't walk through, then a bolt that goes through three mobs, half their armour
     * and a raised shield.
     */
    public static final DeferredItem<FullDrawCrossbowItem> ARBALEST = ITEMS.registerItem("arbalest",
            p -> new FullDrawCrossbowItem(p, WeaponStats.ARBALEST, BoltTraits.ARBALEST),
            p -> p.stacksTo(1).durability(600).component(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY).enchantable(1)
                    .component(DataComponents.USE_EFFECTS, new UseEffects(false, true, 0.0F)));

    /** Four kinds of arrows anywhere in the inventory, R picks which one the bow draws. */
    public static final DeferredItem<QuiverItem> QUIVER = ITEMS.registerItem("quiver", QuiverItem::new, p -> p.stacksTo(1));

    private ModItems() {}
}
