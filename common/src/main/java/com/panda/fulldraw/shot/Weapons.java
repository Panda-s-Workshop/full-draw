package com.panda.fulldraw.shot;

import com.panda.fulldraw.logic.WeaponStats;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Which {@link WeaponStats} a weapon fires with: Full Draw's own weapons know theirs, every other bow is vanilla. */
public final class Weapons {
    /** Implemented by Full Draw's bows and crossbows. */
    public interface Ranged {
        WeaponStats stats();
    }

    /** Weapons the shot pipeline leaves alone: they fire, draw and look as vanilla's. Empty by default. */
    public static final TagKey<Item> NO_SHOT_PIPELINE = TagKey.create(Registries.ITEM, com.panda.fulldraw.FullDrawMod.id("no_shot_pipeline"));

    private Weapons() {}

    /** Whether the shot pipeline (Perfect, spread, Steady Aim, the ring) applies to this weapon. */
    public static boolean inPipeline(ItemStack weapon) {
        return !weapon.is(NO_SHOT_PIPELINE);
    }

    public static WeaponStats stats(ItemStack weapon) {
        return weapon.getItem() instanceof Ranged r ? r.stats() : WeaponStats.VANILLA_BOW;
    }

    /** The weapon's Steady level (+1 s of breath per level). 0 until the Steady enchantment exists. */
    public static int steadyLevel(ItemStack weapon) {
        return 0;
    }
}
