package com.panda.fulldraw.fletching;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

/** What lies on the Fletching Table: the head, shaft, fletching and extra slots, in that order. */
public record FletchingInput(ItemStack head, ItemStack shaft, ItemStack fletching, ItemStack extra) implements RecipeInput {
    @Override
    public ItemStack getItem(int index) {
        return switch (index) {
            case 0 -> head;
            case 1 -> shaft;
            case 2 -> fletching;
            case 3 -> extra;
            default -> throw new IllegalArgumentException("No slot " + index + " on a Fletching Table");
        };
    }

    @Override
    public int size() {
        return 4;
    }
}
