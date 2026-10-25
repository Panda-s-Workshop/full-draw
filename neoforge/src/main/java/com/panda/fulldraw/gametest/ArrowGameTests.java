package com.panda.fulldraw.gametest;

import com.panda.fulldraw.entity.BodkinArrow;
import com.panda.fulldraw.FullDrawConfig;
import com.panda.fulldraw.block.FlareLightBlock;
import com.panda.fulldraw.block.RopeBlock;
import com.panda.fulldraw.entity.BlastArrow;
import com.panda.fulldraw.entity.BogLurker;
import com.panda.fulldraw.entity.BroadheadArrow;
import com.panda.fulldraw.entity.FlareArrow;
import com.panda.fulldraw.entity.GaleHarrier;
import com.panda.fulldraw.entity.RopeArrow;
import com.panda.fulldraw.entity.StuckArrows;
import com.panda.fulldraw.registry.ModBlocks;
import com.panda.fulldraw.registry.ModEffects;
import com.panda.fulldraw.registry.ModEntities;
import com.panda.fulldraw.logic.ArrowTraits;
import com.panda.fulldraw.registry.ModItems;
import com.panda.fulldraw.shot.ShotHolder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The arrow heads on real hits. Each arrow is made by its item, as a bow makes it, and fired level at 3 blocks a tick,
 * so vanilla's damage before the head is 6 (ceil(speed × 2)).
 */
final class ArrowGameTests {
    private ArrowGameTests() {}

