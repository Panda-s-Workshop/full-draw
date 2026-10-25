package com.panda.fulldraw.gametest;

import com.panda.fulldraw.FullDrawMod;
import com.panda.fulldraw.entity.Marksman;
import com.panda.fulldraw.logic.MarksmanRules;
import com.panda.fulldraw.registry.ModEntities;
import com.panda.fulldraw.registry.ModItems;
import com.panda.fulldraw.shot.ShotHolder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The Marksman: its cover, its Perfect shots, its second phase and boss bar, its place on the watchtower, its drops. */
final class MarksmanGameTests {
    private MarksmanGameTests() {}

    /**
     * Ducked at a crenel, a blow from beyond the parapet lands only on its head: an arrow dropping over the parapet into
     * its body is turned away and falls, one level over the parapet into its head lands; standing in the crenel, a body
     * arrow lands. An explosion beyond the parapet neither hurts nor moves it. From the battlements behind it, an arrow
     * and a blow land.
     */
    static void marksmanCoverOnlyPrecision(GameTestHelper helper) {
        // beyond the parapets (out is +x)
        ServerPlayer archer = player(helper, 10.5, 1.5);
        Marksman body = atCrenel(helper, 2, 3, true);
        Marksman head = atCrenel(helper, 2, 8, true);
        Marksman standing = atCrenel(helper, 7, 3, false);
        Marksman behind = atCrenel(helper, 7, 8, true);
        // steeply down over the parapet's inner edge into its body, below its head
        Vec3 down = new Vec3(-0.507, -0.862, 0.0).scale(2.0);
        AbstractArrow bodyArrow = shot(helper, archer, new Vec3(4.32, 4.29, 3.5), down);
        shot(helper, archer, new Vec3(5.5, 2.2, 8.5), new Vec3(-2.0, 0.0, 0.0));
        shot(helper, archer, new Vec3(11.5, 2.5, 3.5), new Vec3(-2.0, 0.0, 0.0));
        Vec3[] behindWas = new Vec3[1];
        helper.runAfterDelay(1, () -> {
            if (!body.isCovered() || !head.isCovered() || standing.isCovered() || !behind.isCovered())
                helper.fail("set up wrong: covered " + body.isCovered() + ", " + head.isCovered() + ", standing " + standing.isCovered() + ", "
                        + behind.isCovered() + "; crenels " + body.crenels());
            // a Blast Arrow's blast just beyond the parapet
            behindWas[0] = behind.position();
            Vec3 at = helper.absoluteVec(new Vec3(9.8, 1.5, 8.5));
            helper.getLevel().explode(archer, at.x, at.y, at.z, 1.5F, Level.ExplosionInteraction.NONE);
        });
        helper.runAfterDelay(12, () -> {
            List<String> wrong = new ArrayList<>();
            if (lost(body) > 0) wrong.add("a body arrow on it ducked should be turned away, it lost " + lost(body));
            if (!bodyArrow.isAlive()) wrong.add("the arrow turned away should fall, not vanish");
            if (body.turnedAway != 1) wrong.add("the arrow turned away should knock on the parapet once, with its chips: " + body.turnedAway);
            if (head.turnedAway + standing.turnedAway + behind.turnedAway != 0) wrong.add("only the turned-away arrow should knock on the parapet");
            if (lost(head) <= 0) wrong.add("a head arrow on it ducked should land");
            if (lost(standing) <= 0) wrong.add("a body arrow on it standing should land");
            if (lost(behind) > 0) wrong.add("an explosion beyond the parapet should do it no harm, it lost " + lost(behind));
            if (behind.position().distanceTo(behindWas[0]) > 0.05)
                wrong.add("an explosion should not move it, it moved " + behind.position().distanceTo(behindWas[0]));
            if (!wrong.isEmpty()) {
                helper.fail(String.join("; ", wrong));
                return;
            }
            // from the battlements behind it, along the walkway
            shot(helper, archer, new Vec3(4.6, 1.5, 8.5), new Vec3(2.0, 0.0, 0.0));
        });
        float[] fromBehind = new float[1];
        helper.runAfterDelay(20, () -> {
            fromBehind[0] = lost(behind);
            if (fromBehind[0] <= 0) helper.fail("an arrow from the battlements behind it should land");
        });
        // past the half second it shrugs off further blows after a hit
        helper.runAfterDelay(45, () -> {
            ServerPlayer near = player(helper, 5.5, 8.5);
            near.attack(behind);
            if (lost(behind) <= fromBehind[0]) helper.fail("a blow from a player behind it on the battlements should land");
            else helper.succeed();
        });
    }

