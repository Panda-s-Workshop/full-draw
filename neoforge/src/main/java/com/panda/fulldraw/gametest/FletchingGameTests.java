package com.panda.fulldraw.gametest;

import com.panda.fulldraw.fletching.FletchingInput;
import com.panda.fulldraw.fletching.FletchingMenu;
import com.panda.fulldraw.fletching.FletchingRecipe;
import com.panda.fulldraw.registry.ModItems;
import com.panda.fulldraw.registry.ModRecipes;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/** The Fletching Table: its recipes, its menu on vanilla's table, and what one craft takes and gives. */
final class FletchingGameTests {
    private FletchingGameTests() {}

    /**
     * The Fletcher's trades join vanilla's own in the trade sets a fletcher draws from at levels 2, 3 and 4,
     * beside the two vanilla trades already there, each level now offering three so all stay; and each makes its offer
     * through vanilla's path: 8 Broadhead Arrows
     * for an emerald, 8 Bodkin Arrows for 2, 4 Rope Arrows for 2.
     */
    static void fletcherTrades(GameTestHelper helper) {
        var level = helper.getLevel();
        var villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new Vec3(2.5, 1, 2.5));
        record Expected(int level, String name, Item result, int count, int emeralds) {}
        for (Expected e : List.of(new Expected(2, "emerald_broadhead_arrow", ModItems.BROADHEAD_ARROW.get(), 8, 1),
                new Expected(3, "emerald_bodkin_arrow", ModItems.BODKIN_ARROW.get(), 8, 2),
                new Expected(4, "emerald_rope_arrow", ModItems.ROPE_ARROW.get(), 4, 2))) {
            TradeSet set = level.registryAccess().lookupOrThrow(Registries.TRADE_SET)
                    .getValue(ResourceKey.create(Registries.TRADE_SET, Identifier.withDefaultNamespace("fletcher/level_" + e.level())));
            if (set == null) throw helper.assertionException("no fletcher trade set at level " + e.level());
            var trade = level.registryAccess().lookupOrThrow(Registries.VILLAGER_TRADE)
                    .get(ResourceKey.create(Registries.VILLAGER_TRADE, com.panda.fulldraw.FullDrawMod.id("fletcher/" + e.level() + "/" + e.name())))
                    .orElseThrow(() -> helper.assertionException("trade " + e.name() + " didn't load"));
            if (!set.trades().contains(trade)) throw helper.assertionException(e.name() + " isn't in the level " + e.level() + " trade set");
            // vanilla's two stay beside ours (other mods may add more)
            if (set.trades().size() < 3) throw helper.assertionException("level " + e.level() + " should keep vanilla's 2 trades beside ours, holds " + set.trades().size());
            // the context vanilla's villager builds for its trade sets
            LootContext context = new LootContext.Builder(new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, villager.position())
                    .withParameter(LootContextParams.THIS_ENTITY, villager)
                    .withParameter(LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED, net.minecraft.util.Unit.INSTANCE)
                    .create(LootContextParamSets.VILLAGER_TRADE)).create(set.randomSequence());
            if (set.calculateNumberOfTrades(context) != 3)
                throw helper.assertionException("level " + e.level() + " should offer 3, so vanilla's two stay beside ours: " + set.calculateNumberOfTrades(context));
            MerchantOffer offer = trade.value().getOffer(context);
            if (offer == null || !offer.getResult().is(e.result()) || offer.getResult().getCount() != e.count()
                    || !offer.getCostA().is(Items.EMERALD) || offer.getCostA().getCount() != e.emeralds() || !offer.getCostB().isEmpty())
                throw helper.assertionException(e.name() + " offers " + (offer == null ? "nothing" : offer.getCostA() + " for " + offer.getResult()));
        }
        helper.succeed();
    }

    /**
     * Every Fletching recipe loads, and each is found from its parts: 8 vanilla arrows from flint, a stick and a feather,
     * 8 Broadheads from two iron nuggets, 8 Bodkins from an iron ingot, 8 Blunts from any wool, 4 Rope Arrows with four
     * string in the extra slot. One nugget makes no Broadhead, and a plain arrow's parts with string in the extra slot
     * make nothing.
     */
    static void fletchingRecipesLoad(GameTestHelper helper) {
        var recipes = helper.getLevel().recipeAccess().getRecipes().stream().filter(r -> r.value() instanceof FletchingRecipe).toList();
        if (recipes.size() != 5) throw helper.assertionException("expected 5 Fletching recipes, found " + recipes.size());
        // a part its slot's tag leaves out can't be put in: the generator writes both from one table
        var untagged = FletchingMenu.untaggedParts(helper.getLevel());
        if (!untagged.isEmpty()) throw helper.assertionException("recipe parts missing from their slot tags: " + untagged);
        ItemStack stick = new ItemStack(Items.STICK), feather = new ItemStack(Items.FEATHER);
        Map<Item, FletchingInput> inputs = Map.of(
                Items.ARROW, new FletchingInput(new ItemStack(Items.FLINT), stick, feather, ItemStack.EMPTY),
                ModItems.BROADHEAD_ARROW.get(), new FletchingInput(new ItemStack(Items.IRON_NUGGET, 2), stick, feather, ItemStack.EMPTY),
                ModItems.BODKIN_ARROW.get(), new FletchingInput(new ItemStack(Items.IRON_INGOT), stick, feather, ItemStack.EMPTY),
                ModItems.BLUNT_ARROW.get(), new FletchingInput(new ItemStack(Items.WOOL.lime()), stick, feather, ItemStack.EMPTY),
                ModItems.ROPE_ARROW.get(), new FletchingInput(new ItemStack(Items.IRON_NUGGET), stick, feather, new ItemStack(Items.STRING, 4)));
        Map<Item, Integer> counts = Map.of(Items.ARROW, 8, ModItems.BROADHEAD_ARROW.get(), 8, ModItems.BODKIN_ARROW.get(), 8,
                ModItems.BLUNT_ARROW.get(), 8, ModItems.ROPE_ARROW.get(), 4);
        for (var entry : inputs.entrySet()) {
            ItemStack made = find(helper, entry.getValue());
            if (!made.is(entry.getKey()) || made.getCount() != counts.get(entry.getKey()))
                throw helper.assertionException(entry.getKey() + "'s parts make " + made + ", expected " + counts.get(entry.getKey()));
        }
        if (!find(helper, new FletchingInput(new ItemStack(Items.IRON_NUGGET), stick, feather, ItemStack.EMPTY)).isEmpty())
            throw helper.assertionException("one iron nugget, a stick and a feather should make nothing");
        if (!find(helper, new FletchingInput(new ItemStack(Items.FLINT), stick, feather, new ItemStack(Items.STRING))).isEmpty())
            throw helper.assertionException("an arrow's parts with string in the extra slot should make nothing");
        helper.succeed();
    }

    /**
     * The table through the player's own use: with a stick in hand it opens; sneaking with a block it places the block
     * instead. A shift-click on a sword moves it within the inventory, not into the part slots. One flint alone isn't
     * crossed out; an arrow's parts with string are, with "leave the Extra slot empty". One nugget, a stick and a
     * feather hint at the Broadhead's second nugget and the Rope Arrow's string. With room for only 4 more arrows, a
     * shift-click on 8 drops the other 4 at the player's feet.
     */
    static void fletchingMenuEdges(GameTestHelper helper) {
        BlockPos table = new BlockPos(1, 1, 1);
        helper.setBlock(table, Blocks.FLETCHING_TABLE);
        BlockPos abs = helper.absolutePos(table);
        BlockHitResult side = new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0, 0.5), Direction.SOUTH, abs, false);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 2.5, 180, 30);

        ItemStack cobble = new ItemStack(Items.COBBLESTONE);
        player.setItemInHand(InteractionHand.MAIN_HAND, cobble);
        player.setShiftKeyDown(true);
        player.gameMode.useItemOn(player, helper.getLevel(), cobble, InteractionHand.MAIN_HAND, side);
        if (player.containerMenu != player.inventoryMenu) throw helper.assertionException("sneaking with a block should place it, not open the table");
        if (!helper.getBlockState(table.south()).is(Blocks.COBBLESTONE)) throw helper.assertionException("sneaking with a block should place it against the table");
        helper.setBlock(table.south(), Blocks.AIR);
        player.setShiftKeyDown(false);

        ItemStack stick = new ItemStack(Items.STICK);
        player.setItemInHand(InteractionHand.MAIN_HAND, stick);
        player.gameMode.useItemOn(player, helper.getLevel(), stick, InteractionHand.MAIN_HAND, side);
        if (!(player.containerMenu instanceof FletchingMenu menu)) throw helper.assertionException("using the table with a stick in hand should open it: " + player.containerMenu);

        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
        menu.quickMoveStack(player, HOTBAR);
        if (!player.getInventory().getItem(0).isEmpty() || player.getInventory().countItem(Items.DIAMOND_SWORD) != 1)
            throw helper.assertionException("a shift-click on a sword should move it into the inventory's rows");
        player.getInventory().clearContent();

        menu.getSlot(FletchingMenu.HEAD).set(new ItemStack(Items.FLINT));
        if (menu.hasRecipeError()) throw helper.assertionException("one flint alone shouldn't be crossed out yet");
        menu.getSlot(FletchingMenu.SHAFT).set(new ItemStack(Items.STICK));
        menu.getSlot(FletchingMenu.FLETCHING).set(new ItemStack(Items.FEATHER));
        menu.getSlot(FletchingMenu.EXTRA).set(new ItemStack(Items.STRING));
        List<FletchingMenu.Hint> hints = menu.hints();
        if (!menu.hasRecipeError() || hints.size() != 1 || hints.get(0).result() != Items.ARROW || hints.get(0).slot() != FletchingMenu.EXTRA || hints.get(0).count() != 0)
            throw helper.assertionException("an arrow's parts with string should be crossed out with 'leave the Extra slot empty': " + hints);
        menu.getSlot(FletchingMenu.EXTRA).set(ItemStack.EMPTY);

        menu.getSlot(FletchingMenu.HEAD).set(new ItemStack(Items.IRON_NUGGET));
        hints = menu.hints();
        boolean broadhead = hints.stream().anyMatch(h -> h.result() == ModItems.BROADHEAD_ARROW.get() && h.slot() == FletchingMenu.HEAD && h.count() == 2 && h.item() == Items.IRON_NUGGET);
        boolean rope = hints.stream().anyMatch(h -> h.result() == ModItems.ROPE_ARROW.get() && h.slot() == FletchingMenu.EXTRA && h.count() == 4 && h.item() == Items.STRING);
        if (!broadhead || !rope) throw helper.assertionException("one nugget, a stick and a feather should hint at 2 nuggets and at 4 string: " + hints);

        menu.getSlot(FletchingMenu.HEAD).set(new ItemStack(Items.FLINT));
        for (int i = 0; i < player.getInventory().getContainerSize() - 5; i++) player.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
        player.getInventory().setItem(0, new ItemStack(Items.ARROW, 60));
        menu.clicked(FletchingMenu.RESULT, 0, ContainerInput.QUICK_MOVE, player);
        int dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(3), e -> e.getItem().is(Items.ARROW))
                .stream().mapToInt(e -> e.getItem().getCount()).sum();
        if (player.getInventory().getItem(0).getCount() != 64 || dropped != 4 || menu.getSlot(FletchingMenu.HEAD).hasItem())
            throw helper.assertionException("with room for 4, a shift-click on 8 arrows should fill the stack and drop 4: stack "
                    + player.getInventory().getItem(0).getCount() + ", dropped " + dropped + ", head " + menu.getSlot(FletchingMenu.HEAD).getItem());
        player.closeContainer();
        helper.succeed();
    }

    /** The menu's slot for the first hotbar slot: after the four parts, the result and the 27 inventory slots. */
    private static final int HOTBAR = 32;

    private static ItemStack find(GameTestHelper helper, FletchingInput input) {
        return helper.getLevel().recipeAccess().getRecipeFor(ModRecipes.FLETCHING.get(), input, helper.getLevel())
                .map(r -> r.value().assemble(input)).orElse(ItemStack.EMPTY);
    }

    /**
     * Using vanilla's Fletching Table opens the menu. Two of each arrow part show 8 arrows; string in the extra slot
     * crosses the arrow out; a shift-click on the result makes 16 and empties the slots. Then a Rope Arrow's parts give
     * 4 and take all four string, and closing the menu hands back what's left.
     */
    static void fletchingMenuCraftsEight(GameTestHelper helper) {
        BlockPos table = new BlockPos(1, 1, 1);
        helper.setBlock(table, Blocks.FLETCHING_TABLE);
        BlockPos abs = helper.absolutePos(table);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 2.5, 180, 30);
        InteractionResult used = helper.getBlockState(table).useWithoutItem(helper.getLevel(), player,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.SOUTH, abs, false));
        if (!used.consumesAction() || !(player.containerMenu instanceof FletchingMenu menu))
            throw helper.assertionException("using a Fletching Table should open its menu: " + used + ", " + player.containerMenu);
        menu.getSlot(FletchingMenu.HEAD).set(new ItemStack(Items.FLINT, 2));
        menu.getSlot(FletchingMenu.SHAFT).set(new ItemStack(Items.STICK, 2));
        menu.getSlot(FletchingMenu.FLETCHING).set(new ItemStack(Items.FEATHER, 2));
        ItemStack shown = menu.getSlot(FletchingMenu.RESULT).getItem();
        if (!shown.is(Items.ARROW) || shown.getCount() != 8) throw helper.assertionException("flint, a stick and a feather should show 8 arrows: " + shown);
        menu.getSlot(FletchingMenu.EXTRA).set(new ItemStack(Items.STRING));
        if (!menu.getSlot(FletchingMenu.RESULT).getItem().isEmpty() || !menu.hasRecipeError())
            throw helper.assertionException("string in the extra slot should make nothing and cross the arrow out");
        menu.getSlot(FletchingMenu.EXTRA).set(ItemStack.EMPTY);
        player.getInventory().clearContent();
        menu.clicked(FletchingMenu.RESULT, 0, ContainerInput.QUICK_MOVE, player);
        int arrows = player.getInventory().countItem(Items.ARROW);
        if (arrows != 16) throw helper.assertionException("a shift-click on the result should make 16 arrows from two of each part, made " + arrows);
        for (int slot : List.of(FletchingMenu.HEAD, FletchingMenu.SHAFT, FletchingMenu.FLETCHING))
            if (menu.getSlot(slot).hasItem()) throw helper.assertionException("slot " + slot + " should be used up: " + menu.getSlot(slot).getItem());

        menu.getSlot(FletchingMenu.HEAD).set(new ItemStack(Items.IRON_NUGGET, 3));
        menu.getSlot(FletchingMenu.SHAFT).set(new ItemStack(Items.STICK));
        menu.getSlot(FletchingMenu.FLETCHING).set(new ItemStack(Items.FEATHER));
        menu.getSlot(FletchingMenu.EXTRA).set(new ItemStack(Items.STRING, 4));
        menu.clicked(FletchingMenu.RESULT, 0, ContainerInput.QUICK_MOVE, player);
        int ropes = player.getInventory().countItem(ModItems.ROPE_ARROW.get());
        if (ropes != 4 || menu.getSlot(FletchingMenu.EXTRA).hasItem() || menu.getSlot(FletchingMenu.HEAD).getItem().getCount() != 2)
            throw helper.assertionException("a Rope Arrow craft should give 4 and take one nugget and all four string: " + ropes + " made, extra "
                    + menu.getSlot(FletchingMenu.EXTRA).getItem() + ", head " + menu.getSlot(FletchingMenu.HEAD).getItem());
        player.closeContainer();
        if (player.getInventory().countItem(Items.IRON_NUGGET) != 2) throw helper.assertionException("closing the menu should hand back the two nuggets left");
        helper.succeed();
    }
}
