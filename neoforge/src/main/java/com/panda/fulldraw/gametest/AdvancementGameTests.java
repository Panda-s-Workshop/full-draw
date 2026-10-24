package com.panda.fulldraw.gametest;

import com.panda.fulldraw.FullDrawMod;
import com.panda.fulldraw.shot.ShotHolder;
import com.panda.fulldraw.shot.ShotRecord;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** The advancements are earned by the mechanics they name. */
final class AdvancementGameTests {
    private AdvancementGameTests() {}

    /** A Perfect Release earns Perfect Release; an arrow of the player's in a zombie's head earns Right Between the Eyes. */
    static void sliceAdvancements(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 at = helper.absoluteVec(new Vec3(5.5, 1, 5.5));
        player.snapTo(at.x, at.y, at.z, 0, -60);
        ItemStack bow = new ItemStack(Items.BOW);
        player.setItemInHand(InteractionHand.MAIN_HAND, bow);
        player.getInventory().add(new ItemStack(Items.ARROW, 4));
        if (done(helper, player, "perfect_release")) helper.fail("Perfect Release awarded before any shot");
        BowItem item = (BowItem) Items.BOW;
        item.releaseUsing(bow, helper.getLevel(), player, item.getUseDuration(bow, player) - 23);
        if (!done(helper, player, "perfect_release")) helper.fail("a Perfect Release did not award Perfect Release");

        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(2.5, 1, 6.5));
        zombie.setBaby(false);
        Arrow arrow = EntityTypes.ARROW.create(helper.getLevel(), EntitySpawnReason.COMMAND);
        Vec3 from = helper.absoluteVec(new Vec3(2.5, 1.0 + 1.75, 2.5));
        arrow.setPos(from);
        arrow.setOwner(player);
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(0, 0, 3.0);
        ((ShotHolder) arrow).fulldraw$setShot(new ShotRecord(false, from, 1.0, 1.0, 0.0, BuiltInRegistries.ITEM.getKey(Items.BOW)));
        helper.getLevel().addFreshEntity(arrow);
        helper.runAfterDelay(10, () -> {
            if (zombie.getHealth() >= zombie.getMaxHealth()) helper.fail("the arrow missed the zombie");
            else if (!done(helper, player, "right_between_the_eyes")) helper.fail("a headshot did not award Right Between the Eyes");
            else helper.succeed();
        });
    }

    /**
     * A hit that deals no damage (here an invulnerable zombie) plays no hit feedback and earns
     * nothing. The arrow is a player's headshot that would otherwise award Right Between the Eyes.
     */
    static void blockedHitReportsNothing(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); // a creative player's damage pierces invulnerability
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(2.5, 1, 6.5));
        zombie.setBaby(false);
        zombie.setPermanentlyInvulnerable(true);
        Arrow arrow = EntityTypes.ARROW.create(helper.getLevel(), EntitySpawnReason.COMMAND);
        Vec3 from = helper.absoluteVec(new Vec3(2.5, 1.0 + 1.75, 2.5));
        arrow.setPos(from);
        arrow.setOwner(player);
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(0, 0, 3.0);
        ((ShotHolder) arrow).fulldraw$setShot(new ShotRecord(false, from, 1.0, 1.0, 0.0, BuiltInRegistries.ITEM.getKey(Items.BOW)));
        helper.getLevel().addFreshEntity(arrow);
        helper.runAfterDelay(10, () -> {
            if (zombie.getHealth() < zombie.getMaxHealth()) helper.fail("the invulnerable zombie took damage");
            else if (done(helper, player, "right_between_the_eyes")) helper.fail("a headshot that dealt nothing awarded Right Between the Eyes");
            else if (((ShotHolder) arrow).fulldraw$shot() != null) helper.fail("a deflected arrow must lose its shot record");
            else helper.succeed();
        });
    }

    /** An arrow a Breeze turns back loses its record, so it can't score for the archer. */
    static void breezeDeflectionDropsRecord(GameTestHelper helper) {
        helper.spawnWithNoFreeWill(EntityTypes.BREEZE, new Vec3(2.5, 1, 6.5));
        Arrow arrow = EntityTypes.ARROW.create(helper.getLevel(), EntitySpawnReason.COMMAND);
        Vec3 from = helper.absoluteVec(new Vec3(2.5, 1.6, 2.5));
        arrow.setPos(from);
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(0, 0, 2.0);
        ((ShotHolder) arrow).fulldraw$setShot(new ShotRecord(true, from, 1.0, 1.0, 0.0, BuiltInRegistries.ITEM.getKey(Items.BOW)));
        helper.getLevel().addFreshEntity(arrow);
        helper.runAfterDelay(6, () -> {
            if (arrow.getDeltaMovement().z > 0) helper.fail("the Breeze did not turn the arrow back: " + arrow.getDeltaMovement());
            else if (((ShotHolder) arrow).fulldraw$shot() != null) helper.fail("a deflected arrow kept its shot record");
            else helper.succeed();
        });
    }

    private static boolean done(GameTestHelper helper, ServerPlayer player, String id) {
        var holder = helper.getLevel().getServer().getAdvancements().get(FullDrawMod.id(id));
        if (holder == null) throw helper.assertionException("advancement fulldraw:" + id + " is not loaded");
        return player.getAdvancements().getOrStartProgress(holder).isDone();
    }
}
