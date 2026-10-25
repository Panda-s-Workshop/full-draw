package com.panda.fulldraw.registry;

import com.panda.fulldraw.FullDrawMod;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * Full Draw's enchantments. They are data (identity, cost, slots); their effects are read in code from the level, so
 * a weapon is asked for its level by key, with no registry at hand.
 */
public final class ModEnchantments {
    public static final ResourceKey<Enchantment> DEADEYE = key("deadeye");
    public static final ResourceKey<Enchantment> STEADY = key("steady");
    public static final ResourceKey<Enchantment> QUICKDRAW = key("quickdraw");
    public static final ResourceKey<Enchantment> RICOCHET = key("ricochet");

    private ModEnchantments() {}

    private static ResourceKey<Enchantment> key(String name) {
        return ResourceKey.create(Registries.ENCHANTMENT, FullDrawMod.id(name));
    }

    /** The enchantment's level on the item, or on the book (stored), 0 without it. */
    public static int level(ItemStack stack, ResourceKey<Enchantment> enchantment) {
        if (stack.isEmpty()) return 0;
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet())
            if (entry.getKey().is(enchantment)) return entry.getIntValue();
        return 0;
    }

    /** Sets the enchantment's level on the item (or the book), keeping the holder the stack already has. */
    public static void setLevel(ItemStack stack, ResourceKey<Enchantment> enchantment, int level) {
        EnchantmentHelper.updateEnchantments(stack, mutable -> {
            for (Holder<Enchantment> holder : mutable.keySet())
                if (holder.is(enchantment)) {
                    mutable.set(holder, level);
                    return;
                }
        });
    }
}
