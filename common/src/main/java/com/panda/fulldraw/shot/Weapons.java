package com.panda.fulldraw.shot;

import com.panda.fulldraw.logic.EnchantmentRules;
import com.panda.fulldraw.logic.WeaponStats;
import com.panda.fulldraw.registry.ModEnchantments;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Which {@link WeaponStats} a weapon fires with: Full Draw's own weapons know theirs, every other bow or crossbow is vanilla's. */
public final class Weapons {
    /** Implemented by Full Draw's bows and crossbows. */
    public interface Ranged {
        WeaponStats stats();
    }

    /**
     * Weapons Full Draw leaves alone: they fire, draw and look as vanilla's (a Full Draw crossbow loads, flies and hits
     * as a vanilla crossbow). Empty by default.
     */
    public static final TagKey<Item> NO_SHOT_PIPELINE = TagKey.create(Registries.ITEM, com.panda.fulldraw.FullDrawMod.id("no_shot_pipeline"));

    /** Weapons whose bolts knock a raised shield down for {@link com.panda.fulldraw.logic.BoltTraits#SHIELD_CRACK_SECONDS}. */
    public static final TagKey<Item> SHIELD_PIERCING = TagKey.create(Registries.ITEM, com.panda.fulldraw.FullDrawMod.id("shield_piercing"));

    private Weapons() {}

    /** Whether the shot pipeline (Perfect, spread, Steady Aim, the ring) applies to this weapon. */
    public static boolean inPipeline(ItemStack weapon) {
        return !weapon.is(NO_SHOT_PIPELINE);
    }

    /** The weapon's numbers as it fires: its own, with Quickdraw's shorter draw and Steady's wider Perfect window. */
    public static WeaponStats stats(ItemStack weapon) {
        WeaponStats base = weapon.getItem() instanceof Ranged r ? r.stats()
                : weapon.getItem() instanceof CrossbowItem ? WeaponStats.VANILLA_CROSSBOW : WeaponStats.VANILLA_BOW;
        return EnchantmentRules.apply(base, ModEnchantments.level(weapon, ModEnchantments.QUICKDRAW), steadyLevel(weapon));
    }

    /** The weapon's Steady level (+1 s of breath and 2 ticks of Perfect window per level). */
    public static int steadyLevel(ItemStack weapon) {
        return ModEnchantments.level(weapon, ModEnchantments.STEADY);
    }
}
