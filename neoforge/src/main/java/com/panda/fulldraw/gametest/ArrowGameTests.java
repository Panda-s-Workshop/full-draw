package com.panda.fulldraw.gametest;

import com.panda.fulldraw.entity.BodkinArrow;
import com.panda.fulldraw.entity.BroadheadArrow;
import com.panda.fulldraw.registry.ModEffects;
import com.panda.fulldraw.registry.ModItems;
import com.panda.fulldraw.shot.ShotHolder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.phys.AABB;
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
     * and the unarmoured husk bleeds 1 a second for 4 seconds: 11.5 in all. A skeleton takes the hit but doesn't bleed.
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
