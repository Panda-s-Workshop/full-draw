package com.panda.fulldraw.gametest;

import com.panda.fulldraw.FullDrawMod;
import com.panda.fulldraw.logic.Breath;
import com.panda.fulldraw.logic.EnchantmentRules;
import com.panda.fulldraw.logic.WeaponStats;
import com.panda.fulldraw.registry.ModEnchantments;
import com.panda.fulldraw.registry.ModItems;
import com.panda.fulldraw.shot.RicochetBolt;
import com.panda.fulldraw.shot.ShotHolder;
import com.panda.fulldraw.shot.ShotRecord;
import com.panda.fulldraw.shot.Weapons;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Deadeye, Steady, Quickdraw and Ricochet: what they are in the registry, and what each does to a shot. */
final class EnchantmentGameTests {
    private EnchantmentGameTests() {}

    /**
     * The four are registered with their levels, items and exclusions; an enchanting table at its best (level 30) gives
     * all four but never Deadeye III or Steady II, which come only from the Marksman's Eye; random loot and trades,
     * which pick any level, never give Deadeye or Steady.
     */
    static void enchantmentsRegistered(GameTestHelper helper) {
        Registry<Enchantment> registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Holder<Enchantment> deadeye = registry.getOrThrow(ModEnchantments.DEADEYE), steady = registry.getOrThrow(ModEnchantments.STEADY),
                quickdraw = registry.getOrThrow(ModEnchantments.QUICKDRAW), ricochet = registry.getOrThrow(ModEnchantments.RICOCHET);
        List<String> wrong = new ArrayList<>();
        int[] levels = {deadeye.value().getMaxLevel(), steady.value().getMaxLevel(), quickdraw.value().getMaxLevel(), ricochet.value().getMaxLevel()};
        if (levels[0] != 3 || levels[1] != 2 || levels[2] != 3 || levels[3] != 2) wrong.add("max levels " + java.util.Arrays.toString(levels));
        if (Enchantment.areCompatible(steady, quickdraw)) wrong.add("Steady and Quickdraw go together");
        if (Enchantment.areCompatible(ricochet, registry.getOrThrow(Enchantments.PIERCING))) wrong.add("Ricochet and Piercing go together");
        if (!Enchantment.areCompatible(deadeye, quickdraw) || !Enchantment.areCompatible(deadeye, steady)) wrong.add("Deadeye should go with either");
        for (Item bow : List.of(Items.BOW, ModItems.SHORTBOW.get(), ModItems.LONGBOW.get(), ModItems.RECURVE_BOW.get()))
            for (Holder<Enchantment> e : List.of(deadeye, steady, quickdraw))
                if (!e.value().isSupportedItem(new ItemStack(bow))) wrong.add(e.getRegisteredName() + " not on " + bow);
        for (Item crossbow : List.of(Items.CROSSBOW, ModItems.ARBALEST.get(), ModItems.REPEATING_CROSSBOW.get())) {
            if (!ricochet.value().isSupportedItem(new ItemStack(crossbow))) wrong.add("Ricochet not on " + crossbow);
            if (deadeye.value().isSupportedItem(new ItemStack(crossbow))) wrong.add("Deadeye on " + crossbow);
        }
        if (ricochet.value().isSupportedItem(new ItemStack(Items.BOW))) wrong.add("Ricochet on a bow");
        for (Holder<Enchantment> e : List.of(deadeye, steady)) {
            if (!e.is(EnchantmentTags.IN_ENCHANTING_TABLE)) wrong.add(e.getRegisteredName() + " not on the table");
            if (e.is(EnchantmentTags.ON_RANDOM_LOOT) || e.is(EnchantmentTags.TRADEABLE)) wrong.add(e.getRegisteredName() + " in random loot or trades");
        }
        for (Holder<Enchantment> e : List.of(quickdraw, ricochet))
            if (!e.is(EnchantmentTags.IN_ENCHANTING_TABLE) || !e.is(EnchantmentTags.ON_RANDOM_LOOT) || !e.is(EnchantmentTags.TRADEABLE))
                wrong.add(e.getRegisteredName() + " should be on the table, in loot and in trades");

        // the table, at its best
        HolderSet.Named<Enchantment> table = registry.getOrThrow(EnchantmentTags.IN_ENCHANTING_TABLE);
        RandomSource random = RandomSource.create(20261025L);
        Set<String> seen = new java.util.TreeSet<>();
        for (int i = 0; i < 3000; i++) {
            ItemStack item = new ItemStack(i % 3 == 0 ? Items.CROSSBOW : Items.BOW);
            boolean hasSteady = false, hasQuickdraw = false;
            for (EnchantmentInstance roll : EnchantmentHelper.selectEnchantment(random, item, 30, table.stream())) {
                if (!roll.enchantment().getRegisteredName().startsWith("fulldraw:")) continue;
                seen.add(roll.enchantment().getRegisteredName() + " " + roll.level());
                if (roll.enchantment().is(ModEnchantments.DEADEYE) && roll.level() > EnchantmentRules.DEADEYE_TABLE_MAX) wrong.add("the table gave Deadeye " + roll.level());
                if (roll.enchantment().is(ModEnchantments.STEADY) && roll.level() > EnchantmentRules.STEADY_TABLE_MAX) wrong.add("the table gave Steady " + roll.level());
                hasSteady |= roll.enchantment().is(ModEnchantments.STEADY);
                hasQuickdraw |= roll.enchantment().is(ModEnchantments.QUICKDRAW);
            }
            if (hasSteady && hasQuickdraw) wrong.add("the table gave Steady with Quickdraw");
        }
        for (String expected : List.of("fulldraw:deadeye 2", "fulldraw:steady 1", "fulldraw:quickdraw 3", "fulldraw:ricochet 2"))
            if (!seen.contains(expected)) wrong.add("3000 level-30 rolls never gave " + expected + " (saw " + seen + ")");
        FullDrawMod.LOGGER.info("level-30 table rolls gave {}", seen);
        if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong.subList(0, Math.min(6, wrong.size()))));
        else helper.succeed();
    }

    /**
     * Quickdraw III: a vanilla bow draws in 16 ticks, not 20; released at 19 ticks it is Perfect and at full power,
     * where a plain bow at 19 is neither; at 23, the plain bow's Perfect, the Quickdraw bow's window has shut.
     */
    static void quickdrawShortensDraw(GameTestHelper helper) {
        ItemStack quick = enchanted(helper, Items.BOW, ModEnchantments.QUICKDRAW, 3), plain = new ItemStack(Items.BOW);
        if (Weapons.stats(quick).drawTicks() != 16) throw helper.assertionException("Quickdraw III draw " + Weapons.stats(quick).drawTicks() + ", expected 16");
        WeaponStats recurve = Weapons.stats(enchanted(helper, ModItems.RECURVE_BOW.get(), ModEnchantments.QUICKDRAW, 2));
        if (recurve.drawTicks() != 15) throw helper.assertionException("a Quickdraw II Recurve draws in " + recurve.drawTicks() + ", expected 15 (18 × 0.86)");
        Player player = archer(helper);
        AbstractArrow quick19 = release(helper, player, quick, 19), plain19 = release(helper, player, plain, 19);
        AbstractArrow quick23 = release(helper, player, quick, 23), plain23 = release(helper, player, plain, 23);
        double quickSpeed = quick19.getDeltaMovement().length(), plainSpeed = plain19.getDeltaMovement().length();
        if (!shot(helper, quick19).perfect()) helper.fail("Quickdraw III released at 19 should be Perfect");
        else if (shot(helper, plain19).perfect()) helper.fail("a plain bow released at 19 is short of its draw");
        else if (shot(helper, quick23).perfect()) helper.fail("Quickdraw III released at 23 is past its window");
        else if (!shot(helper, plain23).perfect()) helper.fail("a plain bow released at 23 should be Perfect");
        else if (quickSpeed < 2.9 || plainSpeed > 2.95) helper.fail("at 19 ticks the Quickdraw bow should be at full power and the plain one not: "
                + quickSpeed + " vs " + plainSpeed);
        else helper.succeed();
    }

    /** Steady I: the Perfect window stays open 2 ticks longer (a release at 27 still counts) and the breath lasts 3.5 s. */
    static void steadyWidensWindow(GameTestHelper helper) {
        ItemStack steady = enchanted(helper, Items.BOW, ModEnchantments.STEADY, 1), plain = new ItemStack(Items.BOW);
        if (Breath.capacityTicks(Weapons.steadyLevel(steady)) != 70) throw helper.assertionException("Steady I breath "
                + Breath.capacityTicks(Weapons.steadyLevel(steady)) + " ticks, expected 70");
        if (Weapons.stats(steady).extraWindow() != 2) throw helper.assertionException("Steady I window +" + Weapons.stats(steady).extraWindow());
        Player player = archer(helper);
        if (!shot(helper, release(helper, player, steady, 27)).perfect()) helper.fail("Steady I released at 27 should still be Perfect");
        else if (shot(helper, release(helper, player, plain, 27)).perfect()) helper.fail("a plain bow released at 27 is past its window");
        else if (shot(helper, release(helper, player, steady, 29)).perfect()) helper.fail("Steady I released at 29 is past its window too");
        else helper.succeed();
    }

    /**
     * Deadeye II: a bow's shots carry +0.30 on every weak spot, so a zombie's head takes ×1.8 a body shot, not ×1.5.
     * The release records the bonus from the bow; arrows carrying it strike two zombies.
     */
    static void deadeyeRaisesPrecision(GameTestHelper helper) {
        Player player = archer(helper);
        ShotRecord fromBow = shot(helper, release(helper, player, enchanted(helper, Items.BOW, ModEnchantments.DEADEYE, 2), 23));
        ShotRecord plainBow = shot(helper, release(helper, player, new ItemStack(Items.BOW), 23));
        if (Math.abs(fromBow.precisionBonus() - 0.30) > 1e-9 || plainBow.precisionBonus() != 0)
            throw helper.assertionException("Deadeye II recorded " + fromBow.precisionBonus() + ", a plain bow " + plainBow.precisionBonus());
        Zombie body = zombie(helper, 2.5), head = zombie(helper, 5.5);
        fire(helper, 2.5, 1.0 + 0.8, 0.30);
        fire(helper, 5.5, 1.0 + 1.75, 0.30);
        helper.runAfterDelay(20, () -> {
            float b = 20 - body.getHealth(), h = 20 - head.getHealth();
            if (b < 5 || b > 6.5) helper.fail("body shot dealt " + b + ", expected about 6: Deadeye adds nothing off a weak spot");
            else if (Math.abs(h / b - 1.8) > 0.08) helper.fail("Deadeye II headshot dealt " + h + " vs body " + b + ": expected ×1.8");
            else helper.succeed();
        });
    }

    /**
     * Ricochet: a bolt bounces off a wall once per level, back the way it came at 70% of its speed, then sticks. Three
     * bolts fly at a wall with another behind them: none, one and two bounces. A crossbow gives its bolts its level. The
     * one-bounce bolt strikes a target block, which still lights; a fourth, loosed by a husk standing in its way back,
     * passes it by: a bounced bolt doesn't strike its shooter.
     */
    static void ricochetBounces(GameTestHelper helper) {
        // the crossbow's part: its level onto the bolt, none from a plain one
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(10.5, 1, 10.5));
        player.snapTo(at.x, at.y, at.z, 0, -80);
        player.setYHeadRot(0);
        player.yHeadRotO = 0;
        player.getInventory().setItem(9, new ItemStack(Items.ARROW, 8));
        AbstractArrow enchantedBolt = loadAndFire(helper, player, enchanted(helper, Items.CROSSBOW, ModEnchantments.RICOCHET, 2));
        AbstractArrow plainBolt = loadAndFire(helper, player, new ItemStack(Items.CROSSBOW));
        int fromEnchanted = ((RicochetBolt) enchantedBolt).fulldraw$ricochets(), fromPlain = ((RicochetBolt) plainBolt).fulldraw$ricochets();
        if (fromEnchanted != 2 || fromPlain != 0) throw helper.assertionException("bolts from Ricochet II and plain crossbows carry "
                + fromEnchanted + " and " + fromPlain + " bounces, expected 2 and 0");
        enchantedBolt.discard();
        plainBolt.discard();

        for (int x = 2; x <= 9; x++)
            for (int y = 1; y <= 5; y++) {
                helper.setBlock(new BlockPos(x, y, 1), Blocks.STONE);
                helper.setBlock(new BlockPos(x, y, 10), Blocks.STONE);
            }
        BlockPos target = new BlockPos(5, 2, 10);
        helper.setBlock(target, Blocks.TARGET);
        net.minecraft.world.entity.monster.zombie.Husk shooter = helper.spawnWithNoFreeWill(EntityTypes.HUSK, new Vec3(8.5, 1, 2.5));
        Arrow none = bolt(helper, 3.5, 0), once = bolt(helper, 5.5, 1), twice = bolt(helper, 7.5, 2), back = bolt(helper, 8.5, 1);
        back.setOwner(shooter);
        double[] before = {0}, after = {0};
        int[] lit = {0};
        helper.onEachTick(() -> {
            Vec3 v = once.getDeltaMovement();
            if (v.z > 0.1) before[0] = v.length();
            else if (v.z < -0.1 && after[0] == 0) after[0] = v.length();
            lit[0] = Math.max(lit[0], helper.getBlockState(target).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWER));
        });
        helper.runAfterDelay(40, () -> {
            double zNone = helper.relativeVec(none.position()).z, zOnce = helper.relativeVec(once.position()).z, zTwice = helper.relativeVec(twice.position()).z;
            if (zNone < 9 || zOnce > 3 || zTwice < 9) helper.fail("bolts with 0, 1 and 2 bounces ended at z " + zNone + ", " + zOnce + ", " + zTwice
                    + ": expected at the far wall, the near wall and the far wall");
            else if (((RicochetBolt) twice).fulldraw$ricochets() != 0) helper.fail("the twice-bouncing bolt has bounces left");
            else if (before[0] == 0 || after[0] / before[0] < 0.66 || after[0] / before[0] > 0.72)
                helper.fail("a bounce should keep 70% of the speed (and a tick of drag): " + before[0] + " -> " + after[0]);
            else if (lit[0] == 0) helper.fail("the bolt bounced off a target block without lighting it");
            else if (shooter.getHealth() < shooter.getMaxHealth()) helper.fail("a bolt coming back off the wall struck the husk that loosed it");
            else if (helper.relativeVec(back.position()).z > 3) helper.fail("the husk's bolt should have flown past it to the near wall, at z "
                    + helper.relativeVec(back.position()).z);
            else helper.succeed();
        });
    }

    /** The Marksman's Eye makes the tiers a table can't: Eye + book + spyglass = Deadeye III, + amethyst shard = Steady II. */
    static void eyeBooksCraft(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack deadeye = craft(level, new ItemStack(ModItems.MARKSMANS_EYE.get()), new ItemStack(Items.BOOK), new ItemStack(Items.SPYGLASS));
        ItemStack steady = craft(level, new ItemStack(Items.AMETHYST_SHARD), new ItemStack(ModItems.MARKSMANS_EYE.get()), new ItemStack(Items.BOOK));
        ItemStack noEye = craft(level, new ItemStack(Items.BOOK), new ItemStack(Items.SPYGLASS), ItemStack.EMPTY);
        if (!deadeye.is(Items.ENCHANTED_BOOK) || ModEnchantments.level(deadeye, ModEnchantments.DEADEYE) != 3) helper.fail("Eye + book + spyglass gave " + deadeye);
        else if (!steady.is(Items.ENCHANTED_BOOK) || ModEnchantments.level(steady, ModEnchantments.STEADY) != 2) helper.fail("Eye + book + amethyst gave " + steady);
        else if (!noEye.isEmpty()) helper.fail("a book and a spyglass alone made " + noEye);
        else helper.succeed();
    }

    /**
     * An anvil puts an Eye-made Deadeye III book on a bow, but two Deadeye II (or two Steady I) don't make the next tier:
     * a bow and a book of the same level give nothing.
     */
    static void anvilKeepsEyeTiers(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        List<String> wrong = new ArrayList<>();
        ItemStack eyeBook = anvil(player, enchanted(helper, Items.BOW, ModEnchantments.DEADEYE, 1), book(helper, ModEnchantments.DEADEYE, 3));
        if (ModEnchantments.level(eyeBook, ModEnchantments.DEADEYE) != 3) wrong.add("bow + Deadeye III book gave " + eyeBook);
        ItemStack twoII = anvil(player, enchanted(helper, Items.BOW, ModEnchantments.DEADEYE, 2), book(helper, ModEnchantments.DEADEYE, 2));
        if (!twoII.isEmpty()) wrong.add("Deadeye II bow + Deadeye II book gave " + twoII);
        ItemStack books = anvil(player, book(helper, ModEnchantments.DEADEYE, 2), book(helper, ModEnchantments.DEADEYE, 2));
        if (!books.isEmpty()) wrong.add("two Deadeye II books gave " + books);
        ItemStack oneI = anvil(player, book(helper, ModEnchantments.DEADEYE, 1), book(helper, ModEnchantments.DEADEYE, 1));
        if (ModEnchantments.level(oneI, ModEnchantments.DEADEYE) != 2) wrong.add("two Deadeye I books should make II, gave " + oneI);
        ItemStack steady = anvil(player, enchanted(helper, Items.BOW, ModEnchantments.STEADY, 1), book(helper, ModEnchantments.STEADY, 1));
        if (!steady.isEmpty()) wrong.add("Steady I bow + Steady I book gave " + steady);
        ItemStack steadyII = anvil(player, new ItemStack(Items.BOW), book(helper, ModEnchantments.STEADY, 2));
        if (ModEnchantments.level(steadyII, ModEnchantments.STEADY) != 2) wrong.add("bow + Steady II book gave " + steadyII);
        // the cap leaves the rest of the merge alone: Power from the book still goes on
        ItemStack book = book(helper, ModEnchantments.DEADEYE, 2);
        book.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.POWER), 2);
        ItemStack mixed = anvil(player, enchanted(helper, Items.BOW, ModEnchantments.DEADEYE, 2), book);
        if (ModEnchantments.level(mixed, ModEnchantments.DEADEYE) != 2 || EnchantmentHelper.getItemEnchantmentLevel(
                helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.POWER), mixed) != 2)
            wrong.add("Deadeye II + a Deadeye II, Power II book should give Deadeye II and Power II, gave " + mixed);
        if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
        else helper.succeed();
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    private static ItemStack enchanted(GameTestHelper helper, Item item, ResourceKey<Enchantment> key, int level) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key), level);
        return stack;
    }

    private static ItemStack book(GameTestHelper helper, ResourceKey<Enchantment> key, int level) {
        return EnchantmentHelper.createBook(new EnchantmentInstance(
                helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key), level));
    }

    private static ItemStack anvil(Player player, ItemStack left, ItemStack right) {
        AnvilMenu menu = new AnvilMenu(0, player.getInventory());
        menu.getSlot(AnvilMenu.INPUT_SLOT).set(left);
        menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(right);
        menu.createResult();
        return menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().copy();
    }

    private static ItemStack craft(ServerLevel level, ItemStack a, ItemStack b, ItemStack c) {
        List<ItemStack> grid = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        grid.set(0, a);
        grid.set(4, b);
        grid.set(8, c);
        CraftingInput input = CraftingInput.of(3, 3, grid);
        return level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level).map(r -> r.value().assemble(input)).orElse(ItemStack.EMPTY);
    }

    /** A survival archer aiming up into the roof, with arrows to spare. */
    private static Player archer(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(10.5, 1, 1.5));
        player.snapTo(at.x, at.y, at.z, 0, -80);
        // in the inventory, not the hotbar slot the bow goes into
        player.getInventory().setItem(9, new ItemStack(Items.ARROW, 32));
        return player;
    }

    /** Draws the bow for {@code held} ticks and lets go: the arrow it looses. */
    private static AbstractArrow release(GameTestHelper helper, Player player, ItemStack bow, int held) {
        player.setItemInHand(InteractionHand.MAIN_HAND, bow);
        BowItem item = (BowItem) bow.getItem();
        AABB box = new AABB(player.blockPosition()).inflate(4);
        Set<AbstractArrow> before = new HashSet<>(helper.getLevel().getEntitiesOfClass(AbstractArrow.class, box));
        item.releaseUsing(bow, helper.getLevel(), player, item.getUseDuration(bow, player) - held);
        List<AbstractArrow> after = helper.getLevel().getEntitiesOfClass(AbstractArrow.class, box, a -> !before.contains(a));
        if (after.size() != 1) throw helper.assertionException("expected one new arrow, found " + after.size());
        AbstractArrow arrow = after.getFirst();
        // it has said what it had to; it shouldn't strike the next one
        helper.runAfterDelay(1, arrow::discard);
        return arrow;
    }

    private static ShotRecord shot(GameTestHelper helper, AbstractArrow arrow) {
        ShotRecord shot = ((ShotHolder) arrow).fulldraw$shot();
        if (shot == null) throw helper.assertionException("a player's arrow has no shot record");
        return shot;
    }

    private static AbstractArrow loadAndFire(GameTestHelper helper, Player player, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        CrossbowItem item = (CrossbowItem) stack.getItem();
        item.onUseTick(helper.getLevel(), player, stack, item.getUseDuration(stack, player) - CrossbowItem.getChargeDuration(stack, player));
        if (!CrossbowItem.isCharged(stack)) throw helper.assertionException("the crossbow didn't load");
        AABB box = new AABB(player.blockPosition()).inflate(4);
        Set<AbstractArrow> before = new HashSet<>(helper.getLevel().getEntitiesOfClass(AbstractArrow.class, box));
        item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        List<AbstractArrow> after = helper.getLevel().getEntitiesOfClass(AbstractArrow.class, box, a -> !before.contains(a));
        if (after.size() != 1) throw helper.assertionException("expected one new bolt, found " + after.size());
        return after.getFirst();
    }

    /** A bolt flying straight at the far wall, with the bounces a Ricochet crossbow would give it. */
    private static Arrow bolt(GameTestHelper helper, double x, int bounces) {
        Arrow arrow = EntityTypes.ARROW.create(helper.getLevel(), EntitySpawnReason.COMMAND);
        arrow.setPos(helper.absoluteVec(new Vec3(x, 2.5, 3.5)));
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(0, 0, 2.0);
        ((RicochetBolt) arrow).fulldraw$setRicochets(bounces);
        helper.getLevel().addFreshEntity(arrow);
        return arrow;
    }

    private static Zombie zombie(GameTestHelper helper, double x) {
        Zombie z = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(x, 1, 6.5));
        z.setBaby(false);
        // test worlds are in daylight: a burning zombie would take fire damage on top of the arrow
        z.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200));
        for (EquipmentSlot slot : EquipmentSlot.values()) z.setItemSlot(slot, ItemStack.EMPTY);
        return z;
    }

    /** A plain arrow at a zombie, its record carrying a bow's Deadeye bonus. */
    private static void fire(GameTestHelper helper, double x, double y, double deadeye) {
        Arrow arrow = EntityTypes.ARROW.create(helper.getLevel(), EntitySpawnReason.COMMAND);
        Vec3 from = helper.absoluteVec(new Vec3(x, y, 1.5));
        arrow.setPos(from);
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(0, 0, 3.0);
        arrow.setBaseDamage(2.0);
        arrow.setCritArrow(false);
        ((ShotHolder) arrow).fulldraw$setShot(new ShotRecord(false, from, 1.0, 1.0, 0.0, BuiltInRegistries.ITEM.getKey(Items.BOW), deadeye));
        helper.getLevel().addFreshEntity(arrow);
    }
}
