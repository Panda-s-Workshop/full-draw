package com.panda.fulldraw.fletching;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.panda.fulldraw.registry.ModRecipes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;

/**
 * {@code fulldraw:fletching}: what the Fletching Table makes from a head, a shaft and a fletching, and sometimes an extra
 * (a Rope Arrow's coil). Each part names how many of its item one craft takes. With no extra, the extra slot must be
 * empty. The table's slots take only the items in their tags ({@code #fulldraw:fletching/heads}, {@code shafts},
 * {@code fletchings}, {@code extras}): a recipe with a new part needs the part in its slot's tag as well.
 */
public record FletchingRecipe(Part head, Part shaft, Part fletching, Optional<Part> extra, ItemStackTemplate result)
        implements Recipe<FletchingInput> {

    /** One slot's ingredient and how many of it a craft takes. */
    public record Part(Ingredient ingredient, int count) {
        public static final Codec<Part> CODEC = RecordCodecBuilder.create(i -> i.group(
                Ingredient.CODEC.fieldOf("ingredient").forGetter(Part::ingredient),
                Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(Part::count)
        ).apply(i, Part::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Part> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, Part::ingredient,
                ByteBufCodecs.VAR_INT, Part::count,
                Part::new);

        public boolean test(ItemStack stack) {
            return ingredient.test(stack) && stack.getCount() >= count;
        }
    }

    public static final MapCodec<FletchingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Part.CODEC.fieldOf("head").forGetter(FletchingRecipe::head),
            Part.CODEC.fieldOf("shaft").forGetter(FletchingRecipe::shaft),
            Part.CODEC.fieldOf("fletching").forGetter(FletchingRecipe::fletching),
            Part.CODEC.optionalFieldOf("extra").forGetter(FletchingRecipe::extra),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(FletchingRecipe::result)
    ).apply(i, FletchingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FletchingRecipe> STREAM_CODEC = StreamCodec.composite(
            Part.STREAM_CODEC, FletchingRecipe::head,
            Part.STREAM_CODEC, FletchingRecipe::shaft,
            Part.STREAM_CODEC, FletchingRecipe::fletching,
            ByteBufCodecs.optional(Part.STREAM_CODEC), FletchingRecipe::extra,
            ItemStackTemplate.STREAM_CODEC, FletchingRecipe::result,
            FletchingRecipe::new);

    @Override
    public boolean matches(FletchingInput input, Level level) {
        return head.test(input.head()) && shaft.test(input.shaft()) && fletching.test(input.fletching())
                && extra.map(p -> p.test(input.extra())).orElse(input.extra().isEmpty());
    }

    /** How many each input slot gives up for one craft, in slot order (head, shaft, fletching, extra). */
    public int[] counts() {
        return new int[] {head.count(), shaft.count(), fletching.count(), extra.map(Part::count).orElse(0)};
    }

    @Override
    public ItemStack assemble(FletchingInput input) {
        return result.create();
    }

    /** No: vanilla's toast says to check the recipe book, which has no Fletching page. */
    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public RecipeSerializer<FletchingRecipe> getSerializer() {
        return ModRecipes.FLETCHING_SERIALIZER.get();
    }

    @Override
    public RecipeType<FletchingRecipe> getType() {
        return ModRecipes.FLETCHING.get();
    }

    private List<Ingredient> ingredients() {
        List<Ingredient> out = new ArrayList<>(List.of(head.ingredient(), shaft.ingredient(), fletching.ingredient()));
        extra.ifPresent(p -> out.add(p.ingredient()));
        return out;
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.create(ingredients());
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(new ShapelessCraftingRecipeDisplay(ingredients().stream().map(Ingredient::display).toList(),
                new SlotDisplay.ItemStackSlotDisplay(result), new SlotDisplay.ItemSlotDisplay(Items.FLETCHING_TABLE)));
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return ModRecipes.FLETCHING_CATEGORY.get();
    }
}