    /**
     * With a foe in sight it ducks for 2 s, then stands, draws for 1.5 s and looses a Perfect arrow (a crit, with a
     * Perfect shot record) that strikes the foe; then it ducks again. (The foe is an iron golem: a mock player can't be
     * held as a mob's target.)
     */
    static void marksmanDrawsPerfectShots(GameTestHelper helper) {
        LivingEntity foe = golem(helper, 1.5, 6.5);
        Marksman marksman = helper.spawn(ModEntities.MARKSMAN.get(), new Vec3(10.5, 1, 6.5));
        marksman.setTarget(foe);
        long start = helper.getLevel().getGameTime();
        List<AbstractArrow> seen = new ArrayList<>();
        helper.onEachTick(() -> helper.getLevel().getEntitiesOfClass(AbstractArrow.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(16),
                a -> a.getOwner() == marksman && !seen.contains(a)).forEach(seen::add));
        helper.succeedWhen(() -> {
            if (marksman.loosed.isEmpty()) throw helper.assertionException("it hasn't loosed yet");
            if (foe.getHealth() >= foe.getMaxHealth()) throw helper.assertionException("its arrow hasn't struck yet");
            var first = marksman.loosed.getFirst();
            if (first.shot() != MarksmanRules.Shot.ARROW) throw helper.assertionException("its first shot should be an arrow: " + first);
            long after = first.gameTime() - start;
            if (after < MarksmanRules.COVER_TICKS + MarksmanRules.DRAW_TICKS)
                throw helper.assertionException("it loosed " + after + " ticks in, before it had ducked and drawn");
            if (Math.abs(first.drawTicks() - MarksmanRules.DRAW_TICKS) > 2) throw helper.assertionException("it drew for " + first.drawTicks() + " ticks");
            if (seen.isEmpty()) throw helper.assertionException("no arrow of its seen");
            AbstractArrow arrow = seen.getFirst();
            var record = ((ShotHolder) arrow).fulldraw$shot();
            if (!arrow.isCritArrow() || record == null || !record.perfect())
                throw helper.assertionException("its arrow should be a Perfect shot: crit " + arrow.isCritArrow() + ", record " + record);
            if (marksman.getPose() != Pose.CROUCHING) throw helper.assertionException("after loosing it should duck again");
            FullDrawMod.LOGGER.info("the Marksman loosed {} ticks in after a {}-tick draw; the foe lost {}", after, first.drawTicks(),
                    foe.getMaxHealth() - foe.getHealth());
        });
    }

    /**
     * A Blunt Arrow's knock in the middle of its draw costs it that shot: its bow comes down, it ducks again, and no
     * arrow comes when the draw would have ended. (The foe is an iron golem: a mock player can't be held as a mob's
     * target.)
     */
    static void marksmanLosesTheShotToABlunt(GameTestHelper helper) {
        LivingEntity foe = golem(helper, 1.5, 6.5);
        Marksman marksman = helper.spawn(ModEntities.MARKSMAN.get(), new Vec3(10.5, 1, 6.5));
        marksman.setTarget(foe);
        long[] knocked = {-1};
        helper.onEachTick(() -> {
            if (knocked[0] < 0 && marksman.isUsingItem() && marksman.getTicksUsingItem() >= 5) {
                if (!marksman.loosed.isEmpty()) helper.fail("it loosed before it was knocked: " + marksman.loosed);
                com.panda.fulldraw.entity.BluntArrow.interrupt(marksman);
                knocked[0] = helper.getLevel().getGameTime();
            }
        });
        helper.succeedWhen(() -> {
            if (knocked[0] < 0) throw helper.assertionException("it hasn't drawn yet");
            long after = helper.getLevel().getGameTime() - knocked[0];
            if (!marksman.loosed.isEmpty()) helper.fail("knocked out of its draw it loosed anyway, " + after + " ticks later: " + marksman.loosed);
            if (marksman.isUsingItem() && after < MarksmanRules.COVER_TICKS) helper.fail("knocked out of its draw it should let its bow down and duck");
            if (after < MarksmanRules.DRAW_TICKS) throw helper.assertionException("waiting past when the draw would have ended");
            if (marksman.getPose() != Pose.CROUCHING) helper.fail("knocked out of its draw it should duck again");
        });
    }

