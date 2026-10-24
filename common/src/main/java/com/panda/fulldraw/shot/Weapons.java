package com.panda.fulldraw.shot;

import com.panda.fulldraw.logic.WeaponStats;
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

    public static WeaponStats stats(ItemStack weapon) {
        if (weapon.getItem() instanceof Ranged r) return r.stats();
        return weapon.getItem() instanceof CrossbowItem ? WeaponStats.VANILLA_CROSSBOW : WeaponStats.VANILLA_BOW;
    }

    /** The weapon's Steady level (+1 s of breath per level). 0 until the Steady enchantment exists. */
    public static int steadyLevel(ItemStack weapon) {
        return 0;
    }
}
