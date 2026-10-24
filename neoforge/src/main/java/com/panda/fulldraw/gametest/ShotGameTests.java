package com.panda.fulldraw.gametest;

import com.panda.fulldraw.logic.Breath;
import com.panda.fulldraw.shot.BowBash;
import com.panda.fulldraw.shot.Focus;
import com.panda.fulldraw.shot.ShotHolder;
import com.panda.fulldraw.shot.ShotRecord;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The shot pipeline in a real world. */
final class ShotGameTests {
    private ShotGameTests() {}

    /**
     * A release inside the Perfect window gives a Perfect, crit arrow; a late one gives neither (vanilla would crit both).
     * The server opens the window a tick before the ring (22): 21 counts, 20 doesn't.
     */
    static void perfectReleaseCrits(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(5.5, 1, 5.5));
        player.snapTo(at.x, at.y, at.z, 0, -60);
        ItemStack bow = new ItemStack(Items.BOW);
        player.setItemInHand(InteractionHand.MAIN_HAND, bow);
        player.getInventory().add(new ItemStack(Items.ARROW, 8));
        BowItem item = (BowItem) Items.BOW;
        int duration = item.getUseDuration(bow, player);

        AbstractArrow perfect = release(helper, player, item, bow, duration - 23);
        AbstractArrow late = release(helper, player, item, bow, duration - 30);
        AbstractArrow grace = release(helper, player, item, bow, duration - 21);
        AbstractArrow early = release(helper, player, item, bow, duration - 20);
        ShotRecord p = ((ShotHolder) perfect).fulldraw$shot(), l = ((ShotHolder) late).fulldraw$shot(), e = ((ShotHolder) early).fulldraw$shot();
        if (p == null || l == null || e == null) throw helper.assertionException("a player's arrow has no shot record");
        if (!p.perfect() || !perfect.isCritArrow()) helper.fail("released at 23 ticks: expected a Perfect crit arrow");
        if (l.perfect() || late.isCritArrow()) helper.fail("released at 30 ticks: expected a plain arrow, vanilla's crit removed");
        ShotRecord g = ((ShotHolder) grace).fulldraw$shot();
        if (g == null || !g.perfect()) helper.fail("released at 21 ticks: the server's grace tick should count as Perfect");
        if (e.perfect() || early.isCritArrow()) helper.fail("released at 20 ticks: the server's window opens at 21");
        if (p.spread() != 0.0) helper.fail("a Perfect arrow must fly true, spread " + p.spread());
        helper.succeed();
    }

    /** The Shortbow's Perfect window follows its 12-tick draw, its arrows fly at 2.6 and hit for ×0.7, and it can sprint. */
    static void weaponStatsShortbow(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(5.5, 1, 5.5));
        player.snapTo(at.x, at.y, at.z, 0, -60);
        ItemStack bow = new ItemStack(com.panda.fulldraw.registry.ModItems.SHORTBOW.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, bow);
        player.getInventory().add(new ItemStack(Items.ARROW, 8));
        BowItem item = (BowItem) bow.getItem();
        int duration = item.getUseDuration(bow, player);
        AbstractArrow perfect = release(helper, player, item, bow, duration - 14);
        AbstractArrow plain = release(helper, player, item, bow, duration - 20);
        ShotRecord p = ((ShotHolder) perfect).fulldraw$shot(), l = ((ShotHolder) plain).fulldraw$shot();
        if (p == null || !p.perfect()) helper.fail("a Shortbow released at 14 ticks (12 + 2) should be Perfect");
        if (l == null || l.perfect()) helper.fail("a Shortbow released at 20 ticks is past its window");
        if (p != null && Math.abs(p.weaponMult() - 0.7) > 1e-6) helper.fail("Shortbow damage multiplier " + p.weaponMult());
        double speed = perfect.getDeltaMovement().length();
        if (Math.abs(speed - 2.6) > 0.05) helper.fail("Shortbow launch speed " + speed + ", expected 2.6");
        var use = bow.get(net.minecraft.core.component.DataComponents.USE_EFFECTS);
        if (use == null || !use.canSprint() || use.speedMultiplier() != 1.0F) helper.fail("the Shortbow must draw at full speed and allow sprinting: " + use);
        helper.succeed();
    }

    /**
     * Sneaking at full draw holds the breath, and the server's breath sets the spread. A late release (30
     * ticks) by a still archer: 0.5 plain, 0.25 focused, and 1.5 when the breath ran out three seconds ago (the tremble
     * at full). The breath runs out after 2.5 s and the exhale leaves the archer unfocused.
     */
    static void steadyAimHalvesSpread(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(5.5, 1, 5.5));
        player.snapTo(at.x, at.y, at.z, 0, -60);
        ItemStack bow = new ItemStack(Items.BOW);
        player.setItemInHand(InteractionHand.MAIN_HAND, bow);
        player.getInventory().add(new ItemStack(Items.ARROW, 8));
        BowItem item = (BowItem) Items.BOW;
        int duration = item.getUseDuration(bow, player);
        double still = 0.5;

        // the input: sneaking while the draw is full, counted on the server's own tick
        player.setShiftKeyDown(true);
        player.startUsingItem(InteractionHand.MAIN_HAND);
        for (int i = 0; i < 25; i++) {
            player.tick();
            Focus.tick(player);
        }
        if (!Focus.of(player).focusing()) helper.fail("sneaking at full draw (" + player.getTicksUsingItem() + " ticks) should hold the breath");
        for (int i = 0; i < 60; i++) {
            player.tick();
            Focus.tick(player);
        }
        if (Focus.of(player).focusing() || !Focus.of(player).exhausted()) helper.fail("2.5 s of breath should have run out: " + Focus.of(player));
        player.stopUsingItem();
        player.setShiftKeyDown(false);

        Focus.set(player, Breath.FULL);
        double plain = lastSpread(helper, player, item, bow, duration);
        Focus.set(player, new Breath(0.5, true, -1));
        double focused = lastSpread(helper, player, item, bow, duration);
        Focus.set(player, new Breath(0.0, false, 60));
        double exhausted = lastSpread(helper, player, item, bow, duration);
        Focus.set(player, Breath.FULL);
        if (Math.abs(plain - 1.0 * still) > 1e-6) helper.fail("plain late shot spread " + plain + ", expected 0.5");
        else if (Math.abs(focused - 0.5 * still) > 1e-6) helper.fail("focused spread " + focused + ", expected half of plain (0.25)");
        else if (Math.abs(exhausted - 3.0 * still) > 1e-6) helper.fail("out of breath for 3 s: spread " + exhausted + ", expected the full tremble (1.5)");
        else helper.succeed();
    }

    /**
     * A melee hit with a bow does 1 damage, shoves the target about three blocks and rests the bow for a second;
     * a second hit inside that second is a plain punch.
     */
    static void bowBashShoves(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
        player.snapTo(at.x, at.y, at.z, 0, 0); // facing +Z
        ItemStack bow = new ItemStack(Items.BOW);
        player.setItemInHand(InteractionHand.MAIN_HAND, bow);
        for (int i = 0; i < 20; i++) player.tick(); // a full attack charge
        var pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, new Vec3(2.5, 1, 3.7));
        Vec3 start = pig.position();
        player.attack(pig);
        double shove = pig.getDeltaMovement().horizontalDistance();
        float dealt = pig.getMaxHealth() - pig.getHealth();
        if (Math.abs(dealt - 1.0F) > 0.01F) helper.fail("a bow bash should deal 1 damage, dealt " + dealt);
        else if (Math.abs(shove - (0.2 + BowBash.SHOVE)) > 0.05) helper.fail("a bow bash should launch at 0.8 (half the hit's 0.4, plus 0.6), horizontal speed " + shove);
        else if (!player.getCooldowns().isOnCooldown(bow)) helper.fail("the bow should rest for a second after a bash");
        else {
            // inside the cooldown: no shove of our own (vanilla's hurt knockback only, under 0.6)
            var cow = helper.spawnWithNoFreeWill(EntityTypes.COW, new Vec3(2.5, 1, 1.3));
            player.snapTo(at.x, at.y, at.z, 180, 0);
            player.attack(cow);
            double plain = cow.getDeltaMovement().horizontalDistance();
            if (plain > 0.6) helper.fail("a hit while the bow rests should be a plain punch, horizontal speed " + plain);
            else helper.runAfterDelay(20, () -> {
                double moved = pig.position().subtract(start).horizontalDistance();
                // the test arena's walls stop a full shove, so the distance is measured in the open elsewhere
                if (moved < 2.0) helper.fail("the pig should be shoved at least two blocks, moved " + moved);
                else helper.succeed();
            });
        }
    }

    /**
     * A bashed player is shoved too: its own client moves it, so the hit marks it to be sent its new motion. The bow's
     * rest also blocks drawing: a draw started during it doesn't take, one started before the bash does.
     */
    @SuppressWarnings("removal")
    static void bowBashMovesPlayers(GameTestHelper helper) {
        net.minecraft.server.level.ServerPlayer attacker = helper.makeMockServerPlayerInLevel();
        net.minecraft.server.level.ServerPlayer target = helper.makeMockServerPlayerInLevel();
        attacker.setGameMode(GameType.SURVIVAL);
        target.setGameMode(GameType.SURVIVAL);
        // a player is invulnerable until its client says the world has loaded; a mock player's never does
        target.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        Vec3 at = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
        attacker.snapTo(at.x, at.y, at.z, 0, 0); // facing +Z
        target.snapTo(at.x, at.y, at.z + 1.2, 180, 0);
        ItemStack bow = new ItemStack(Items.BOW);
        attacker.setItemInHand(InteractionHand.MAIN_HAND, bow);
        attacker.getInventory().add(new ItemStack(Items.ARROW, 4));
        attacker.gameMode.useItem(attacker, helper.getLevel(), bow, InteractionHand.MAIN_HAND);
        if (!attacker.isUsingItem()) {
            helper.fail("before the bash the bow should draw");
            return;
        }
        attacker.stopUsingItem();
        attacker.attack(target);
        double shove = target.getDeltaMovement().horizontalDistance();
        if (shove < BowBash.SHOVE)
            helper.fail("a bashed player should be shoved at least " + BowBash.SHOVE + ", horizontal speed " + shove + ", health " + target.getHealth());
        else if (!target.syncVelocity) helper.fail("a bashed player's own client should be sent the shove");
        else {
            attacker.gameMode.useItem(attacker, helper.getLevel(), bow, InteractionHand.MAIN_HAND);
            if (attacker.isUsingItem()) helper.fail("the bow should not draw while it rests after a bash");
            else helper.succeed();
        }
    }

    /** {@code /fulldraw stats} needs no permission: a player who isn't an operator runs it. */
    @SuppressWarnings("removal")
    static void statsNeedsNoPermission(GameTestHelper helper) {
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        var source = player.createCommandSourceStack().withPermission(net.minecraft.server.permissions.PermissionSet.NO_PERMISSIONS)
                .withSuppressedOutput();
        try {
            int result = helper.getLevel().getServer().getCommands().getDispatcher().execute("fulldraw stats", source);
            if (result != 1) helper.fail("/fulldraw stats returned " + result + " for a player with no permissions");
            else helper.succeed();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            helper.fail("/fulldraw stats refused a player with no permissions: " + e.getMessage());
        }
    }

    /** The server's numbers survive the trip to a client: every field, with values that differ from the defaults. */
    static void serverTuningCodecRoundTrips(GameTestHelper helper) {
        var sent = new com.panda.fulldraw.network.FullDrawNetwork.ServerTuning(new com.panda.fulldraw.logic.ShotMath.Tuning(
                7, 3, 2.25, 1.75, 1.25, 4.5, 12.0, 0.03, 1.8, 3.5, 0.45, 2.5, 7.5, 4.0));
        var buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        com.panda.fulldraw.network.FullDrawNetwork.ServerTuning.STREAM_CODEC.encode(buf, sent);
        var received = com.panda.fulldraw.network.FullDrawNetwork.ServerTuning.STREAM_CODEC.decode(buf);
        if (!received.equals(sent)) helper.fail("server_tuning changed on the way: sent " + sent + ", got " + received);
        else if (buf.readableBytes() != 0) helper.fail("server_tuning left " + buf.readableBytes() + " bytes unread");
        else helper.succeed();
    }

    /**
     * {@code /fulldraw stats} names the held bow's numbers and breaks down the last arrow that landed: here a
     * Perfect headshot at 10 blocks, 6 × 1.5 × 1.5 = 13.5.
     */
    static void statsReports(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
        var before = com.panda.fulldraw.command.StatsCommand.lines(player);
        if (before.size() != 2 || !before.get(1).getString().contains("No arrow")) helper.fail("before any hit: " + before);
        Zombie head = zombie(helper, 5.5);
        Arrow arrow = EntityTypes.ARROW.create(helper.getLevel(), EntitySpawnReason.COMMAND);
        Vec3 from = helper.absoluteVec(new Vec3(5.5, 1.0 + 1.75, 1.5));
        arrow.setPos(from);
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(0, 0, 3.0);
        arrow.setBaseDamage(2.0);
        arrow.setOwner(player);
        ((ShotHolder) arrow).fulldraw$setShot(new ShotRecord(true, from.add(0, 0, -6), 1.0, 1.0, 0.0, BuiltInRegistries.ITEM.getKey(Items.BOW)));
        helper.getLevel().addFreshEntity(arrow);
        helper.runAfterDelay(20, () -> {
            var last = com.panda.fulldraw.shot.Shots.lastShot(player);
            if (last == null) {
                helper.fail("the hit wasn't kept for /fulldraw stats (zombie health " + head.getHealth() + ")");
                return;
            }
            var b = last.damage();
            var lines = com.panda.fulldraw.command.StatsCommand.lines(player);
            String text = lines.stream().map(net.minecraft.network.chat.Component::getString).reduce("", (x, y) -> x + " | " + y);
            if (b.base() != 6 || b.perfectMult() != 1.5 || b.precisionMult() != 1.5 || Math.abs(b.total() - 13.5) > 1e-6)
                helper.fail("breakdown " + b);
            else if (lines.size() != 3 || !text.contains("Perfect from 1.10 s to 1.30 s") || !text.contains("13.50 damage")
                    || !text.contains("1.50 Perfect") || !text.contains("1.00 arrow") || text.contains("point-blank"))
                helper.fail("stats lines: " + text);
            else {
                // a crossbow keeps vanilla's numbers, so it's shown no Perfect window
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CROSSBOW));
                String crossbow = com.panda.fulldraw.command.StatsCommand.lines(player).getFirst().getString();
                if (!crossbow.contains("no Perfect window")) helper.fail("crossbow stats: " + crossbow);
                else helper.succeed();
            }
        });
    }

    /** The spread the server recorded for a late (30-tick) release. */
    private static double lastSpread(GameTestHelper helper, Player player, BowItem item, ItemStack bow, int duration) {
        ShotRecord shot = ((ShotHolder) release(helper, player, item, bow, duration - 30)).fulldraw$shot();
        if (shot == null) throw helper.assertionException("a player's arrow has no shot record");
        return shot.spread();
    }

    private static AbstractArrow release(GameTestHelper helper, Player player, BowItem item, ItemStack bow, int timeLeft) {
        AABB box = new AABB(player.blockPosition()).inflate(4);
        Set<AbstractArrow> before = new HashSet<>(helper.getLevel().getEntitiesOfClass(AbstractArrow.class, box));
        item.releaseUsing(bow, helper.getLevel(), player, timeLeft);
        List<AbstractArrow> after = helper.getLevel().getEntitiesOfClass(AbstractArrow.class, box, a -> !before.contains(a));
        if (after.size() != 1) throw helper.assertionException("expected one new arrow, found " + after.size());
        return after.getFirst();
    }

    /**
     * Four zombies in a row, an arrow at each: body, head, a body shot whose record says it left the bow 40 blocks back,
     * and the upper chest just under the neck. Head ×1.5 and range ×1.2 over the body shot, armour included; the chest
     * is a body shot.
     */
    static void headshotAndLongShot(GameTestHelper helper) {
        Zombie body = zombie(helper, 2.5), head = zombie(helper, 5.5), longShot = zombie(helper, 8.5), chest = zombie(helper, 10.5);
        fire(helper, 2.5, 1.0 + 0.8, 0);
        fire(helper, 5.5, 1.0 + 1.75, 0);
        fire(helper, 8.5, 1.0 + 0.8, 40);
        fire(helper, 10.5, 1.0 + 1.35, 0);
        helper.runAfterDelay(20, () -> {
            float b = 20 - body.getHealth(), h = 20 - head.getHealth(), l = 20 - longShot.getHealth(), c = 20 - chest.getHealth();
            if (b < 5 || b > 6.5) helper.fail("body shot dealt " + b + ", expected about 6 (5.9 after the zombie's armour)");
            else if (Math.abs(h / b - 1.5) > 0.08) helper.fail("headshot dealt " + h + " vs body " + b + ": expected ×1.5");
            else if (Math.abs(l / b - 1.2) > 0.08) helper.fail("40-block shot dealt " + l + " vs body " + b + ": expected ×1.2");
            else if (Math.abs(c - b) > 0.01) helper.fail("an upper-chest shot (1.35 m) dealt " + c + " vs body " + b + ": the chest is not the head");
            else helper.succeed();
        });
    }

    /** The shipped weak-spot files load: slimes have none, a spider's head is at its front, not on its back. */
    static void weakSpotDataLoads(GameTestHelper helper) {
        var overrides = com.panda.fulldraw.shot.WeakSpots.overrides();
        for (var type : List.of(EntityTypes.SLIME, EntityTypes.MAGMA_CUBE, EntityTypes.SPIDER, EntityTypes.CAVE_SPIDER, EntityTypes.GHAST, EntityTypes.CREEPER))
            if (!overrides.containsKey(type)) helper.fail("no weak-spot override loaded for " + net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type));
        var slime = helper.spawnWithNoFreeWill(EntityTypes.SLIME, new Vec3(2.5, 1, 2.5));
        if (com.panda.fulldraw.shot.WeakSpots.of(slime) != com.panda.fulldraw.logic.WeakSpot.NONE) helper.fail("a slime must have no weak spot");
        var spider = helper.spawnWithNoFreeWill(EntityTypes.SPIDER, new Vec3(6.5, 1, 6.5));
        spider.setYBodyRot(0); // facing +Z
        var spot = com.panda.fulldraw.shot.WeakSpots.of(spider);
        double w = spider.getBbWidth(), h = spider.getBbHeight();
        if (!spot.hit(0, 0.5 * h, 0.45 * w, 0, w, h)) helper.fail("a spider's front should be its weak spot");
        if (spot.hit(0, 0.95 * h, -0.3 * w, 0, w, h)) helper.fail("a spider's back is not its head");
        helper.succeed();
    }

    private static Zombie zombie(GameTestHelper helper, double x) {
        Zombie z = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(x, 1, 6.5));
        z.setBaby(false);
        // test worlds are in daylight: a burning zombie would take fire damage on top of the arrow
        z.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 200));
        for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) z.setItemSlot(slot, ItemStack.EMPTY);
        return z;
    }

    private static void fire(GameTestHelper helper, double x, double y, double originBack) {
        Arrow arrow = EntityTypes.ARROW.create(helper.getLevel(), EntitySpawnReason.COMMAND);
        Vec3 from = helper.absoluteVec(new Vec3(x, y, 1.5));
        arrow.setPos(from);
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(0, 0, 3.0);
        arrow.setBaseDamage(2.0);
        arrow.setCritArrow(false);
        ((ShotHolder) arrow).fulldraw$setShot(new ShotRecord(false, from.add(0, 0, -originBack), 1.0, 1.0, 0.0,
                BuiltInRegistries.ITEM.getKey(Items.BOW)));
        helper.getLevel().addFreshEntity(arrow);
    }
}
