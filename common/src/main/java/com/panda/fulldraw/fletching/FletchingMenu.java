package com.panda.fulldraw.fletching;

import com.panda.fulldraw.FullDrawMod;
import com.panda.fulldraw.registry.ModMenus;
import com.panda.fulldraw.registry.ModRecipes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The menu vanilla's Fletching Table opens: head, shaft, fletching and extra slots and the result, as the Smithing
 * Table's. Each slot takes the items its tag names ({@code #fulldraw:fletching/heads} and so on), so a data pack that
 * adds a recipe with a new part adds the part to its slot's tag too. Taking the result uses up what the recipe asks of
 * each slot.
 */
public class FletchingMenu extends ItemCombinerMenu {
    public static final int HEAD = 0, SHAFT = 1, FLETCHING = 2, EXTRA = 3, RESULT = 4;
    /** Where the slots sit on the 176 x 166 screen (the background texture draws their frames there). */
    public static final int FLETCHING_X = 26, SHAFT_X = 44, HEAD_X = 62, ROW_Y = 27, EXTRA_X = 44, EXTRA_Y = 49, RESULT_X = 124, RESULT_Y = 38;
    /** The items each slot takes, by slot. */
    public static final List<TagKey<Item>> SLOT_TAGS = List.of(tag("heads"), tag("shafts"), tag("fletchings"), tag("extras"));
    /** At most this many near misses are hinted at once. */
    public static final int HINTS = 2;
    /**
     * A hint's numbers: the arrow it would make, the slot short of it, how many it needs there, of which item. An item id
     * takes two slots: a menu's data goes to the client as shorts (on Fabric with nothing to carry more), so ids past
     * 32767 in a big pack would come back wrong.
     */
    private static final int HINT_RESULT = 0, HINT_SLOT = 2, HINT_COUNT = 3, HINT_ITEM = 4, HINT_SIZE = 6;

    private final Level level;
    private final DataSlot recipeError = DataSlot.standalone();
    /** {@link #HINTS} hints of {@link #HINT_SIZE} numbers each; a result of -1 (sent as 0) means none. */
    private final DataSlot[] hints = new DataSlot[HINTS * HINT_SIZE];
    @Nullable
    private RecipeHolder<FletchingRecipe> current;
    /** The tick the work sound last played: a shift-click that crafts many plays it once. */
    private long lastSound;

    /** The client's menu: the server fills it. */
    public FletchingMenu(int id, Inventory inventory) {
        this(id, inventory, ContainerLevelAccess.NULL);
    }