    /** A bow draws a broadhead from the inventory and fires a broadhead with a shot record; a dispenser fires a bodkin. */
    static void arrowsFireFromBowAndDispenser(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
        player.snapTo(at.x, at.y, at.z, 0, -60);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
        player.getInventory().setItem(9, new ItemStack(ModItems.BROADHEAD_ARROW.get(), 4));
        ItemStack bow = player.getMainHandItem();
        BowItem item = (BowItem) bow.getItem();
        AABB box = new AABB(player.blockPosition()).inflate(4);
        Set<AbstractArrow> before = new HashSet<>(helper.getLevel().getEntitiesOfClass(AbstractArrow.class, box));
        item.releaseUsing(bow, helper.getLevel(), player, item.getUseDuration(bow, player) - 23);
        List<AbstractArrow> shot = helper.getLevel().getEntitiesOfClass(AbstractArrow.class, box, a -> !before.contains(a));
        if (shot.size() != 1 || !(shot.getFirst() instanceof BroadheadArrow broadhead))
            throw helper.assertionException("the bow should fire one broadhead from the inventory: " + shot);
        if (((ShotHolder) broadhead).fulldraw$shot() == null) throw helper.assertionException("a drawn broadhead should carry a shot record");
        if (player.getInventory().getItem(9).getCount() != 3) throw helper.assertionException("the bow should take one broadhead");

        BlockPos dispenser = new BlockPos(6, 1, 2);
        helper.setBlock(dispenser, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.SOUTH));
        helper.getBlockEntity(dispenser, DispenserBlockEntity.class).setItem(0, new ItemStack(ModItems.BODKIN_ARROW.get()));
        helper.pulseRedstone(dispenser.above(), 2);
        helper.runAfterDelay(10, () -> {
            var fired = helper.getLevel().getEntitiesOfClass(BodkinArrow.class, new AABB(helper.absolutePos(dispenser)).inflate(8));
            if (fired.size() != 1) helper.fail("the dispenser should fire one bodkin, found " + fired.size());
            else if (fired.getFirst().pickup != AbstractArrow.Pickup.ALLOWED) helper.fail("a dispensed bodkin should be picked up again");
            else helper.succeed();
        });
    }

    /**
     * A broadhead does ×1.25 to a husk with no armour (7.5) and ×0.75 before armour to one with 10 points (3.105 after),
     * and the unarmoured husk bleeds 1 a second for 4 seconds: 11.5 in all; the plated one doesn't bleed. A skeleton
     * takes the hit but doesn't bleed.
     */
    static void broadheadBleeds(GameTestHelper helper) {
        LivingEntity bare = target(helper, 2.5, 0), plated = target(helper, 5.5, 10);
        var skeleton = helper.spawnWithNoFreeWill(EntityTypes.SKELETON, new Vec3(8.5, 1, 6.5));
        for (EquipmentSlot slot : EquipmentSlot.values()) skeleton.setItemSlot(slot, ItemStack.EMPTY);
        Player player = shooter(helper);
        fire(helper, player, ModItems.BROADHEAD_ARROW.get(), 2.5);
        fire(helper, player, ModItems.BROADHEAD_ARROW.get(), 5.5);
        fire(helper, player, ModItems.BROADHEAD_ARROW.get(), 8.5);
        helper.runAfterDelay(8, () -> {
            float bareLost = lost(bare), platedLost = lost(plated);
            if (Math.abs(bareLost - 7.5F) > 0.01F) helper.fail("a broadhead on a bare husk should do 6 × 1.25 = 7.5, not " + bareLost);
            else if (Math.abs(platedLost - 3.105F) > 0.01F) helper.fail("a broadhead on 10 armour should do 6 × 0.75 = 4.5 before armour, 3.105 after, not " + platedLost);
            else if (!bare.hasEffect(ModEffects.BLEEDING.holder())) helper.fail("the bare husk should be bleeding");
            else if (plated.hasEffect(ModEffects.BLEEDING.holder())) helper.fail("10 armour points should stop the bleed");
            else if (skeleton.hasEffect(ModEffects.BLEEDING.holder()) || lost(skeleton) <= 0) helper.fail("the skeleton should be hit and not bleed");
        });
        helper.runAfterDelay(95, () -> {
            float bareLost = lost(bare);
            if (Math.abs(bareLost - 11.5F) > 0.01F) helper.fail("7.5 from the hit and 4 from bleeding should make 11.5, not " + bareLost);
            else if (bare.hasEffect(ModEffects.BLEEDING.holder())) helper.fail("the bleeding should have stopped after 4 s");
            else helper.succeed();
        });
    }

    /**
     * Into 20 armour a bodkin does 4.8 before armour (×0.8) through 8 points (60% ignored): 3.725; a vanilla arrow's 6
     * through all 20 does 1.92. Unarmoured, the arrow wins: 6 to the bodkin's 4.8.
     */
    static void bodkinVsArmour(GameTestHelper helper) {
        LivingEntity bodkinPlated = target(helper, 2.5, 20), arrowPlated = target(helper, 4.5, 20);
        LivingEntity bodkinBare = target(helper, 6.5, 0), arrowBare = target(helper, 8.5, 0);
        Player player = shooter(helper);
        fire(helper, player, ModItems.BODKIN_ARROW.get(), 2.5);
        fire(helper, player, Items.ARROW, 4.5);
        fire(helper, player, ModItems.BODKIN_ARROW.get(), 6.5);
        fire(helper, player, Items.ARROW, 8.5);
        helper.runAfterDelay(8, () -> {
            float a = lost(bodkinPlated), b = lost(arrowPlated), c = lost(bodkinBare), d = lost(arrowBare);
            if (Math.abs(a - 3.7248F) > 0.01F) helper.fail("a bodkin into 20 armour should do 3.725, not " + a);
            else if (Math.abs(b - 1.92F) > 0.01F) helper.fail("a vanilla arrow into 20 armour should do 1.92, not " + b);
            else if (Math.abs(c - 4.8F) > 0.01F || Math.abs(d - 6.0F) > 0.01F) helper.fail("unarmoured: bodkin 4.8 and arrow 6, not " + c + " and " + d);
            else helper.succeed();
        });
    }

    /**
     * A blunt arrow into a creeper two-thirds of the way to exploding does exactly 1, puts its fuse out and shoves it
     * far harder than a vanilla arrow shoves a husk (vanilla's hurt knockback only).
     */
    static void bluntCancelsCreeper(GameTestHelper helper) {
        Creeper creeper = helper.spawnWithNoFreeWill(EntityTypes.CREEPER, new Vec3(2.5, 1, 6.5));
        LivingEntity control = target(helper, 5.5, 0);
        creeper.setSwellDir(1);
        Player player = shooter(helper);
        double[] push = new double[2];
        helper.runAfterDelay(18, () -> {
            fire(helper, player, ModItems.BLUNT_ARROW.get(), 2.5);
            fire(helper, player, Items.ARROW, 5.5);
        });
        for (int t = 19; t < 28; t++)
            helper.runAfterDelay(t, () -> {
                push[0] = Math.max(push[0], creeper.getDeltaMovement().horizontalDistance());
                push[1] = Math.max(push[1], control.getDeltaMovement().horizontalDistance());
            });
        helper.runAfterDelay(45, () -> {
            float lost = lost(creeper);
            if (!creeper.isAlive()) helper.fail("the creeper exploded: the blunt arrow should have put its fuse out");
            else if (Math.abs(lost - 1.0F) > 0.001F) helper.fail("a blunt arrow should do exactly 1, not " + lost);
            else if (creeper.getSwelling(1.0F) > 0.0F) helper.fail("the creeper should have no swell left, has " + creeper.getSwelling(1.0F));
            else if (push[0] < 1.0 || push[0] < 2 * push[1]) helper.fail("the blunt arrow should shove hard: " + push[0] + " against a plain arrow's " + push[1]);
            else helper.succeed();
        });
    }

    /**
     * Through the shot pipeline (arrows with a Perfect shot record, 10 blocks from their release): a Perfect broadhead
     * on a bare husk does 6 × min(2.5, 1.5 Perfect × 1.25 head) = 11.25; a Perfect headshot broadhead stops at the cap,
     * 6 × 2.5 = 15 (not 6 × 2.25 × 1.25 = 16.9), where a Perfect headshot arrow does 6 × 2.25 = 13.5; a Perfect blunt
     * does 1.
     */
    static void arrowHeadsInThePipeline(GameTestHelper helper) {
        LivingEntity body = target(helper, 2.5, 0), head = target(helper, 5.5, 0), plain = target(helper, 8.5, 0), blunt = target(helper, 11.5, 0);
        Player player = shooter(helper);
        recorded(helper, player, ModItems.BROADHEAD_ARROW.get(), 2.5, 2.0);
        recorded(helper, player, ModItems.BROADHEAD_ARROW.get(), 5.5, 2.75);
        recorded(helper, player, Items.ARROW, 8.5, 2.75);
        recorded(helper, player, ModItems.BLUNT_ARROW.get(), 11.5, 2.0);
        helper.runAfterDelay(8, () -> {
            float a = lost(body), b = lost(head), c = lost(plain), d = lost(blunt);
            if (Math.abs(a - 11.25F) > 0.01F) helper.fail("a Perfect broadhead body shot should do 6 × 1.875 = 11.25, not " + a);
            else if (Math.abs(b - 15.0F) > 0.01F) helper.fail("a Perfect headshot broadhead should stop at the cap, 15, not " + b);
            else if (Math.abs(c - 13.5F) > 0.01F) helper.fail("a Perfect headshot arrow should do 6 × 2.25 = 13.5, not " + c);
            else if (Math.abs(d - 1.0F) > 0.001F) helper.fail("a Perfect blunt should still do 1, not " + d);
            else helper.succeed();
        });
    }

    /**
     * A skeleton drawing its bow lets the draw go when a blunt arrow hits it, even one turned away by the damage
     * cooldown of a harder hit 4 ticks before. A skeleton hit by plain arrows keeps drawing.
     */
    static void bluntInterruptsArcher(GameTestHelper helper) {
        Mob hit = archer(helper, 2.5), control = archer(helper, 5.5);
        Player player = shooter(helper);
        fire(helper, player, Items.ARROW, 2.5);
        fire(helper, player, Items.ARROW, 5.5);
        helper.runAfterDelay(4, () -> {
            fire(helper, player, ModItems.BLUNT_ARROW.get(), 2.5);
            fire(helper, player, Items.ARROW, 5.5);
        });
        helper.runAfterDelay(14, () -> {
            if (hit.isUsingItem()) helper.fail("the skeleton a blunt arrow hit inside its damage cooldown should have let its draw go");
            else if (!control.isUsingItem()) helper.fail("the skeleton hit by plain arrows should still be drawing");
            else helper.succeed();
        });
    }

    /**
     * A blunt arrow breaks off the encounters' own wind-ups: a Gale Harrier shrieking before its dive circles again, and
     * a Bog Lurker swelling for a spit lets it go. Each is held still in its tell.
     */
    static void bluntBreaksOffTells(GameTestHelper helper) {
        Player player = shooter(helper);
        GaleHarrier harrier = helper.spawn(ModEntities.GALE_HARRIER.get(), new Vec3(2.5, 3.0, 8.5));
        harrier.setNoAi(true);
        harrier.startTell(helper.getLevel());
        BogLurker lurker = helper.spawn(ModEntities.BOG_LURKER.get(), new Vec3(6.5, 1.0, 8.5));
        lurker.setNoAi(true);
        lurker.startTell();
        shot(helper, player, ModItems.BLUNT_ARROW.get(), new Vec3(2.5, 3.0 + harrier.getBbHeight() * 0.5, 8.5 - 2.4), new Vec3(0.0, 0.0, 3.0));
        shot(helper, player, ModItems.BLUNT_ARROW.get(), new Vec3(6.5, 1.0 + lurker.getBbHeight() * 0.5, 8.5 - 2.4), new Vec3(0.0, 0.0, 3.0));
        helper.runAfterDelay(6, () -> {
            if (harrier.getHealth() >= harrier.getMaxHealth() || lurker.getHealth() >= lurker.getMaxHealth())
                helper.fail("both blunt arrows should hit: harrier " + harrier.getHealth() + ", lurker " + lurker.getHealth());
            else if (harrier.phase() != GaleHarrier.Phase.CIRCLE) helper.fail("the harrier's shriek should be broken off, it is " + harrier.phase().id());
            else if (lurker.phase() != BogLurker.Phase.LURK) helper.fail("the lurker's spit should be broken off, it is " + lurker.phase().id());
            else helper.succeed();
        });
    }

    /** A skeleton at x 6 blocks down the arena, a helmet against the sun, drawing a bow. */
    private static Mob archer(GameTestHelper helper, double x) {
        Mob skeleton = helper.spawnWithNoFreeWill(EntityTypes.SKELETON, new Vec3(x, 1, 6.5));
        skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        skeleton.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
        // handing a skeleton a bow gives it its bow goal back, which would start a let-go draw over at once
        skeleton.removeFreeWill();
        skeleton.startUsingItem(InteractionHand.MAIN_HAND);
        return skeleton;
    }

    /** As {@link #fire}, but the arrow carries a Perfect shot record from 10 blocks back, at height y. */
    private static void recorded(GameTestHelper helper, Player player, net.minecraft.world.item.Item ammo, double x, double y) {
        ItemStack stack = new ItemStack(ammo);
        AbstractArrow arrow = ((ArrowItem) ammo).createArrow(helper.getLevel(), stack, player, null);
        Vec3 from = helper.absoluteVec(new Vec3(x, y, 2.5));
        arrow.setPos(from.x, from.y, from.z);
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(0, 0, 3.0);
        arrow.setBaseDamage(2.0);
        arrow.setCritArrow(false);
        ((ShotHolder) arrow).fulldraw$setShot(new com.panda.fulldraw.shot.ShotRecord(true, from.add(0, 0, -10), 1.0, 1.0, 0.0,
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(Items.BOW)));
        helper.getLevel().addFreshEntity(arrow);
    }

    /**
     * A Rope Arrow into the side of a wall 5 blocks up ties a rope there that hangs to the floor (a knot over four more),
     * climbable, hanging against the wall, and the arrow is spent; one shot up into a block's underside hangs a rope in
     * the middle of the block below it; one shot down into the floor's top stays an arrow and hangs nothing.
     */
    static void ropeArrowPlacesRope(GameTestHelper helper) {
        for (int x = 2; x <= 4; x++)
            for (int y = 1; y <= 6; y++) helper.setBlock(new BlockPos(x, y, 9), Blocks.STONE);
        Player player = shooter(helper);
        RopeArrow wall = rope(helper, player, new Vec3(3.5, 5.5, 2.5), new Vec3(0, 0, 3.0));
        RopeArrow floor = rope(helper, player, new Vec3(7.5, 4.0, 4.5), new Vec3(0, -3.0, 0));
        helper.setBlock(new BlockPos(9, 6, 2), Blocks.STONE);
        rope(helper, player, new Vec3(9.5, 2.0, 2.5), new Vec3(0, 3.0, 0));
        helper.runAfterDelay(10, () -> {
            for (int y = 1; y <= 5; y++) {
                var state = helper.getBlockState(new BlockPos(3, y, 8));
                if (!state.is(ModBlocks.ROPE.get())) throw helper.assertionException("no rope at y " + y + " under the arrow: " + state);
                if (state.getValue(RopeBlock.KNOT) != (y == 5)) throw helper.assertionException("only the top rope is the knot, y " + y);
                if (!state.is(BlockTags.CLIMBABLE)) throw helper.assertionException("the rope should be climbable");
                if (state.getValue(RopeBlock.TIED) != Direction.SOUTH) throw helper.assertionException("the rope should hang against the wall: " + state);
            }
            if (helper.getBlockState(new BlockPos(3, 6, 8)).is(ModBlocks.ROPE.get())) throw helper.assertionException("rope above the knot");
            for (int y = 1; y <= 5; y++) {
                var state = helper.getBlockState(new BlockPos(9, y, 2));
                if (!state.is(ModBlocks.ROPE.get()) || state.getValue(RopeBlock.TIED) != Direction.UP)
                    throw helper.assertionException("an arrow into a ceiling should hang a rope in the middle, y " + y + ": " + state);
            }
            if (!wall.isRemoved()) throw helper.assertionException("the arrow that hung the rope should be spent");
            if (floor.isRemoved()) throw helper.assertionException("an arrow in the floor's top should stay to be picked up");
            if (helper.getBlockState(new BlockPos(7, 1, 4)).is(ModBlocks.ROPE.get())) throw helper.assertionException("an arrow in the floor hung rope");
            helper.succeed();
        });
    }

    /**
     * A rope tied under a ceiling and stopped by a block above the floor; with the block gone, a rope used on the rope
     * adds one at the bottom; a player breaking the knot takes every rope below with it, all four as items. Lose the
     * ceiling and a hanging rope falls, one item a rope. Collecting stops at another knot (a rope tied to a wall right
     * below keeps hanging), a creative player's break drops nothing, and rope burns.
     */
    static void ropeComesDownTogether(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 6, 3), Blocks.STONE);
        helper.setBlock(new BlockPos(3, 2, 3), Blocks.STONE);
        int hung = ModBlocks.ROPE.get().hang(helper.getLevel(), helper.absolutePos(new BlockPos(3, 5, 3)), Direction.UP);
        if (hung != 3) throw helper.assertionException("a rope from y 5 should stop on the block at y 2: " + hung);
        helper.setBlock(new BlockPos(3, 2, 3), Blocks.AIR);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack coil = new ItemStack(ModItems.ROPE.get(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, coil);
        BlockPos top = helper.absolutePos(new BlockPos(3, 5, 3));
        var result = helper.getLevel().getBlockState(top).useItemOn(coil, helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(top), Direction.NORTH, top, false));
        if (!result.consumesAction() || !helper.getBlockState(new BlockPos(3, 2, 3)).is(ModBlocks.ROPE.get()))
            throw helper.assertionException("a rope used on the rope should hang one more at the bottom: " + result);
        if (coil.getCount() != 1) throw helper.assertionException("hanging a rope by hand should use one: " + coil.getCount());

        ServerPlayer breaker = helper.makeMockServerPlayerInLevel();
        breaker.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        breaker.gameMode.destroyBlock(top);
        for (int y = 2; y <= 5; y++)
            if (!helper.getBlockState(new BlockPos(3, y, 3)).isAir()) throw helper.assertionException("rope left at y " + y + " after breaking the knot");

        helper.setBlock(new BlockPos(8, 6, 8), Blocks.STONE);
        int second = ModBlocks.ROPE.get().hang(helper.getLevel(), helper.absolutePos(new BlockPos(8, 5, 8)), Direction.UP);
        helper.setBlock(new BlockPos(8, 6, 8), Blocks.AIR);

        // a rope under a ceiling stopped at y 3; then a rope tied to a wall at y 3, right below it
        helper.setBlock(new BlockPos(10, 6, 3), Blocks.STONE);
        helper.setBlock(new BlockPos(10, 3, 3), Blocks.STONE);
        ModBlocks.ROPE.get().hang(helper.getLevel(), helper.absolutePos(new BlockPos(10, 5, 3)), Direction.UP);
        helper.setBlock(new BlockPos(10, 3, 3), Blocks.AIR);
        helper.setBlock(new BlockPos(10, 3, 2), Blocks.STONE);
        ModBlocks.ROPE.get().hang(helper.getLevel(), helper.absolutePos(new BlockPos(10, 3, 3)), Direction.NORTH);
        breaker.gameMode.destroyBlock(helper.absolutePos(new BlockPos(10, 5, 3)));
        for (int y = 1; y <= 3; y++)
            if (!helper.getBlockState(new BlockPos(10, y, 3)).is(ModBlocks.ROPE.get())) throw helper.assertionException("collecting went past another knot, y " + y);
        if (!helper.getBlockState(new BlockPos(10, 4, 3)).isAir()) throw helper.assertionException("the rope below the broken knot is still there");
        ServerPlayer builder = helper.makeMockServerPlayerInLevel();
        builder.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
        builder.gameMode.destroyBlock(helper.absolutePos(new BlockPos(10, 3, 3)));
        if (!helper.getBlockState(new BlockPos(10, 1, 3)).isAir()) throw helper.assertionException("a creative break should take the rope below too");
        int burns = ((net.minecraft.world.level.block.FireBlock) Blocks.FIRE).getBurnOdds(ModBlocks.ROPE.get().defaultBlockState());
        if (burns <= 0) throw helper.assertionException("rope should burn");
        helper.runAfterDelay(5, () -> {
            int separate = ropes(helper, new BlockPos(10, 3, 3));
            int knot = ropes(helper, new BlockPos(3, 3, 3)), fell = ropes(helper, new BlockPos(8, 3, 8));
            if (separate != 2) helper.fail("the survival break should drop its own 2 and the creative one none, found " + separate + ": " + drops(helper));
            else if (knot != 4) helper.fail("breaking the knot should drop all four ropes, dropped " + knot + ": " + drops(helper));
            else if (second != 5 || fell != 5) helper.fail("a rope losing its ceiling should fall, one item a rope: hung " + second + ", dropped " + fell);
            else if (!helper.getBlockState(new BlockPos(8, 1, 8)).isAir()) helper.fail("the fallen rope is still there");
            else helper.succeed();
        });
    }

    /**
     * Rope Arrows into a wall, five lanes: a survival player's hangs a rope and earns Rope Trick; a Multishot copy, one
     * into a spot already taken (a light block), one into a fence's side (not solid) and an Adventure player's each stay
     * an arrow and hang nothing.
     */
    static void ropeArrowRefusals(GameTestHelper helper) {
        for (int x = 2; x <= 10; x++)
            for (int y = 1; y <= 6; y++) helper.setBlock(new BlockPos(x, y, 9), Blocks.STONE);
        helper.setBlock(new BlockPos(1, 4, 9), Blocks.OAK_FENCE);
        helper.setBlock(new BlockPos(7, 4, 8), Blocks.LIGHT);
        ServerPlayer builder = helper.makeMockServerPlayerInLevel(), visitor = helper.makeMockServerPlayerInLevel();
        builder.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        visitor.gameMode.changeGameModeForPlayer(GameType.ADVENTURE);
        Player archer = helper.makeMockPlayer(GameType.SURVIVAL);
        RopeArrow paid = rope(helper, builder, new Vec3(3.5, 4.5, 2.5), new Vec3(0, 0, 3.0));
        ItemStack copy = new ItemStack(ModItems.ROPE_ARROW.get());
        copy.set(net.minecraft.core.component.DataComponents.INTANGIBLE_PROJECTILE, net.minecraft.util.Unit.INSTANCE);
        List<RopeArrow> kept = List.of(rope(helper, archer, copy, new Vec3(5.5, 4.5, 2.5), new Vec3(0, 0, 3.0)),
                rope(helper, archer, new Vec3(7.5, 4.5, 2.5), new Vec3(0, 0, 3.0)),
                rope(helper, archer, new Vec3(1.5, 4.5, 2.5), new Vec3(0, 0, 3.0)),
                rope(helper, visitor, new Vec3(9.5, 4.5, 2.5), new Vec3(0, 0, 3.0)));
        helper.runAfterDelay(10, () -> {
            for (int y = 1; y <= 4; y++)
                if (!helper.getBlockState(new BlockPos(3, y, 8)).is(ModBlocks.ROPE.get())) throw helper.assertionException("the survival player's arrow hung no rope at y " + y);
            if (!paid.isRemoved()) throw helper.assertionException("the arrow that hung the rope should be spent");
            var trick = helper.getLevel().getServer().getAdvancements().get(com.panda.fulldraw.FullDrawMod.id("rope_trick"));
            if (trick == null || !builder.getAdvancements().getOrStartProgress(trick).isDone()) throw helper.assertionException("hanging a rope should earn Rope Trick");
            String[] why = {"a Multishot copy", "an arrow into a taken spot", "an arrow into a fence", "an Adventure player's arrow"};
            int[] lane = {5, 7, 1, 9};
            for (int i = 0; i < lane.length; i++) {
                for (int y = 1; y <= 5; y++)
                    if (helper.getBlockState(new BlockPos(lane[i], y, 8)).is(ModBlocks.ROPE.get())) throw helper.assertionException(why[i] + " hung rope");
                if (kept.get(i).isRemoved()) throw helper.assertionException(why[i] + " should stay an arrow");
            }
            helper.succeed();
        });
    }

    /**
     * A Flare Arrow shot down into the floor near a corner lights the block it lands in (15, ten steps of 3 s, so 30 s;
     * another flare there tops it back up) and spends the arrow; a husk 3 blocks off and one 7 off glow, one 9 off doesn't.
     * A second flare into a husk lights the spot where it struck.
     */
    static void flareLightsAndGlows(GameTestHelper helper) {
        LivingEntity near = husk(helper, 4.5, 1.5), mid = husk(helper, 1.5, 8.5), far = husk(helper, 10.5, 10.5), distant = husk(helper, 10.5, 0.5);
        Player player = shooter(helper);
        FlareArrow flare = (FlareArrow) shot(helper, player, ModItems.FLARE_ARROW.get(), new Vec3(1.5, 4.0, 1.5), new Vec3(0, -3.0, 0));
        // and one into the far husk: a flare that hits a creature lights the spot too
        shot(helper, player, ModItems.FLARE_ARROW.get(), new Vec3(10.5, 1.5, 6.5), new Vec3(0, 0, 3.0));
        BlockPos lit = new BlockPos(1, 1, 1);
        helper.runAfterDelay(5, () -> {
            int byHusk = 0;
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(9, 1, 8), new BlockPos(11, 3, 11)))
                if (helper.getBlockState(pos).is(ModBlocks.FLARE_LIGHT.get())) byHusk++;
            if (byHusk != 1) throw helper.assertionException("a flare into a husk should light the spot it struck: " + byHusk + " lights; " + drops(helper));
            var state = helper.getBlockState(lit);
            if (!state.is(ModBlocks.FLARE_LIGHT.get())) throw helper.assertionException("no flare light where the flare landed: " + state);
            if (state.getLightEmission() != 15 || state.getValue(FlareLightBlock.BURN) != FlareLightBlock.STEPS)
                throw helper.assertionException("a fresh flare should light 15 with its whole time to burn: " + state);
            BlockPos abs = helper.absolutePos(lit);
            if (!helper.getLevel().getBlockTicks().hasScheduledTick(abs, ModBlocks.FLARE_LIGHT.get())) throw helper.assertionException("the flare isn't counting down");
            if (!flare.isRemoved()) throw helper.assertionException("the flare arrow should be spent on landing");
            if (!near.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING) || !mid.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING))
                throw helper.assertionException("husks within 8 blocks should glow");
            if (lost(far) <= 0) throw helper.assertionException("the second flare should have struck the far husk");
            if (distant.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING)) throw helper.assertionException("a husk 9 blocks from either flare should not glow");
            int glow = near.getEffect(net.minecraft.world.effect.MobEffects.GLOWING).getDuration();
            if (glow > 200 || glow < 190) throw helper.assertionException("the glow should last 10 s: " + glow);
            // three steps burn down, then another flare lands there: back to the full 30 s
            for (int i = 0; i < 3; i++) helper.getLevel().getBlockState(abs).tick(helper.getLevel(), abs, helper.getLevel().getRandom());
            if (helper.getBlockState(lit).getValue(FlareLightBlock.BURN) != FlareLightBlock.STEPS - 3) throw helper.assertionException("the flare should burn down a step a tick call");
            ModBlocks.FLARE_LIGHT.get().light(helper.getLevel(), abs);
            if (helper.getBlockState(lit).getValue(FlareLightBlock.BURN) != FlareLightBlock.STEPS) throw helper.assertionException("another flare should top the light up");
            for (int i = 0; i < FlareLightBlock.STEPS; i++) helper.getLevel().getBlockState(abs).tick(helper.getLevel(), abs, helper.getLevel().getRandom());
            if (!helper.getBlockState(lit).isAir()) throw helper.assertionException("the flare should be out after its ten steps: " + helper.getBlockState(lit));
            helper.succeed();
        });
    }

    /**
     * A Blast Arrow into a glass wall beside a husk explodes there: the husk is hurt and the arrow spent, and with the
     * server's default no glass breaks. Then, with {@code blastArrowBreaksBlocks} on, a second one breaks the glass; a
     * third, with {@code tnt_explodes} off, doesn't, nor does a husk's with {@code mobGriefing} off.
     */
    static void blastArrowExplodes(GameTestHelper helper) {
        for (int x = 1; x <= 10; x++)
            for (int y = 1; y <= 4; y++) helper.setBlock(new BlockPos(x, y, 9), Blocks.GLASS);
        LivingEntity husk = husk(helper, 3.5, 7.5);
        Player player = shooter(helper);
        BlastArrow first = (BlastArrow) shot(helper, player, ModItems.BLAST_ARROW.get(), new Vec3(2.5, 2.5, 4.5), new Vec3(0, 0, 3.0));
        helper.runAfterDelay(5, () -> {
            if (!first.isRemoved()) throw helper.assertionException("the blast arrow should be spent");
            // the arrow flies past the husk into the glass: anything it lost, the explosion did
            if (lost(husk) <= 0) throw helper.assertionException("the explosion should hurt the husk beside it");
            for (int x = 1; x <= 10; x++)
                for (int y = 1; y <= 4; y++)
                    if (!helper.getBlockState(new BlockPos(x, y, 9)).is(Blocks.GLASS)) throw helper.assertionException("a blast arrow broke glass by default at " + x + "," + y);
            FullDrawConfig.Source before = FullDrawConfig.source();
            FullDrawConfig.use(new FullDrawConfig.Source() {
                @Override public boolean vanillaCritOnFullDraw() { return before.vanillaCritOnFullDraw(); }
                @Override public com.panda.fulldraw.logic.ShotMath.Tuning tuning() { return before.tuning(); }
                @Override public boolean blastArrowBreaksBlocks() { return true; }
                @Override public int ropeArrowLength() { return before.ropeArrowLength(); }
                @Override public double arrowRecoveryChance() { return before.arrowRecoveryChance(); }
                @Override public int arrowPickupLifetimeSeconds() { return before.arrowPickupLifetimeSeconds(); }
                @Override public boolean shieldbearerSpawns() { return before.shieldbearerSpawns(); }
                @Override public boolean galeHarrierSpawns() { return before.galeHarrierSpawns(); }
                @Override public boolean bogLurkerSpawns() { return before.bogLurkerSpawns(); }
            });
            shot(helper, player, ModItems.BLAST_ARROW.get(), new Vec3(8.5, 2.5, 4.5), new Vec3(0, 0, 3.0));
            var rules = helper.getLevel().getGameRules();
            var server = helper.getLevel().getServer();
            helper.runAfterDelay(5, () -> {
                if (helper.getBlockState(new BlockPos(8, 2, 9)).is(Blocks.GLASS)) {
                    FullDrawConfig.use(before);
                    throw helper.assertionException("with blastArrowBreaksBlocks on, the glass it struck should break");
                }
                rules.set(GameRules.TNT_EXPLODES, false, server);
                rules.set(GameRules.MOB_GRIEFING, false, server);
                shot(helper, player, ModItems.BLAST_ARROW.get(), new Vec3(2.5, 3.5, 4.5), new Vec3(0, 0, 3.0));
                LivingEntity archer = husk(helper, 4.5, 1.5);
                shot(helper, archer, new ItemStack(ModItems.BLAST_ARROW.get()), new Vec3(4.5, 3.5, 4.5), new Vec3(0, 0, 3.0));
                helper.runAfterDelay(5, () -> {
                    FullDrawConfig.use(before);
                    rules.set(GameRules.TNT_EXPLODES, true, server);
                    rules.set(GameRules.MOB_GRIEFING, true, server);
                    if (!helper.getBlockState(new BlockPos(2, 3, 9)).is(Blocks.GLASS)) helper.fail("with tnt_explodes off, a player's blast arrow broke glass");
                    else if (!helper.getBlockState(new BlockPos(4, 3, 9)).is(Blocks.GLASS)) helper.fail("with mobGriefing off, a husk's blast arrow broke glass");
                    else helper.succeed();
                });
            });
        });
    }

    /**
     * A Blast Arrow that strikes a husk lands its own hit and then the explosion in full on top: the husk loses more than
     * the explosion alone does at its centre. A Multishot copy flies as a plain arrow: it sticks in the glass beside
     * another husk and doesn't go off.
     */
    static void blastHitAddsAndCopiesArePlain(GameTestHelper helper) {
        for (int x = 6; x <= 10; x++)
            for (int y = 1; y <= 4; y++) helper.setBlock(new BlockPos(x, y, 9), Blocks.GLASS);
        LivingEntity struck = husk(helper, 2.5, 6.5), beside = husk(helper, 8.5, 7.5);
        Player player = shooter(helper);
        shot(helper, player, ModItems.BLAST_ARROW.get(), new Vec3(2.5, 1.9, 2.5), new Vec3(0, 0, 3.0));
        ItemStack copy = new ItemStack(ModItems.BLAST_ARROW.get());
        copy.set(net.minecraft.core.component.DataComponents.INTANGIBLE_PROJECTILE, net.minecraft.util.Unit.INSTANCE);
        AbstractArrow plain = shot(helper, player, copy, new Vec3(7.5, 2.5, 4.5), new Vec3(0, 0, 3.0));
        helper.runAfterDelay(5, () -> {
            double explosion = ArrowTraits.blastDamage(0.0, 1.0);
            if (lost(struck) <= explosion + 0.5) helper.fail("a struck husk should take the arrow's hit and the explosion: lost " + lost(struck) + ", the explosion alone " + explosion);
            else if (plain.isRemoved() || lost(beside) > 0) helper.fail("a Multishot copy should fly as a plain arrow, not go off");
            else helper.succeed();
        });
    }

    /**
     * A flare falls through short grass and a thin layer of snow to the ground beneath and burns on top of them; in water
     * it lights nothing. A player standing by doesn't glow: a player's flare shows the mobs.
     */
    static void flareBurnsAboveCover(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 0, 3), Blocks.GRASS_BLOCK);
        helper.setBlock(new BlockPos(1, 1, 3), Blocks.SHORT_GRASS);
        helper.setBlock(new BlockPos(5, 1, 3), Blocks.SNOW);
        for (int x = 8; x <= 10; x++)
            for (int z = 2; z <= 4; z++) helper.setBlock(new BlockPos(x, 1, z), x == 9 && z == 3 ? Blocks.WATER : Blocks.GLASS);
        ServerPlayer bystander = helper.makeMockServerPlayerInLevel();
        Vec3 by = helper.absoluteVec(new Vec3(3.5, 1, 4.5));
        bystander.snapTo(by.x, by.y, by.z, 0, 0);
        Player player = shooter(helper);
        for (int x : new int[] {1, 5, 9})
            shot(helper, player, ModItems.FLARE_ARROW.get(), new Vec3(x + 0.5, 4.0, 3.5), new Vec3(0, -3.0, 0));
        helper.runAfterDelay(10, () -> {
            if (!helper.getBlockState(new BlockPos(1, 2, 3)).is(ModBlocks.FLARE_LIGHT.get()) || !helper.getBlockState(new BlockPos(1, 1, 3)).is(Blocks.SHORT_GRASS))
                helper.fail("a flare into short grass should burn above it, the grass left: " + helper.getBlockState(new BlockPos(1, 1, 3)) + " / " + helper.getBlockState(new BlockPos(1, 2, 3)));
            else if (!helper.getBlockState(new BlockPos(5, 2, 3)).is(ModBlocks.FLARE_LIGHT.get()))
                helper.fail("a flare into thin snow should burn above it: " + helper.getBlockState(new BlockPos(5, 1, 3)) + " / " + helper.getBlockState(new BlockPos(5, 2, 3)));
            else if (lights(helper, new BlockPos(8, 1, 2), new BlockPos(10, 5, 4)) != 0) helper.fail("a flare in water should light nothing");
            else if (bystander.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING)) helper.fail("a player's flare should not set a player glowing");
            else helper.succeed();
        });
    }

    /**
     * An Adventure player's flare lights nothing but still shows the mobs around. A husk's flare shows the player beside
     * it, not the husks.
     */
    static void flareAdventureAndMobFlares(GameTestHelper helper) {
        LivingEntity watcher = husk(helper, 4.5, 2.5);
        ServerPlayer adventurer = helper.makeMockServerPlayerInLevel();
        adventurer.gameMode.changeGameModeForPlayer(GameType.ADVENTURE);
        shot(helper, adventurer, new ItemStack(ModItems.FLARE_ARROW.get()), new Vec3(3.5, 4.0, 2.5), new Vec3(0, -3.0, 0));
        helper.runAfterDelay(5, () -> {
            if (lights(helper, new BlockPos(2, 1, 1), new BlockPos(4, 3, 3)) != 0) throw helper.assertionException("an Adventure player's flare should light nothing");
            if (!watcher.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING)) throw helper.assertionException("an Adventure player's flare should still show the mobs");
            watcher.removeAllEffects();
            LivingEntity archer = husk(helper, 8.5, 8.5);
            ServerPlayer target = helper.makeMockServerPlayerInLevel();
            Vec3 at = helper.absoluteVec(new Vec3(8.5, 1, 6.5));
            target.snapTo(at.x, at.y, at.z, 0, 0);
            shot(helper, archer, new ItemStack(ModItems.FLARE_ARROW.get()), new Vec3(7.5, 4.0, 7.5), new Vec3(0, -3.0, 0));
            helper.runAfterDelay(5, () -> {
                if (!target.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING)) helper.fail("a husk's flare should set the player near it glowing");
                else if (archer.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING) || watcher.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING))
                    helper.fail("a husk's flare should not set the husks glowing");
                else helper.succeed();
            });
        });
    }

    private static int lights(GameTestHelper helper, BlockPos from, BlockPos to) {
        int n = 0;
        for (BlockPos pos : BlockPos.betweenClosed(from, to))
            if (helper.getBlockState(pos).is(ModBlocks.FLARE_LIGHT.get())) n++;
        return n;
    }

    private static LivingEntity husk(GameTestHelper helper, double x, double z) {
        Mob husk = helper.spawnWithNoFreeWill(EntityTypes.HUSK, new Vec3(x, 1, z));
        for (EquipmentSlot slot : EquipmentSlot.values()) husk.setItemSlot(slot, ItemStack.EMPTY);
        return husk;
    }

    /** An arrow of this item from the player, at a relative point, moving without gravity. */
    private static AbstractArrow shot(GameTestHelper helper, Player player, net.minecraft.world.item.Item ammo, Vec3 at, Vec3 motion) {
        return shot(helper, player, new ItemStack(ammo), at, motion);
    }

    /** An arrow of this stack from this shooter, at a relative point, moving without gravity. */
    private static AbstractArrow shot(GameTestHelper helper, LivingEntity shooter, ItemStack ammo, Vec3 at, Vec3 motion) {
        AbstractArrow arrow = ((ArrowItem) ammo.getItem()).createArrow(helper.getLevel(), ammo, shooter, null);
        Vec3 from = helper.absoluteVec(at);
        arrow.setPos(from.x, from.y, from.z);
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(motion);
        helper.getLevel().addFreshEntity(arrow);
        return arrow;
    }

    private static RopeArrow rope(GameTestHelper helper, Player player, Vec3 at, Vec3 motion) {
        return rope(helper, player, new ItemStack(ModItems.ROPE_ARROW.get()), at, motion);
    }

    private static RopeArrow rope(GameTestHelper helper, Player player, ItemStack stack, Vec3 at, Vec3 motion) {
        RopeArrow arrow = (RopeArrow) ((ArrowItem) ModItems.ROPE_ARROW.get()).createArrow(helper.getLevel(), stack, player, null);
        Vec3 from = helper.absoluteVec(at);
        arrow.setPos(from.x, from.y, from.z);
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(motion);
        helper.getLevel().addFreshEntity(arrow);
        return arrow;
    }

    /**
     * Husks keep the arrows stuck in them and drop the paid-for ones back by their chance when they die, one husk per
     * case, each checked a tick after it dies with the items cleared between:
     * <ul>
     * <li>a Blunt that leaves its husk standing is kept (certain); a Flare after it is counted with nothing to give;</li>
     * <li>a Blunt that kills its husk comes back: the killing arrow counts;</li>
     * <li>400 plain arrows and 400 Broadheads (the husk's random seeded) come back near 40% and 25%;</li>
     * <li>an arrow no one can pick up (a Multishot or Infinity copy) gives nothing, and a tipped arrow keeps its potion;</li>
     * <li>a baby husk gives its arrows back, though it drops no loot; none come back with {@code doMobLoot} off or with
     * {@code arrowRecoveryChance} at 0;</li>
     * <li>as vanilla's count falls, the oldest arrows go first, the unpaid ones among them.</li>
     * </ul>
     */
    static void arrowsRecoveredOnKill(GameTestHelper helper) {
        Player player = shooter(helper);
        LivingEntity standing = target(helper, 2.5, 0), dying = target(helper, 5.5, 0), mixed = target(helper, 8.5, 0);
        dying.setHealth(1.0F);
        shot(helper, player, ModItems.BLUNT_ARROW.get(), new Vec3(2.5, 2.0, 4.5), new Vec3(0, 0, 3.0));
        shot(helper, player, ModItems.BLUNT_ARROW.get(), new Vec3(5.5, 2.0, 4.5), new Vec3(0, 0, 3.0));
        shot(helper, player, Items.ARROW, new Vec3(8.5, 2.0, 4.5), new Vec3(0, 0, 3.0)).pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        ItemStack tipped = net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.TIPPED_ARROW, net.minecraft.world.item.alchemy.Potions.SWIFTNESS);
        // past the first hits' damage cooldown, so these land too
        helper.runAfterDelay(15, () -> {
            shot(helper, player, ModItems.FLARE_ARROW.get(), new Vec3(2.5, 2.0, 4.5), new Vec3(0, 0, 3.0));
            shot(helper, player, tipped, new Vec3(8.5, 2.0, 4.5), new Vec3(0, 0, 3.0));
        });
        FullDrawConfig.Source before = FullDrawConfig.source();
        var rules = helper.getLevel().getGameRules();
        var server = helper.getLevel().getServer();
        Vec3[] died = new Vec3[1];
        helper.runAfterDelay(22, () -> {
            if (dying.isAlive()) throw helper.assertionException("a Blunt should kill a husk on its last point of health");
            // the only loose Blunt in the arena (the corpse slides and the item bounces, so not by where it lies)
            int back = arrowsNear(helper, helper.absoluteVec(new Vec3(5.5, 1, 6.5)), 16, ModItems.BLUNT_ARROW.get());
            if (back != 1) throw helper.assertionException("the Blunt that killed its husk should come back, " + back + " did: " + drops(helper));
            List<StuckArrows.Stuck> stuck = ((StuckArrows) standing).fulldraw$stuck();
            if (standing.getArrowCount() != 2 || stuck.size() != 2 || !stuck.get(0).arrow().is(ModItems.BLUNT_ARROW.get())
                    || stuck.get(0).recovery() != 1.0 || !stuck.get(1).arrow().isEmpty())
                throw helper.assertionException("the standing husk should count 2 arrows, the Blunt kept and the Flare empty: " + standing.getArrowCount() + " " + stuck);
            clearItems(helper);
            standing.setArrowCount(802);
            for (int i = 0; i < 400; i++) ((StuckArrows) standing).fulldraw$stick(new StuckArrows.Stuck(new ItemStack(Items.ARROW), ArrowTraits.PLAIN_RECOVERY));
            for (int i = 0; i < 400; i++) ((StuckArrows) standing).fulldraw$stick(new StuckArrows.Stuck(new ItemStack(ModItems.BROADHEAD_ARROW.get()), ArrowTraits.BROADHEAD.recovery()));
            standing.getRandom().setSeed(1234L);
            died[0] = standing.position();
            standing.kill(helper.getLevel());
        });
        helper.runAfterDelay(24, () -> {
            int blunts = arrowsNear(helper, died[0], ModItems.BLUNT_ARROW.get()), flares = arrowsNear(helper, died[0], ModItems.FLARE_ARROW.get()),
                    plain = arrowsNear(helper, died[0], Items.ARROW), broadheads = arrowsNear(helper, died[0], ModItems.BROADHEAD_ARROW.get());
            if (blunts != 1 || flares != 0) throw helper.assertionException("the Blunt should come back and no Flare: " + blunts + ", " + flares);
            // 40% of 400 is 160 and 25% is 100, each about ±10 at one standard deviation
            if (plain < 130 || plain > 190) throw helper.assertionException("near 160 of 400 plain arrows should come back, " + plain + " did");
            if (broadheads < 74 || broadheads > 126) throw helper.assertionException("near 100 of 400 Broadheads should come back, " + broadheads + " did");
            clearItems(helper);
            List<StuckArrows.Stuck> stuck = ((StuckArrows) mixed).fulldraw$stuck();
            if (stuck.size() != 2 || !stuck.get(0).arrow().isEmpty() || !ItemStack.isSameItemSameComponents(stuck.get(1).arrow(), tipped))
                throw helper.assertionException("the copy should be kept empty and the tipped arrow with its potion: " + stuck);
            FullDrawConfig.use(config(before, 1.0, before.arrowPickupLifetimeSeconds()));
            try {
                died[0] = mixed.position();
                mixed.kill(helper.getLevel());
            } finally {
                FullDrawConfig.use(before);
            }
        });
        helper.runAfterDelay(26, () -> {
            var items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(died[0], died[0]).inflate(2, 3, 2),
                    e -> e.getItem().is(net.minecraft.tags.ItemTags.ARROWS));
            if (items.size() != 1 || !ItemStack.isSameItemSameComponents(items.getFirst().getItem(), tipped))
                throw helper.assertionException("only the tipped arrow, with its potion, should come back: " + drops(helper));
            clearItems(helper);
            Mob baby = helper.spawnWithNoFreeWill(EntityTypes.HUSK, new Vec3(5.5, 1, 6.5));
            ((net.minecraft.world.entity.monster.zombie.Zombie) baby).setBaby(true);
            stick(baby, 3);
            died[0] = baby.position();
            baby.kill(helper.getLevel());
        });
        helper.runAfterDelay(28, () -> {
            int back = arrowsNear(helper, died[0], ModItems.BLUNT_ARROW.get());
            if (back != 3) throw helper.assertionException("a baby husk drops no loot but should give its 3 Blunts back, gave " + back);
            clearItems(helper);
            LivingEntity noLoot = target(helper, 5.5, 0);
            stick(noLoot, 3);
            rules.set(GameRules.MOB_DROPS, false, server);
            try {
                died[0] = noLoot.position();
                noLoot.kill(helper.getLevel());
            } finally {
                rules.set(GameRules.MOB_DROPS, true, server);
            }
        });
        helper.runAfterDelay(30, () -> {
            int back = arrowsNear(helper, died[0], ModItems.BLUNT_ARROW.get());
            if (back != 0) throw helper.assertionException("with doMobLoot off nothing should come back, " + back + " Blunts did");
            LivingEntity off = target(helper, 2.5, 0);
            stick(off, 3);
            FullDrawConfig.use(config(before, 0.0, before.arrowPickupLifetimeSeconds()));
            try {
                died[0] = off.position();
                off.kill(helper.getLevel());
            } finally {
                FullDrawConfig.use(before);
            }
        });
        helper.runAfterDelay(32, () -> {
            int back = arrowsNear(helper, died[0], ModItems.BLUNT_ARROW.get());
            if (back != 0) throw helper.assertionException("with arrowRecoveryChance 0 nothing should come back, " + back + " Blunts did");
            // a paid arrow, then two no one paid for: as the count falls to 2, the paid one (the oldest) goes
            LivingEntity trimmed = target(helper, 8.5, 0);
            trimmed.setArrowCount(3);
            StuckArrows list = (StuckArrows) trimmed;
            list.fulldraw$stick(new StuckArrows.Stuck(new ItemStack(ModItems.BLUNT_ARROW.get()), 1.0));
            list.fulldraw$stick(new StuckArrows.Stuck(ItemStack.EMPTY, 0.0));
            list.fulldraw$stick(new StuckArrows.Stuck(ItemStack.EMPTY, 0.0));
            trimmed.setArrowCount(2);
            if (list.fulldraw$stuck().size() != 2 || list.fulldraw$stuck().stream().anyMatch(s -> !s.arrow().isEmpty()))
                throw helper.assertionException("the oldest arrow should go first as the count falls: " + list.fulldraw$stuck());
            helper.succeed();
        });
    }

    /** Sets the creature's arrow count to {@code blunts} and keeps that many Blunts in it, as their hits would. */
    private static void stick(LivingEntity entity, int blunts) {
        entity.setArrowCount(blunts);
        for (int i = 0; i < blunts; i++) ((StuckArrows) entity).fulldraw$stick(new StuckArrows.Stuck(new ItemStack(ModItems.BLUNT_ARROW.get()), 1.0));
    }

    private static void clearItems(GameTestHelper helper) {
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(24)).forEach(e -> e.discard());
    }

    /**
     * With {@code arrowPickupLifetimeSeconds} at 1, a player's arrow in a block is gone after a second while a husk's
     * (which no one can pick up) stays for vanilla's minute.
     */
    static void arrowPickupLifetime(GameTestHelper helper) {
        for (int x = 1; x <= 6; x++) helper.setBlock(new BlockPos(x, 2, 9), Blocks.STONE);
        FullDrawConfig.Source before = FullDrawConfig.source();
        FullDrawConfig.use(config(before, before.arrowRecoveryChance(), 1));
        AbstractArrow players = shot(helper, shooter(helper), Items.ARROW, new Vec3(2.5, 2.5, 4.5), new Vec3(0, 0, 3.0));
        AbstractArrow husks = shot(helper, husk(helper, 5.5, 1.5), new ItemStack(Items.ARROW), new Vec3(5.5, 2.5, 4.5), new Vec3(0, 0, 3.0));
        helper.runAfterDelay(40, () -> {
            FullDrawConfig.use(before);
            if (players.pickup != AbstractArrow.Pickup.ALLOWED || husks.pickup == AbstractArrow.Pickup.ALLOWED)
                throw helper.assertionException("expected a pickable player's arrow and a husk's that isn't");
            if (husks.isRemoved() || husks.getDeltaMovement().lengthSqr() > 1.0E-6) throw helper.assertionException("the husk's arrow should still be in the stone");
            if (!players.isRemoved()) throw helper.assertionException("the player's arrow should be gone after its 1 s lifetime");
            helper.succeed();
        });
    }

    /** The loader's config with the recovery chance and pickup lifetime replaced. */
    private static FullDrawConfig.Source config(FullDrawConfig.Source before, double recovery, int lifetimeSeconds) {
        return new FullDrawConfig.Source() {
            @Override public boolean vanillaCritOnFullDraw() { return before.vanillaCritOnFullDraw(); }
            @Override public com.panda.fulldraw.logic.ShotMath.Tuning tuning() { return before.tuning(); }
            @Override public boolean blastArrowBreaksBlocks() { return before.blastArrowBreaksBlocks(); }
            @Override public int ropeArrowLength() { return before.ropeArrowLength(); }
            @Override public double arrowRecoveryChance() { return recovery; }
            @Override public int arrowPickupLifetimeSeconds() { return lifetimeSeconds; }
            @Override public boolean shieldbearerSpawns() { return before.shieldbearerSpawns(); }
            @Override public boolean galeHarrierSpawns() { return before.galeHarrierSpawns(); }
            @Override public boolean bogLurkerSpawns() { return before.bogLurkerSpawns(); }
        };
    }

    /** How many of this item lie within 2 blocks of an absolute point. */
    private static int arrowsNear(GameTestHelper helper, Vec3 at, net.minecraft.world.item.Item item) {
        return arrowsNear(helper, at, 2, item);
    }

    /** How many of this item lie within {@code reach} blocks (sideways) of an absolute point. */
    private static int arrowsNear(GameTestHelper helper, Vec3 at, double reach, net.minecraft.world.item.Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(reach, 3, reach),
                e -> e.getItem().is(item)).stream().mapToInt(e -> e.getItem().getCount()).sum();
    }

    /** Every rope item in the arena, where it lies, for a failure message. */
    private static String drops(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(16)).stream()
                .map(e -> e.getItem().getCount() + " " + e.getItem().getItem() + " at " + helper.relativeVec(e.position())).toList().toString();
    }

    /** Rope items lying within 1.5 blocks of the column through pos, 4 up or down. */
    private static int ropes(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(pos)).inflate(1.5, 4, 1.5),
                e -> e.getItem().is(ModItems.ROPE.get())).stream().mapToInt(e -> e.getItem().getCount()).sum();
    }

    /** A husk 6 blocks down the arena's z axis at x, its armour points set, nothing worn. */
    private static LivingEntity target(GameTestHelper helper, double x, double armour) {
        Mob husk = helper.spawnWithNoFreeWill(EntityTypes.HUSK, new Vec3(x, 1, 6.5));
        for (EquipmentSlot slot : EquipmentSlot.values()) husk.setItemSlot(slot, ItemStack.EMPTY);
        husk.getAttribute(Attributes.ARMOR).setBaseValue(armour);
        return husk;
    }

    private static Player shooter(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1, 1.5));
        player.snapTo(at.x, at.y, at.z, 0, 0);
        return player;
    }

    /** The arrow the item makes for this shooter, fired level along +z at x, at body height, 3 blocks a tick. */
    private static void fire(GameTestHelper helper, Player player, net.minecraft.world.item.Item ammo, double x) {
        ItemStack stack = new ItemStack(ammo);
        AbstractArrow arrow = ((ArrowItem) ammo).createArrow(helper.getLevel(), stack, player, null);
        Vec3 from = helper.absoluteVec(new Vec3(x, 2.0, 2.5));
        arrow.setPos(from.x, from.y, from.z);
        arrow.shoot(0, 0, 1, 3.0F, 0.0F);
        helper.getLevel().addFreshEntity(arrow);
    }

    private static float lost(LivingEntity entity) {
        return entity.getMaxHealth() - entity.getHealth();
    }
}
