package com.panda.fulldraw.quiver;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

/**
 * What a Quiver holds: up to {@link #SLOTS} stacks of arrows, each a different kind, and which one the bow
 * draws from. Immutable, like every component: each change returns a new value.
 *
 * @param stacks   the non-empty stacks, in the order they went in
 * @param selected the index the bow draws from (0 when empty)
 */
public record QuiverContents(List<ItemStackTemplate> stacks, int selected) {
    public static final int SLOTS = 4;
    public static final QuiverContents EMPTY = new QuiverContents(List.of(), 0);

    public static final Codec<QuiverContents> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStackTemplate.CODEC.listOf(0, SLOTS).fieldOf("stacks").forGetter(QuiverContents::stacks),
            Codec.INT.optionalFieldOf("selected", 0).forGetter(QuiverContents::selected)
    ).apply(i, QuiverContents::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, QuiverContents> STREAM_CODEC = StreamCodec.composite(
            ItemStackTemplate.STREAM_CODEC.apply(ByteBufCodecs.list(SLOTS)), QuiverContents::stacks,
            ByteBufCodecs.VAR_INT, QuiverContents::selected,
            QuiverContents::new);

    public QuiverContents {
        stacks = List.copyOf(stacks);
        selected = stacks.isEmpty() ? 0 : Math.floorMod(selected, stacks.size());
    }

    public boolean isEmpty() {
        return stacks.isEmpty();
    }

    /** The stack the bow draws from, as a fresh ItemStack (EMPTY when the quiver is empty). */
    public ItemStack selectedStack() {
        return stacks.isEmpty() ? ItemStack.EMPTY : stacks.get(selected).create();
    }

    public int total() {
        return stacks.stream().mapToInt(ItemStackTemplate::count).sum();
    }

    /** The next kind of arrow (wrapping), or this when there's one kind or none. */
    public QuiverContents cycle(int direction) {
        return stacks.size() < 2 ? this : new QuiverContents(stacks, selected + Integer.signum(direction == 0 ? 1 : direction));
    }

    /** How many of {@code stack} fit: onto a stack of the same arrow, or into a free slot. */
    public int roomFor(ItemStack stack) {
        for (ItemStackTemplate t : stacks) {
            if (same(t, stack)) return Math.max(0, stack.getMaxStackSize() - t.count());
        }
        return stacks.size() < SLOTS ? stack.getMaxStackSize() : 0;
    }

    /** Adds up to {@code count} of {@code stack}; callers shrink the source by {@link #roomFor} first. */
    public QuiverContents insert(ItemStack stack, int count) {
        if (count <= 0 || stack.isEmpty()) return this;
        List<ItemStackTemplate> out = new ArrayList<>(stacks);
        for (int i = 0; i < out.size(); i++) {
            if (same(out.get(i), stack)) {
                out.set(i, out.get(i).withCount(out.get(i).count() + count));
                return new QuiverContents(out, selected);
            }
        }
        out.add(ItemStackTemplate.fromNonEmptyStack(stack, count));
        // the first arrows into an empty quiver are what the bow draws
        return new QuiverContents(out, stacks.isEmpty() ? 0 : selected);
    }

    /** Replaces the selected stack after a bow used some of it (an empty one leaves the quiver). */
    public QuiverContents withSelected(ItemStack after) {
        if (stacks.isEmpty()) return this;
        List<ItemStackTemplate> out = new ArrayList<>(stacks);
        if (after.isEmpty()) {
            out.remove(selected);
            return new QuiverContents(out, selected);
        }
        out.set(selected, ItemStackTemplate.fromNonEmptyStack(after));
        return new QuiverContents(out, selected);
    }

    /** Takes the selected stack out whole (the bundle-style "click to take out"). */
    public QuiverContents removeSelected() {
        return withSelected(ItemStack.EMPTY);
    }

    private static boolean same(ItemStackTemplate t, ItemStack stack) {
        return ItemStack.isSameItemSameComponents(t.create(), stack);
    }
}