    public FletchingMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(ModMenus.FLETCHING.get(), id, inventory, access, slots());
        this.level = inventory.player.level();
        addDataSlot(recipeError).set(0);
        for (int i = 0; i < hints.length; i++) addDataSlot(hints[i] = DataSlot.standalone()).set(0);
    }

    /** What a Fletching Table at this spot opens. */
    public static MenuProvider provider(Level level, BlockPos pos) {
        return new SimpleMenuProvider((id, inventory, player) -> new FletchingMenu(id, inventory, ContainerLevelAccess.create(level, pos)),
                Blocks.FLETCHING_TABLE.getName());
    }

    /**
     * Whether the server has any Fletching recipe. The first time it's asked after a (re)load, it also warns about every
     * recipe part its slot's tag leaves out: such a recipe can't be made, since the slot won't take the part.
     */
    public static boolean hasRecipes(ServerLevel server) {
        var recipes = server.recipeAccess();
        if (checked != recipes) {
            checked = recipes;
            for (String missing : untaggedParts(server)) FullDrawMod.LOGGER.warn("Fletching recipe part not in its slot's tag, so it can't be made: {}", missing);
        }
        return recipes.getRecipes().stream().anyMatch(r -> r.value() instanceof FletchingRecipe);
    }

    @Nullable
    private static Object checked;

    /** Every recipe part whose item its slot's tag leaves out, as "recipe: item (slot tag)". */
    public static List<String> untaggedParts(ServerLevel server) {
        List<String> out = new ArrayList<>();
        for (RecipeHolder<?> holder : server.recipeAccess().getRecipes()) {
            if (!(holder.value() instanceof FletchingRecipe r)) continue;
            List<Optional<FletchingRecipe.Part>> parts = List.of(Optional.of(r.head()), Optional.of(r.shaft()), Optional.of(r.fletching()), r.extra());
            for (int slot = HEAD; slot <= EXTRA; slot++) {
                TagKey<Item> tag = SLOT_TAGS.get(slot);
                parts.get(slot).ifPresent(part -> part.ingredient().items().filter(item -> !item.is(tag))
                        .forEach(item -> out.add(holder.id().identifier() + ": " + item.getRegisteredName() + " (#" + tag.location() + ")")));
            }
        }
        return out;
    }

    private static TagKey<Item> tag(String slot) {
        return TagKey.create(Registries.ITEM, FullDrawMod.id("fletching/" + slot));
    }

    private static ItemCombinerMenuSlotDefinition slots() {
        return ItemCombinerMenuSlotDefinition.create()
                .withSlot(HEAD, HEAD_X, ROW_Y, s -> s.is(SLOT_TAGS.get(HEAD)))
                .withSlot(SHAFT, SHAFT_X, ROW_Y, s -> s.is(SLOT_TAGS.get(SHAFT)))
                .withSlot(FLETCHING, FLETCHING_X, ROW_Y, s -> s.is(SLOT_TAGS.get(FLETCHING)))
                .withSlot(EXTRA, EXTRA_X, EXTRA_Y, s -> s.is(SLOT_TAGS.get(EXTRA)))
                .withResultSlot(RESULT, RESULT_X, RESULT_Y)
                .build();
    }

    @Override
    protected boolean isValidBlock(BlockState state) {
        return state.is(Blocks.FLETCHING_TABLE);
    }

    private FletchingInput input() {
        return new FletchingInput(inputSlots.getItem(HEAD), inputSlots.getItem(SHAFT), inputSlots.getItem(FLETCHING), inputSlots.getItem(EXTRA));
    }

    /**
     * The server finds the recipe and puts its result out; the client shows what the server sends. With a head, a shaft
     * and a fletching in and nothing made, the arrow is crossed out, and the server says what the nearest recipes still
     * need.
     */
    @Override
    public void createResult() {
        FletchingInput input = input();
        Optional<RecipeHolder<FletchingRecipe>> found = level instanceof ServerLevel server
                ? server.recipeAccess().getRecipeFor(ModRecipes.FLETCHING.get(), input, level) : Optional.empty();
        current = found.orElse(null);
        resultSlots.setRecipeUsed(current);
        resultSlots.setItem(0, current == null ? ItemStack.EMPTY : current.value().assemble(input));
        if (level instanceof ServerLevel server) {
            boolean assembled = !input.head().isEmpty() && !input.shaft().isEmpty() && !input.fletching().isEmpty();
            recipeError.set(current == null && assembled ? 1 : 0);
            List<int[]> near = current == null && assembled ? nearMisses(server, input) : List.of();
            for (int h = 0; h < HINTS; h++) {
                int[] hint = h < near.size() ? near.get(h) : new int[] {-1, -1, 0, -1};
                int base = h * HINT_SIZE;
                putId(base + HINT_RESULT, hint[0]);
                hints[base + HINT_SLOT].set(hint[1]);
                hints[base + HINT_COUNT].set(hint[2]);
                putId(base + HINT_ITEM, hint[3]);
            }
        }
    }

    /**
     * The recipes whose head, shaft and fletching take what lies there, and the first thing each is short of: a slot
     * that holds too few, an extra it wants, or an extra it doesn't (count 0). As {@code {result, slot, count, item}}.
     */
    private static List<int[]> nearMisses(ServerLevel server, FletchingInput input) {
        List<int[]> out = new ArrayList<>();
        for (RecipeHolder<?> holder : server.recipeAccess().getRecipes()) {
            if (!(holder.value() instanceof FletchingRecipe r) || out.size() >= HINTS) continue;
            FletchingRecipe.Part[] parts = {r.head(), r.shaft(), r.fletching()};
            boolean fits = true;
            for (int slot = HEAD; slot <= FLETCHING; slot++) fits &= parts[slot].ingredient().test(input.getItem(slot));
            if (!fits) continue;
            int result = BuiltInRegistries.ITEM.getId(r.result().create().getItem());
            int[] hint = null;
            for (int slot = HEAD; slot <= FLETCHING && hint == null; slot++)
                if (input.getItem(slot).getCount() < parts[slot].count())
                    hint = new int[] {result, slot, parts[slot].count(), BuiltInRegistries.ITEM.getId(input.getItem(slot).getItem())};
            if (hint == null && r.extra().isPresent()) {
                FletchingRecipe.Part extra = r.extra().get();
                ItemStack there = input.extra();
                Item wanted = extra.ingredient().test(there) ? there.getItem()
                        : extra.ingredient().items().findFirst().map(Holder::value).orElse(Items.AIR);
                if (!extra.test(there)) hint = new int[] {result, EXTRA, extra.count(), BuiltInRegistries.ITEM.getId(wanted)};
            } else if (hint == null && !input.extra().isEmpty()) {
                hint = new int[] {result, EXTRA, 0, -1};
            }
            if (hint != null) out.add(hint);
        }
        return out;
    }

    /** What the server says the nearest recipes still need, for the crossed-out arrow's tooltip. */
    public List<Hint> hints() {
        List<Hint> out = new ArrayList<>();
        for (int h = 0; h < HINTS; h++) {
            int result = id(h * HINT_SIZE + HINT_RESULT);
            if (result < 0) continue;
            int item = id(h * HINT_SIZE + HINT_ITEM);
            out.add(new Hint(BuiltInRegistries.ITEM.byId(result), hints[h * HINT_SIZE + HINT_SLOT].get(), hints[h * HINT_SIZE + HINT_COUNT].get(),
                    item < 0 ? Items.AIR : BuiltInRegistries.ITEM.byId(item)));
        }
        return out;
    }

    private void putId(int at, int id) {
        int[] halves = idHalves(id);
        hints[at].set(halves[0]);
        hints[at + 1].set(halves[1]);
    }

    private int id(int at) {
        return idFromHalves(hints[at].get(), hints[at + 1].get());
    }

    /** An item id (or -1) as two numbers of 15 bits, each a short can carry. */
    public static int[] idHalves(int id) {
        int value = id + 1;
        return new int[] {value & 0x7FFF, value >>> 15};
    }

    public static int idFromHalves(int low, int high) {
        return ((high & 0x7FFF) << 15 | low & 0x7FFF) - 1;
    }

    /** A near miss: {@code result} needs {@code count} of {@code item} in {@code slot}; a count of 0 means that slot must be empty. */
    public record Hint(Item result, int slot, int count, Item item) {}

    /**
     * A shift-click on the result crafts as many as there's room for. What doesn't fit of the last craft drops at the
     * player's feet, as at the crafting table, rather than being lost when the result is made again.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index != getResultSlot()) return super.quickMoveStack(player, index);
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack made = stack.copy();
        if (!moveItemStackTo(stack, index + 1, slots.size(), true)) return ItemStack.EMPTY;
        slot.onQuickCraft(stack, made);
        slot.onTake(player, made);
        if (!stack.isEmpty() && !player.level().isClientSide()) player.drop(stack.copy(), false, net.minecraft.util.Prediction.SERVER_ONLY);
        slot.setByPlayer(ItemStack.EMPTY);
        createResult();
        return made;
    }

    /** From the inventory, a shift-click tries the part slots only with a part one of them takes; anything else moves between the inventory's rows. */
    @Override
    protected boolean canMoveIntoInputSlots(ItemStack stack) {
        for (int slot = HEAD; slot <= EXTRA; slot++) {
            ItemStack there = inputSlots.getItem(slot);
            if (stack.is(SLOT_TAGS.get(slot)) && (there.isEmpty() || ItemStack.isSameItemSameComponents(there, stack) && there.getCount() < there.getMaxStackSize()))
                return true;
        }
        return false;
    }

    @Override
    protected void onTake(Player player, ItemStack stack) {
        stack.onCraftedBy(player, stack.getCount());
        resultSlots.awardUsedRecipes(player, List.of(inputSlots.getItem(HEAD), inputSlots.getItem(SHAFT), inputSlots.getItem(FLETCHING), inputSlots.getItem(EXTRA)));
        // the client doesn't know the recipe: it takes nothing, and the server's slots follow
        if (current != null) {
            int[] counts = current.value().counts();
            for (int slot = HEAD; slot <= EXTRA; slot++) shrink(slot, counts[slot]);
        }
        access.execute((l, pos) -> {
            if (l.getGameTime() == lastSound) return;
            lastSound = l.getGameTime();
            l.playSound(null, pos, SoundEvents.VILLAGER_WORK_FLETCHER, SoundSource.BLOCKS, 1.0F, 1.0F);
        });
    }

    private void shrink(int slot, int count) {
        if (count <= 0) return;
        ItemStack stack = inputSlots.getItem(slot);
        if (stack.isEmpty()) return;
        stack.shrink(count);
        inputSlots.setItem(slot, stack);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != resultSlots && super.canTakeItemForPickAll(stack, slot);
    }

    /** A head, a shaft and a fletching lie on the table and make nothing: the screen crosses out the arrow. */
    public boolean hasRecipeError() {
        return recipeError.get() > 0;
    }
}
