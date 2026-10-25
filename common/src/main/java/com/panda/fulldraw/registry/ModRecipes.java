package com.panda.fulldraw.registry;

import com.panda.fulldraw.FullDrawMod;
import com.panda.fulldraw.fletching.FletchingRecipe;
import com.panda.fulldraw.platform.registry.DeferredHolder;
import com.panda.fulldraw.platform.registry.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

/** {@code fulldraw:fletching}: what the Fletching Table makes. */
public final class ModRecipes {
    private static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, FullDrawMod.MODID);
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, FullDrawMod.MODID);
    private static final DeferredRegister<RecipeBookCategory> CATEGORIES = DeferredRegister.create(Registries.RECIPE_BOOK_CATEGORY, FullDrawMod.MODID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<FletchingRecipe>> FLETCHING = TYPES.register("fletching", () -> new RecipeType<>() {
        @Override
        public String toString() {
            return FullDrawMod.id("fletching").toString();
        }
    });

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FletchingRecipe>> FLETCHING_SERIALIZER = SERIALIZERS.register("fletching",
            () -> new RecipeSerializer<>(FletchingRecipe.MAP_CODEC, FletchingRecipe.STREAM_CODEC));

    /** No recipe book shows it (vanilla's book has a fixed set of kinds): it exists because every recipe names a category. */
    public static final DeferredHolder<RecipeBookCategory, RecipeBookCategory> FLETCHING_CATEGORY = CATEGORIES.register("fletching", RecipeBookCategory::new);

    private ModRecipes() {}
}
