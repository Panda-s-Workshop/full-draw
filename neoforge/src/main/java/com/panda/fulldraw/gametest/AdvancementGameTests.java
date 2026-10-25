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

    /**
     * Long Shot needs a hit from 50 blocks or more: two players' arrows strike two zombies, one loosed 40 blocks off and
     * one 52 (the distance is the shot's, from where it was loosed: here the record's origin, set back along the line).
     */
    static void longShotAdvancement(GameTestHelper helper) {
        ServerPlayer near = helper.makeMockServerPlayerInLevel(), far = helper.makeMockServerPlayerInLevel();
        Zombie first = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(2.5, 1, 6.5));
        Zombie second = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(8.5, 1, 6.5));
        first.setBaby(false);
        second.setBaby(false);
        loose(helper, near, 2.5, 40.0);
        loose(helper, far, 8.5, 52.0);
        helper.runAfterDelay(10, () -> {
            if (first.getHealth() >= first.getMaxHealth() || second.getHealth() >= second.getMaxHealth()) helper.fail("both arrows should hit");
            else if (done(helper, near, "long_shot")) helper.fail("a hit from 40 blocks shouldn't award Long Shot");
            else if (!done(helper, far, "long_shot")) helper.fail("a hit from 52 blocks should award Long Shot");
            else helper.succeed();
        });
    }

    /** A player's arrow down +z at x, about 3.6 blocks short of a zombie at z 6.5, its shot loosed {@code distance} off. */
    private static void loose(GameTestHelper helper, ServerPlayer player, double x, double distance) {
        Arrow arrow = EntityTypes.ARROW.create(helper.getLevel(), EntitySpawnReason.COMMAND);
        Vec3 from = helper.absoluteVec(new Vec3(x, 2.0, 2.5));
        arrow.setPos(from);
        arrow.setOwner(player);
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(0, 0, 3.0);
        Vec3 loosed = from.add(0.0, 0.0, -(distance - 3.6));
        ((ShotHolder) arrow).fulldraw$setShot(new ShotRecord(false, loosed, 1.0, 1.0, 0.0, BuiltInRegistries.ITEM.getKey(Items.BOW)));
        helper.getLevel().addFreshEntity(arrow);
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

    /**
     * Duel at Dusk: a Marksman brought down by a player's arrow awards it; one brought down by a player's blow doesn't
     * (an archer's duel, as vanilla's Sniper Duel asks of a skeleton).
     */
    static void duelAtDuskAdvancement(GameTestHelper helper) {
        ServerPlayer archer = helper.makeMockServerPlayerInLevel(), fighter = helper.makeMockServerPlayerInLevel();
        var shot = helper.spawnWithNoFreeWill(com.panda.fulldraw.registry.ModEntities.MARKSMAN.get(), new Vec3(6.5, 1, 6.5));
        var struck = helper.spawnWithNoFreeWill(com.panda.fulldraw.registry.ModEntities.MARKSMAN.get(), new Vec3(2.5, 1, 6.5));
        shot.setHealth(1.0F);
        struck.setHealth(1.0F);
        loose(helper, archer, 6.5, 4.0);
        struck.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(fighter), 5.0F);
        helper.runAfterDelay(10, () -> {
            if (shot.isAlive() || struck.isAlive()) helper.fail("both Marksmen should be down: shot " + shot.isAlive() + ", struck " + struck.isAlive());
            else if (!done(helper, archer, "duel_at_dusk")) helper.fail("bringing the Marksman down with an arrow should award Duel at Dusk");
            else if (done(helper, fighter, "duel_at_dusk")) helper.fail("bringing it down with a blow shouldn't award Duel at Dusk");
            else helper.succeed();
        });
    }

    /** A Blast Arrow's blast is an arrow's kill too: its direct source is the arrow. */
    static void duelAtDuskBlast(GameTestHelper helper) {
        ServerPlayer bomber = helper.makeMockServerPlayerInLevel();
        var marksman = helper.spawnWithNoFreeWill(com.panda.fulldraw.registry.ModEntities.MARKSMAN.get(), new Vec3(6.5, 1, 7.5));
        marksman.setHealth(1.0F);
        var arrow = new com.panda.fulldraw.entity.BlastArrow(helper.getLevel(), bomber, new ItemStack(com.panda.fulldraw.registry.ModItems.BLAST_ARROW.get()), null);
        Vec3 from = helper.absoluteVec(new Vec3(6.5, 2.0, 3.5));
        arrow.setPos(from);
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(0, 0, 2.0);
        helper.getLevel().addFreshEntity(arrow);
        helper.runAfterDelay(10, () -> {
            if (marksman.isAlive()) helper.fail("the Blast Arrow should have brought the Marksman down");
            else if (!done(helper, bomber, "duel_at_dusk")) helper.fail("a Blast Arrow's kill should award Duel at Dusk");
            else helper.succeed();
        });
    }

    /** Whether the player has earned the advancement fulldraw:{@code id}. */
    static boolean done(GameTestHelper helper, ServerPlayer player, String id) {
        var holder = helper.getLevel().getServer().getAdvancements().get(FullDrawMod.id(id));
        if (holder == null) throw helper.assertionException("advancement fulldraw:" + id + " is not loaded");
        return player.getAdvancements().getOrStartProgress(holder).isDone();
    }
}