    /**
     * Below half its health its bar turns red, and a foe who hides is lit up with a Flare Arrow (one not glowing) or
     * shelled with a Blast Arrow (one glowing); its bar shows to the players near it. (The foes are iron golems: a mock
     * player can't be held as a mob's target.)
     */
    static void marksmanPhaseTwo(GameTestHelper helper) {
        // a wall across the arena hides each foe from both Marksmen
        for (int x = 0; x < 12; x++)
            for (int y = 1; y < 8; y++) helper.setBlock(new BlockPos(x, y, 6), Blocks.STONE);
        Marksman flares = helper.spawn(ModEntities.MARKSMAN.get(), new Vec3(2.5, 1, 2.5));
        Marksman blasts = helper.spawn(ModEntities.MARKSMAN.get(), new Vec3(9.5, 1, 2.5));
        LivingEntity dark = golem(helper, 2.5, 9.5), lit = golem(helper, 9.5, 9.5);
        // behind the wall too, so neither Marksman takes it for a foe
        ServerPlayer near = player(helper, 5.5, 9.5);
        lit.addEffect(new MobEffectInstance(MobEffects.GLOWING, 400));
        for (Marksman m : List.of(flares, blasts)) m.setHealth(MarksmanRules.MAX_HEALTH * 0.5F);
        flares.setTarget(dark);
        blasts.setTarget(lit);
        helper.succeedWhen(() -> {
            if (flares.bossBarColor() != BossEvent.BossBarColor.RED) throw helper.assertionException("the bar should be red at half health");
            if (!flares.bossBarPlayers().contains(near)) throw helper.assertionException("a player near it should see its bar: " + flares.bossBarPlayers());
            if (flares.loosed.isEmpty() || blasts.loosed.isEmpty()) throw helper.assertionException("not loosed yet");
            if (flares.loosed.getFirst().shot() != MarksmanRules.Shot.FLARE)
                throw helper.assertionException("at a hidden foe not glowing it should loose a flare: " + flares.loosed);
            if (blasts.loosed.getFirst().shot() != MarksmanRules.Shot.BLAST)
                throw helper.assertionException("at a hidden foe glowing it should loose a blast: " + blasts.loosed);
            if (Math.abs(flares.loosed.getFirst().drawTicks() - MarksmanRules.DRAW_TICKS_PHASE_TWO) > 2)
                throw helper.assertionException("in its second phase it should draw for 24 ticks: " + flares.loosed);
        });
    }

    /**
     * Its bar leaves a player's screen the moment that player stops tracking it (a portal, a teleport), and every
     * screen when it is removed in any way, an unloaded chunk's included.
     */
    static void marksmanBossBarLeaves(GameTestHelper helper) {
        Marksman marksman = helper.spawn(ModEntities.MARKSMAN.get(), new Vec3(2.5, 1, 2.5));
        ServerPlayer near = player(helper, 5.5, 5.5);
        boolean[] stopped = new boolean[1];
        helper.succeedWhen(() -> {
            if (!stopped[0]) {
                if (!marksman.bossBarPlayers().contains(near)) throw helper.assertionException("a player near it should see its bar first");
                marksman.stopSeenByPlayer(near);
                if (marksman.bossBarPlayers().contains(near)) helper.fail("a player who stops tracking it should lose its bar at once");
                stopped[0] = true;
                throw helper.assertionException("now waiting for the bar to come back");
            }
            if (!marksman.bossBarPlayers().contains(near)) throw helper.assertionException("a player still near it should see its bar again");
            marksman.setRemoved(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
            if (!marksman.bossBarPlayers().isEmpty()) helper.fail("unloaded, its bar should leave every screen: " + marksman.bossBarPlayers());
        });
    }

    /**
     * The watchtower's template, placed as a structure places it, puts one Marksman on the top: persistent, its home
     * where it stands, holding a bow, with the twelve crenels of the battlements about it; saved and loaded it
     * keeps its home. With no foe it walks into a crenel to keep watch.
     */
    static void marksmanOnTheWatchtower(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var template = level.getServer().getStructureTemplateManager().get(FullDrawMod.id("rangers_watchtower")).orElseThrow();
        BlockPos corner = helper.absolutePos(new BlockPos(-8, 40, -8));
        template.placeInWorld(level, corner, corner, new StructurePlaceSettings().setFinalizeEntities(true), RandomSource.create(1L), 2);
        List<Marksman> found = level.getEntitiesOfClass(Marksman.class, new AABB(corner).expandTowards(27, 21, 27));
        if (found.size() != 1) {
            helper.fail("the tower should hold one Marksman, it holds " + found.size());
            return;
        }
        Marksman marksman = found.getFirst();
        BlockPos top = corner.offset(13, 18, 13);
        List<String> wrong = new ArrayList<>();
        if (!marksman.blockPosition().equals(top)) wrong.add("it should stand on the top's middle " + top + ", not " + marksman.blockPosition());
        if (!top.equals(marksman.home())) wrong.add("its home should be where it stands: " + marksman.home());
        if (!marksman.isPersistenceRequired()) wrong.add("it should never despawn");
        ItemStack held = marksman.getMainHandItem();
        if (!held.is(ModItems.RECURVE_BOW.get()) || !prize(held)) wrong.add("it should hold the Recurve it drops, Deadeye II and named: " + held);
        if (marksman.crenels().size() != 12) wrong.add("it should find the 12 crenels, found " + marksman.crenels());
        var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
        marksman.saveWithoutId(out);
        Marksman copy = ModEntities.MARKSMAN.get().create(level, EntitySpawnReason.LOAD);
        if (copy == null) throw helper.assertionException("couldn't make a Marksman");
        copy.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), out.buildResult()));
        if (!top.equals(copy.home())) wrong.add("loaded, its home should be " + top + ", not " + copy.home());
        if (!wrong.isEmpty()) {
            marksman.discard();
            helper.fail(String.join("; ", wrong));
            return;
        }
        // with no foe it walks into a crenel and keeps watch from it
        helper.succeedWhen(() -> {
            if (marksman.crenels().stream().noneMatch(c -> c.stand().equals(marksman.blockPosition())))
                throw helper.assertionException("it should walk into a crenel to keep watch; it stands at " + marksman.blockPosition().subtract(corner));
            marksman.discard();
        });
    }

    /**
     * On its tower, with a foe on the ground 23 blocks out and 16 below: it ducks to the crenel that faces the foe, steps
     * up onto the parapet in the crenel's gap (from behind the parapet it couldn't see so far down), draws, looses an
     * arrow that strikes, and steps back down to duck behind the parapet.
     */
    static void marksmanShootsFromTheGap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var template = level.getServer().getStructureTemplateManager().get(FullDrawMod.id("rangers_watchtower")).orElseThrow();
        BlockPos corner = helper.absolutePos(new BlockPos(-8, 40, -8));
        template.placeInWorld(level, corner, corner, new StructurePlaceSettings().setFinalizeEntities(true), RandomSource.create(1L), 2);
        Marksman marksman = level.getEntitiesOfClass(Marksman.class, new AABB(corner).expandTowards(27, 21, 27)).getFirst();
        BlockPos pad = corner.offset(13, 1, -10);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) level.setBlockAndUpdate(pad.offset(dx, 0, dz), Blocks.STONE.defaultBlockState());
        IronGolem foe = net.minecraft.world.entity.EntityTypes.IRON_GOLEM.create(level, EntitySpawnReason.COMMAND);
        if (foe == null) throw helper.assertionException("couldn't make a golem");
        foe.snapTo(pad.getX() + 0.5, pad.getY() + 1, pad.getZ() + 0.5, 0, 0);
        foe.setNoAi(true);
        level.addFreshEntity(foe);
        marksman.setTarget(foe);
        boolean[] rose = {false};
        helper.succeedWhen(() -> {
            if (marksman.crenels().stream().anyMatch(c -> Marksman.shootingSpot(c).equals(marksman.blockPosition()))) rose[0] = true;
            if (marksman.loosed.isEmpty()) throw helper.assertionException("not loosed yet; it stands at " + marksman.blockPosition().subtract(corner));
            if (!rose[0]) throw helper.assertionException("it should step up into a crenel's gap to shoot");
            if (foe.getHealth() >= foe.getMaxHealth()) throw helper.assertionException("its arrow should strike the foe below");
            if (!marksman.isCovered()) throw helper.assertionException("after loosing it should duck behind the parapet again");
            FullDrawMod.LOGGER.info("from the gap the Marksman loosed at a foe 16 below and 23 out; the foe lost {}", foe.getMaxHealth() - foe.getHealth());
            marksman.discard();
            foe.discard();
        });
    }

    /**
     * Struck by a foe it had not taken on, standing beyond the reach of its post (as an archer on the ground below its
     * tower stands), it takes that foe on and shoots back. (The foe is an iron golem: a mock player can't be held as a
     * mob's target.)
     */
    static void marksmanShootsBackAtAnUnseenAttacker(GameTestHelper helper) {
        Marksman marksman = helper.spawn(ModEntities.MARKSMAN.get(), new Vec3(1.5, 1, 1.5));
        marksman.setHome(marksman.blockPosition());
        LivingEntity foe = golem(helper, 10.5, 10.5);
        helper.runAfterDelay(1, () -> {
            if (marksman.getTarget() != null) helper.fail("it shouldn't have a foe before it is struck: " + marksman.getTarget());
            marksman.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(foe), 1.0F);
        });
        helper.succeedWhen(() -> {
            if (marksman.getTarget() != foe) throw helper.assertionException("struck, it should take on whoever struck it: " + marksman.getTarget());
            if (marksman.loosed.isEmpty()) throw helper.assertionException("not loosed yet");
            if (foe.getHealth() >= foe.getMaxHealth()) throw helper.assertionException("its arrow hasn't struck yet");
        });
    }

    /** Knocked off its tower onto the ground below, with no way up, it is back at its post in about 5 s. */
    static void marksmanBackToPost(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var template = level.getServer().getStructureTemplateManager().get(FullDrawMod.id("rangers_watchtower")).orElseThrow();
        BlockPos corner = helper.absolutePos(new BlockPos(-8, 40, -8));
        template.placeInWorld(level, corner, corner, new StructurePlaceSettings().setFinalizeEntities(true), RandomSource.create(1L), 2);
        Marksman marksman = level.getEntitiesOfClass(Marksman.class, new AABB(corner).expandTowards(27, 21, 27)).getFirst();
        BlockPos home = marksman.home();
        BlockPos below = corner.offset(13, 1, -6);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) level.setBlockAndUpdate(below.offset(dx, 0, dz), Blocks.STONE.defaultBlockState());
        marksman.teleportTo(below.getX() + 0.5, below.getY() + 1, below.getZ() + 0.5);
        long start = level.getGameTime();
        helper.succeedWhen(() -> {
            if (marksman.blockPosition().distManhattan(home) > 2)
                throw helper.assertionException("it should be back at its post " + home + ", it stands at " + marksman.blockPosition());
            long after = level.getGameTime() - start;
            if (after < MarksmanRules.STRANDED_TICKS) throw helper.assertionException("back after " + after + " ticks, too soon to be the climb back");
            FullDrawMod.LOGGER.info("knocked off its tower, the Marksman was back at its post after {} ticks", after);
            marksman.discard();
        });
    }

    /** A foe beside it on the battlements gets the bow's end, not an arrow: a blow and a shove, within a second or so. */
    static void marksmanBashesAFoeBesideIt(GameTestHelper helper) {
        LivingEntity foe = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityTypes.HUSK, new Vec3(5.5, 1, 6.5));
        Marksman marksman = helper.spawn(ModEntities.MARKSMAN.get(), new Vec3(7.5, 1, 6.5));
        marksman.setTarget(foe);
        Vec3 was = foe.position();
        helper.succeedWhen(() -> {
            if (lost(foe) <= 0) throw helper.assertionException("its bow's end should strike the foe beside it");
            if (!marksman.loosed.isEmpty()) throw helper.assertionException("it should strike a foe beside it, not shoot: " + marksman.loosed);
            if (foe.position().distanceTo(was) < 1.0) throw helper.assertionException("the blow should shove the foe off it");
            FullDrawMod.LOGGER.info("the Marksman struck a foe beside it for {} and shoved it {} blocks", lost(foe), foe.position().distanceTo(was));
        });
    }

    /** Every death: the Marksman's Eye and a Recurve Bow with Deadeye II, 1 to 3 bones and 2 to 6 arrows. */
    /**
     * The server's numbers for it: with health 60, a 12-tick draw and a blow of 5 in the config, a new Marksman has 60
     * health and a blow of 5, and draws for 12 ticks. (Alone: the config is the server's.)
     */
    static void marksmanFollowsTheConfig(GameTestHelper helper) {
        com.panda.fulldraw.FullDrawConfig.Source before = com.panda.fulldraw.FullDrawConfig.source();
        MarksmanRules.Tuning tuned = new MarksmanRules.Tuning(60.0F, 12, 10, 2.0, 5.0);
        com.panda.fulldraw.FullDrawConfig.use(new com.panda.fulldraw.FullDrawConfig.Source() {
            @Override public boolean vanillaCritOnFullDraw() { return before.vanillaCritOnFullDraw(); }
            @Override public com.panda.fulldraw.logic.ShotMath.Tuning tuning() { return before.tuning(); }
            @Override public boolean blastArrowBreaksBlocks() { return before.blastArrowBreaksBlocks(); }
            @Override public int ropeArrowLength() { return before.ropeArrowLength(); }
            @Override public double arrowRecoveryChance() { return before.arrowRecoveryChance(); }
            @Override public int arrowPickupLifetimeSeconds() { return before.arrowPickupLifetimeSeconds(); }
            @Override public boolean shieldbearerSpawns() { return before.shieldbearerSpawns(); }
            @Override public boolean galeHarrierSpawns() { return before.galeHarrierSpawns(); }
            @Override public boolean bogLurkerSpawns() { return before.bogLurkerSpawns(); }
            @Override public MarksmanRules.Tuning marksman() { return tuned; }
        });
        LivingEntity foe = golem(helper, 1.5, 6.5);
        Marksman marksman = helper.spawn(ModEntities.MARKSMAN.get(), new Vec3(10.5, 1, 6.5));
        marksman.setTarget(foe);
        float[] max = new float[1], health = new float[1];
        double[] blow = new double[1];
        // after its first tick, where one placed without a spawn takes the numbers
        helper.runAfterDelay(5, () -> {
            max[0] = marksman.getMaxHealth();
            health[0] = marksman.getHealth();
            blow[0] = marksman.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        });
        helper.runAfterDelay(150, () -> {
            com.panda.fulldraw.FullDrawConfig.use(before);
            if (max[0] != 60.0F || health[0] != 60.0F) helper.fail("a new Marksman should have the config's 60 health: " + health[0] + " of " + max[0]);
            else if (blow[0] != 5.0) helper.fail("its blow should be the config's 5: " + blow[0]);
            else if (marksman.loosed.isEmpty()) helper.fail("it should have loosed by now");
            else if (Math.abs(marksman.loosed.getFirst().drawTicks() - 12) > 2) helper.fail("it should draw for the config's 12 ticks: " + marksman.loosed.getFirst());
            else helper.succeed();
        });
    }

    /** Whether the Recurve is the Marksman's own: Deadeye II, named The Marksman's Recurve. */
    private static boolean prize(ItemStack stack) {
        var name = stack.get(net.minecraft.core.component.DataComponents.ITEM_NAME);
        return com.panda.fulldraw.registry.ModEnchantments.level(stack, com.panda.fulldraw.registry.ModEnchantments.DEADEYE) == 2 && name != null
                && name.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getKey().equals("item.fulldraw.marksmans_recurve");
    }

    static void marksmanDrops(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Marksman marksman = helper.spawnWithNoFreeWill(ModEntities.MARKSMAN.get(), new Vec3(6.5, 1, 6.5));
        LootTable table = level.getServer().reloadableRegistries().getLootTable(marksman.getType().getDefaultLootTable().orElseThrow());
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, marksman)
                .withParameter(LootContextParams.ORIGIN, marksman.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic())
                .create(LootContextParamSets.ENTITY);
        RandomSource random = RandomSource.create(20261025L);
        List<String> wrong = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            int eyes = 0, bows = 0, bones = 0, arrows = 0;
            for (ItemStack stack : table.getRandomItems(params, random)) {
                if (stack.is(ModItems.MARKSMANS_EYE.get())) eyes += stack.getCount();
                else if (stack.is(ModItems.RECURVE_BOW.get())) {
                    bows += stack.getCount();
                    if (!prize(stack)) wrong.add("its Recurve should be Deadeye II and named The Marksman's Recurve: " + stack + " " + stack.getComponents());
                }
                else if (stack.is(Items.BONE)) bones += stack.getCount();
                else if (stack.is(Items.ARROW)) arrows += stack.getCount();
                else if (!stack.isEmpty()) wrong.add("also " + stack);
            }
            if (eyes != 1 || bows != 1 || bones < 1 || bones > 3 || arrows < 2 || arrows > 6)
                wrong.add("eyes " + eyes + ", bows " + bows + ", bones " + bones + ", arrows " + arrows);
        }
        marksman.discard();
        if (!wrong.isEmpty()) helper.fail("drops: " + wrong.subList(0, Math.min(5, wrong.size())));
        else helper.succeed();
    }

    /**
     * Peaceful keeps the Marksman (one to a tower, none to replace it) but it gives up its foe: switched to Peaceful with
     * an iron golem as its target, a second later it is still there, with no target. Runs alone: the difficulty is the
     * server's.
     */
    static void marksmanKeptOnPeaceful(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        net.minecraft.world.Difficulty before = helper.getLevel().getDifficulty();
        Marksman marksman = helper.spawn(ModEntities.MARKSMAN.get(), new Vec3(3.5, 1, 3.5));
        IronGolem golem = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityTypes.IRON_GOLEM, new Vec3(8.5, 1, 8.5));
        marksman.setTarget(golem);
        server.setDifficulty(net.minecraft.world.Difficulty.PEACEFUL, true);
        helper.runAfterDelay(20, () -> {
            server.setDifficulty(before, true);
            if (marksman.isRemoved() || !marksman.isAlive()) helper.fail("Peaceful removed the Marksman: " + marksman.getRemovalReason());
            else if (marksman.getTarget() != null) helper.fail("on Peaceful it should drop its foe, still targets " + marksman.getTarget());
            else helper.succeed();
        });
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    /**
     * A Marksman at a crenel: a parapet a block high along x = cx + 1 from cz - 2 to cz + 2, merlons two higher on it but
     * at cz, the Marksman standing at (cx, cz) behind the gap, its AI off, ducked or not.
     */
    private static Marksman atCrenel(GameTestHelper helper, int cx, int cz, boolean ducked) {
        for (int z = cz - 2; z <= cz + 2; z++) {
            helper.setBlock(new BlockPos(cx + 1, 1, z), Blocks.STONE_BRICKS);
            if (z != cz) {
                helper.setBlock(new BlockPos(cx + 1, 2, z), Blocks.STONE_BRICKS);
                helper.setBlock(new BlockPos(cx + 1, 3, z), Blocks.STONE_BRICKS);
            }
        }
        Marksman marksman = helper.spawnWithNoFreeWill(ModEntities.MARKSMAN.get(), new Vec3(cx + 0.5, 1, cz + 0.5));
        marksman.setHome(helper.absolutePos(new BlockPos(cx, 1, cz)));
        marksman.setYRot(-90.0F);
        marksman.yBodyRot = -90.0F;
        if (ducked) marksman.setPose(Pose.CROUCHING);
        return marksman;
    }

    private static LivingEntity golem(GameTestHelper helper, double x, double z) {
        return helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityTypes.IRON_GOLEM, new Vec3(x, 1, z));
    }

    private static ServerPlayer player(GameTestHelper helper, double x, double z) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(x, 1, z));
        player.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
        return player;
    }

    /** A plain arrow from the player at a relative point, flying straight without gravity. */
    private static AbstractArrow shot(GameTestHelper helper, Player player, Vec3 at, Vec3 motion) {
        AbstractArrow arrow = ((ArrowItem) Items.ARROW).createArrow(helper.getLevel(), new ItemStack(Items.ARROW), player, null);
        Vec3 from = helper.absoluteVec(at);
        arrow.setPos(from.x, from.y, from.z);
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(motion);
        helper.getLevel().addFreshEntity(arrow);
        return arrow;
    }

    private static float lost(LivingEntity entity) {
        return entity.getMaxHealth() - entity.getHealth();
    }
}
