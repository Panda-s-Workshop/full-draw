package com.panda.fulldraw.gametest;

import com.panda.fulldraw.FullDrawConfig;
import com.panda.fulldraw.entity.Shieldbearer;
import com.panda.fulldraw.logic.TowerShield;
import com.panda.fulldraw.registry.ModEntities;
import com.panda.fulldraw.registry.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.phys.Vec3;

/**
 * The Shieldbearer: its tower shield against arrows from the front, the side, behind and above; the Blunt arrow's
 * stagger and the shield-piercing bolt's crack; standing dazed while it's down; its slow turn in a fight; its bash; and
 * where it comes from (raid waves, outposts), with the server's switch for both.
 */
final class ShieldbearerGameTests {
    private ShieldbearerGameTests() {}

    /**
     * Arrows from the front thud off the shield and fall at its feet: a plain one, and one that pierces three, which
     * stops there too and never reaches the husk behind. The shield stays up.
     */
    static void shieldbearerBlocksFront(GameTestHelper helper) {
        Shieldbearer bearer = bearer(helper, 6.5, 6.5);
        LivingEntity behind = husk(helper, 9.5, 6.5);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        AbstractArrow plain = shot(helper, player, Items.ARROW, new Vec3(2.5, 2.2, 6.5), new Vec3(2.0, 0.0, 0.0));
        AbstractArrow piercing = shot(helper, player, Items.ARROW, new Vec3(1.5, 1.6, 6.5), new Vec3(2.0, 0.0, 0.0));
        piercing.setPierceLevel((byte) 3);
        helper.runAfterDelay(12, () -> {
            if (lost(bearer) > 0) helper.fail("arrows from the front should stop on the shield, the bearer lost " + lost(bearer));
            else if (lost(behind) > 0) helper.fail("a piercing arrow should stop on the shield too, the husk behind lost " + lost(behind));
            else if (bearer.isLowered()) helper.fail("plain arrows shouldn't lower the shield");
            else if (!plain.isAlive() || !piercing.isAlive()) helper.fail("stopped arrows should fall, not vanish");
            else if (plain.getX() > bearer.getX() || piercing.getX() > bearer.getX()) helper.fail("stopped arrows should fall in front of the shield");
            else helper.succeed();
        });
    }

    /**
     * From the side, from behind, and dropping in over the top at 49 degrees, arrows get past the shield. A Blunt arrow
     * from the side hits the bearer as an arrow and leaves the shield up; a blaze's fireball from the front isn't
     * stopped, it burns the bearer.
     */
    static void shieldbearerHitFromSide(GameTestHelper helper) {
        Shieldbearer side = bearer(helper, 3.5, 3.5);
        Shieldbearer back = bearer(helper, 3.5, 8.5);
        Shieldbearer over = bearer(helper, 8.5, 3.5);
        Shieldbearer blunt = bearer(helper, 8.5, 8.5);
        Shieldbearer fire = bearer(helper, 6.5, 11.5);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        // each faces -X; the side shots come along +Z, the back shot along -X, the high one along +X falling steeply
        shot(helper, player, Items.ARROW, new Vec3(3.5, 2.0, 0.6), new Vec3(0.0, 0.0, 2.0));
        shot(helper, player, Items.ARROW, new Vec3(6.5, 2.0, 8.5), new Vec3(-2.0, 0.0, 0.0));
        shot(helper, player, Items.ARROW, new Vec3(5.9, 4.6, 3.5), new Vec3(1.2, -1.4, 0.0));
        shot(helper, player, ModItems.BLUNT_ARROW.get(), new Vec3(8.5, 2.0, 5.6), new Vec3(0.0, 0.0, 2.0));
        Vec3 from = helper.absoluteVec(new Vec3(4.6, 2.0, 11.5));
        var fireball = new net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball(helper.getLevel(), from.x, from.y, from.z, new Vec3(1.0, 0.0, 0.0));
        helper.getLevel().addFreshEntity(fireball);
        helper.runAfterDelay(12, () -> {
            List<String> missed = new java.util.ArrayList<>();
            if (lost(side) <= 0) missed.add("from the side");
            if (lost(back) <= 0) missed.add("from behind");
            if (lost(over) <= 0) missed.add("over the top");
            if (lost(blunt) <= 0) missed.add("a Blunt arrow from the side");
            if (lost(fire) <= 0 && !fire.isOnFire()) missed.add("a fireball from the front");
            if (!missed.isEmpty()) helper.fail("these should get past the shield " + missed);
            else if (blunt.isLowered()) helper.fail("a Blunt arrow that hits the bearer, not the shield, shouldn't lower the shield");
            else helper.succeed();
        });
    }

    /**
     * A Blunt arrow on the shield knocks it down for 1.5 s and hurts nothing; an arrow from the front lands while it's
     * down, and once it's back up the next is stopped again.
     */
    static void shieldbearerBluntStagger(GameTestHelper helper) {
        Shieldbearer bearer = bearer(helper, 6.5, 6.5);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        shot(helper, player, ModItems.BLUNT_ARROW.get(), new Vec3(2.5, 2.0, 6.5), new Vec3(2.0, 0.0, 0.0));
        helper.runAfterDelay(4, () -> {
            if (!bearer.isLowered()) helper.fail("a Blunt arrow on the shield should knock it down");
            else if (lost(bearer) > 0) helper.fail("the Blunt arrow should hurt nothing through the shield, the bearer lost " + lost(bearer));
            else if (bearer.loweredTicks() > TowerShield.STAGGER_TICKS || bearer.loweredTicks() < TowerShield.STAGGER_TICKS - 5)
                helper.fail("the stagger should last " + TowerShield.STAGGER_TICKS + " ticks, " + bearer.loweredTicks() + " left after 4");
            else shot(helper, player, Items.ARROW, new Vec3(2.5, 2.0, 6.5), new Vec3(2.0, 0.0, 0.0));
        });
        helper.runAfterDelay(12, () -> {
            if (lost(bearer) <= 0) helper.fail("an arrow from the front should land while the shield is down");
        });
        helper.runAfterDelay(TowerShield.STAGGER_TICKS + 4, () -> {
            if (bearer.isLowered()) helper.fail("the shield should be back up after " + TowerShield.STAGGER_TICKS + " ticks");
            else shot(helper, player, Items.ARROW, new Vec3(2.5, 2.0, 6.5), new Vec3(2.0, 0.0, 0.0));
        });
        float[] before = new float[1];
        helper.runAfterDelay(TowerShield.STAGGER_TICKS + 5, () -> before[0] = bearer.getHealth());
        helper.runAfterDelay(TowerShield.STAGGER_TICKS + 16, () -> {
            if (bearer.getHealth() < before[0]) helper.fail("with the shield back up the next arrow should stop on it");
            else helper.succeed();
        });
    }

    /**
     * An Arbalest bolt (shield-piercing) is stopped by the shield, piercing or not, and cracks it down for 3 s: the
     * husk behind is untouched, the bearer too.
     */
    static void shieldbearerBoltCracks(GameTestHelper helper) {
        Shieldbearer bearer = bearer(helper, 6.5, 6.5);
        LivingEntity behind = husk(helper, 9.5, 6.5);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 6.5));
        player.snapTo(at.x, at.y, at.z, -90.0F, 0.0F);
        player.setYHeadRot(-90.0F);
        player.yHeadRotO = -90.0F;
        player.getInventory().setItem(9, new ItemStack(Items.ARROW, 4));
        ItemStack arbalest = new ItemStack(ModItems.ARBALEST.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, arbalest);
        CrossbowItem item = (CrossbowItem) arbalest.getItem();
        item.onUseTick(helper.getLevel(), player, arbalest, item.getUseDuration(arbalest, player) - CrossbowItem.getChargeDuration(arbalest, player));
        if (!CrossbowItem.isCharged(arbalest)) throw helper.assertionException("the Arbalest didn't load");
        item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.runAfterDelay(8, () -> {
            if (lost(bearer) > 0 || lost(behind) > 0)
                helper.fail("the shield should stop the bolt: the bearer lost " + lost(bearer) + ", the husk behind " + lost(behind));
            else if (!bearer.isLowered()) helper.fail("an Arbalest bolt on the shield should crack it down");
            else if (bearer.loweredTicks() > TowerShield.CRACK_TICKS || bearer.loweredTicks() < TowerShield.CRACK_TICKS - 10)
                helper.fail("the crack should last " + TowerShield.CRACK_TICKS + " ticks, " + bearer.loweredTicks() + " left after 8");
            else helper.succeed();
        });
    }

    /**
     * While the shield is down the bearer stands dazed: with a target 3 blocks off it neither walks nor bashes. Once
     * the shield is back up it walks over and bashes.
     */
    static void shieldbearerDazedWhileDown(GameTestHelper helper) {
        Shieldbearer bearer = helper.spawn(ModEntities.SHIELDBEARER.get(), new Vec3(3.5, 1, 6.5));
        LivingEntity husk = husk(helper, 6.5, 6.5);
        bearer.lowerShield(TowerShield.STAGGER_TICKS, Shieldbearer.KNOCKED);
        bearer.setTarget(husk);
        Vec3 start = bearer.position();
        helper.runAfterDelay(TowerShield.STAGGER_TICKS - 2, () -> {
            if (bearer.position().distanceTo(start) > 0.3) helper.fail("a dazed bearer should stand still, it moved " + bearer.position().distanceTo(start));
            else if (lost(husk) > 0) helper.fail("a dazed bearer shouldn't bash");
        });
        helper.runAfterDelay(TowerShield.STAGGER_TICKS + 1, () -> bearer.setTarget(husk));
        helper.succeedWhen(() -> {
            if (lost(husk) <= 0) throw helper.assertionException("with its shield back up it should walk over and bash");
        });
    }

    /**
     * In a fight the bearer turns its shield at {@link TowerShield#TURN_DEGREES} a tick, however far its head is
     * turned; out of one it walks facing where it goes, as any mob.
     */
    static void shieldbearerTurnsSlowlyInAFight(GameTestHelper helper) {
        Shieldbearer fighting = helper.spawnWithNoFreeWill(ModEntities.SHIELDBEARER.get(), new Vec3(3.5, 1, 3.5));
        for (Shieldbearer b : List.of(fighting)) {
            b.setYRot(0.0F);
            b.setYHeadRot(0.0F);
            b.yBodyRot = 0.0F;
            b.yBodyRotO = 0.0F;
        }
        fighting.setAggressive(true);
        // something to watch off its left: yaw 90 (it faces +Z at yaw 0)
        LivingEntity watched = husk(helper, 0.5, 3.5);
        helper.onEachTick(() -> fighting.getLookControl().setLookAt(watched));
        Shieldbearer walking = helper.spawnWithNoFreeWill(ModEntities.SHIELDBEARER.get(), new Vec3(8.5, 1, 1.5));
        walking.setYRot(90.0F);
        walking.setYHeadRot(90.0F);
        walking.yBodyRot = 90.0F;
        // a path is only made once the mob stands on the ground, a tick or two after it spawns
        Vec3 to = helper.absoluteVec(new Vec3(8.5, 1, 10.5)), walkFrom = walking.position();
        helper.runAfterDelay(3, () -> walking.getNavigation().moveTo(to.x, to.y, to.z, 1.0));
        float[] at10 = new float[1];
        helper.runAfterDelay(10, () -> at10[0] = fighting.yBodyRot);
        helper.runAfterDelay(25, () -> {
            float walkingOff = Math.abs(net.minecraft.util.Mth.wrapDegrees(walking.yBodyRot));
            if (at10[0] < 10 * TowerShield.TURN_DEGREES - 6 || at10[0] > 10 * TowerShield.TURN_DEGREES + 6)
                helper.fail("in a fight the body should turn " + TowerShield.TURN_DEGREES + " a tick: " + at10[0] + " after 10 ticks");
            else if (walking.position().distanceTo(walkFrom) < 1.0) helper.fail("the walking bearer should have walked, it moved " + walking.position().distanceTo(walkFrom));
            else if (walkingOff > 20) helper.fail("out of a fight it should walk facing ahead (+Z, yaw 0), its body is " + walking.yBodyRot);
            else if (fighting.yBodyRot < 25 * TowerShield.TURN_DEGREES - 8) helper.fail("it should keep turning: " + fighting.yBodyRot + " after 25 ticks");
            else helper.succeed();
        });
    }

    /** A shield that is down stays down through a save and load, cracked or knocked, with the time it had left; a raised one saves nothing. */
    static void shieldbearerShieldSaves(GameTestHelper helper) {
        var level = helper.getLevel();
        Shieldbearer cracked = bearer(helper, 3.5, 3.5);
        Shieldbearer knocked = bearer(helper, 6.5, 3.5);
        Shieldbearer raised = bearer(helper, 9.5, 3.5);
        cracked.lowerShield(TowerShield.CRACK_TICKS, Shieldbearer.CRACKED);
        knocked.lowerShield(TowerShield.STAGGER_TICKS, Shieldbearer.KNOCKED);
        List<String> wrong = new java.util.ArrayList<>();
        for (Shieldbearer b : List.of(cracked, knocked, raised)) {
            var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
            b.saveWithoutId(out);
            Shieldbearer copy = ModEntities.SHIELDBEARER.get().create(level, EntitySpawnReason.LOAD);
            if (copy == null) throw helper.assertionException("couldn't make a Shieldbearer");
            copy.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), out.buildResult()));
            if (copy.isLowered() != b.isLowered() || copy.isCracked() != b.isCracked() || copy.loweredTicks() != b.loweredTicks())
                wrong.add("lowered " + b.isLowered() + "/" + copy.isLowered() + ", cracked " + b.isCracked() + "/" + copy.isCracked()
                        + ", ticks " + b.loweredTicks() + "/" + copy.loweredTicks());
        }
        if (!wrong.isEmpty()) helper.fail("the shield should load as it was saved: " + wrong);
        else helper.succeed();
    }

    /** Up close it bashes: 5 damage (through a husk's 2 armour, vanilla's formula: 4.92) and a shove the way it faces. */
    static void shieldbearerBashes(GameTestHelper helper) {
        Shieldbearer bearer = bearer(helper, 6.5, 6.5);
        LivingEntity husk = husk(helper, 5.5, 6.5);
        Vec3 from = husk.position();
        if (!bearer.doHurtTarget(helper.getLevel(), husk)) throw helper.assertionException("the bash should land");
        double expected = 5.0 * (1.0 - Math.max(2.0 / 5.0, 2.0 - 5.0 / 2.0) / 25.0);
        if (Math.abs(lost(husk) - expected) > 0.01) helper.fail("the bash should deal " + expected + ", dealt " + lost(husk));
        else if (husk.getDeltaMovement().horizontalDistance() < 0.2) helper.fail("the bash should shove, motion " + husk.getDeltaMovement());
        else helper.runAfterDelay(1, () -> {
            if (from.x - husk.position().x <= 0) helper.fail("the bash should push the husk away from the bearer");
            else helper.succeed();
        });
    }

    /**
     * A raid's third wave brings Shieldbearers (as many as the level's difficulty sends), raiders of the wave like
     * vanilla's; the second brings none. The wave is spawned through vanilla's own spawnGroup and cleared at once.
     */
    static void shieldbearersJoinRaidWaves(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(6, 1, 6));
        try {
            var spawnGroup = Raid.class.getDeclaredMethod("spawnGroup", net.minecraft.server.level.ServerLevel.class, BlockPos.class);
            var groups = Raid.class.getDeclaredField("groupsSpawned");
            spawnGroup.setAccessible(true);
            groups.setAccessible(true);
            List<String> wrong = new java.util.ArrayList<>();
            for (int wave = 2; wave <= 3; wave++) {
                Raid raid = new Raid(pos, Difficulty.HARD);
                groups.setInt(raid, wave - 1);
                spawnGroup.invoke(raid, helper.getLevel(), pos);
                List<Raider> raiders = List.copyOf(raid.getAllRaiders());
                long bearers = raiders.stream().filter(r -> r instanceof Shieldbearer).count();
                boolean joined = raiders.stream().filter(r -> r instanceof Shieldbearer).allMatch(r -> r.getCurrentRaid() == raid && r.getWave() == raid.getGroupsSpawned());
                int difficulty = helper.getLevel().getDifficulty().getId();
                long fewest = TowerShield.perWave(wave, difficulty, 0.0), most = TowerShield.perWave(wave, difficulty, 0.99);
                if (bearers < fewest || bearers > most || !joined)
                    wrong.add("wave " + wave + ": " + bearers + " Shieldbearers (expected " + fewest + " to " + most + "), joined " + joined);
                raiders.forEach(r -> {
                    r.getPassengers().forEach(net.minecraft.world.entity.Entity::discard);
                    r.discard();
                });
            }
            // with the server's switch off, a third wave brings none
            FullDrawConfig.Source before = FullDrawConfig.source();
            FullDrawConfig.use(spawnsOff(before));
            try {
                Raid raid = new Raid(pos, Difficulty.HARD);
                groups.setInt(raid, 2);
                spawnGroup.invoke(raid, helper.getLevel(), pos);
                List<Raider> raiders = List.copyOf(raid.getAllRaiders());
                if (raiders.stream().anyMatch(r -> r instanceof Shieldbearer)) wrong.add("with shieldbearerSpawns off, the third wave still brought one");
                raiders.forEach(r -> {
                    r.getPassengers().forEach(net.minecraft.world.entity.Entity::discard);
                    r.discard();
                });
            } finally {
                FullDrawConfig.use(before);
            }
            if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
            else helper.succeed();
        } catch (ReflectiveOperationException e) {
            throw helper.assertionException("couldn't reach vanilla's raid wave: " + e);
        }
    }

    /**
     * At a pillager outpost one spawn in ten is a Shieldbearer, the pillager keeps the rest, and vanilla's outpost file
     * is untouched; other spawn lists stay as they are. Its spawn rule keeps one at a time: a spot that passes alone
     * fails with a Shieldbearer within 48 blocks. With the server's switch off, neither outposts nor the rule bring one.
     */
    static void shieldbearerOutpostSpawns(GameTestHelper helper) {
        var level = helper.getLevel();
        var outpost = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(BuiltinStructures.PILLAGER_OUTPOST);
        var own = outpost == null ? null : outpost.spawnOverrides().get(MobCategory.MONSTER);
        if (own == null) throw helper.assertionException("the pillager outpost has no monster spawns");
        if (own.spawns().unwrap().stream().anyMatch(s -> s.value().type() == ModEntities.SHIELDBEARER.get()))
            throw helper.assertionException("the outpost's own file should be vanilla's, without the Shieldbearer: " + own.spawns().unwrap());
        var spawns = ModEntities.outpostSpawns(level.structureManager(), MobCategory.MONSTER, own.spawns()).unwrap();
        int total = spawns.stream().mapToInt(net.minecraft.util.random.Weighted::weight).sum();
        int bearers = spawns.stream().filter(s -> s.value().type() == ModEntities.SHIELDBEARER.get()).mapToInt(net.minecraft.util.random.Weighted::weight).sum();
        int pillagers = spawns.stream().filter(s -> s.value().type() == EntityTypes.PILLAGER).mapToInt(net.minecraft.util.random.Weighted::weight).sum();
        if (bearers * TowerShield.OUTPOST_SHARE_IN != total || pillagers + bearers != total)
            throw helper.assertionException("at an outpost one spawn in " + TowerShield.OUTPOST_SHARE_IN + " should be a Shieldbearer and the rest pillagers: " + spawns);
        var elsewhere = net.minecraft.util.random.WeightedList.of(new net.minecraft.world.level.biome.MobSpawnSettings.SpawnerData(EntityTypes.ZOMBIE,
                net.minecraft.util.valueproviders.ConstantInt.of(1)));
        if (ModEntities.outpostSpawns(level.structureManager(), MobCategory.MONSTER, elsewhere) != elsewhere)
            throw helper.assertionException("spawns anywhere but an outpost shouldn't change");
        if (ModEntities.outpostSpawns(level.structureManager(), MobCategory.CREATURE, own.spawns()) != own.spawns())
            throw helper.assertionException("only the outpost's monsters should change");
        // grass under, nothing lit: a spot any monster could spawn on. The lone one is 120 blocks up, out of reach of
        // the Shieldbearers in the tests running beside this one
        BlockPos high = helper.absolutePos(new BlockPos(3, 120, 3));
        level.setBlockAndUpdate(high.below(), net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState());
        boolean alone = SpawnPlacements.checkSpawnRules(ModEntities.SHIELDBEARER.get(), level, EntitySpawnReason.NATURAL, high, level.getRandom());
        FullDrawConfig.Source before = FullDrawConfig.source();
        FullDrawConfig.use(spawnsOff(before));
        boolean aloneOff, listOff;
        try {
            aloneOff = SpawnPlacements.checkSpawnRules(ModEntities.SHIELDBEARER.get(), level, EntitySpawnReason.NATURAL, high, level.getRandom());
            listOff = ModEntities.outpostSpawns(level.structureManager(), MobCategory.MONSTER, own.spawns()) != own.spawns();
        } finally {
            FullDrawConfig.use(before);
        }
        level.setBlockAndUpdate(high.below(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        helper.setBlock(new BlockPos(3, 0, 3), net.minecraft.world.level.block.Blocks.GRASS_BLOCK);
        BlockPos spot = helper.absolutePos(new BlockPos(3, 1, 3));
        bearer(helper, 9.5, 9.5);
        boolean second = SpawnPlacements.checkSpawnRules(ModEntities.SHIELDBEARER.get(), level, EntitySpawnReason.NATURAL, spot, level.getRandom());
        boolean egg = SpawnPlacements.checkSpawnRules(ModEntities.SHIELDBEARER.get(), level, EntitySpawnReason.SPAWN_ITEM_USE, spot, level.getRandom());
        if (!alone) helper.fail("a Shieldbearer should be able to spawn at an outpost with none about (difficulty " + level.getDifficulty()
                + ", block light " + level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, high) + ")");
        else if (second) helper.fail("a second Shieldbearer shouldn't spawn within 48 blocks of one");
        else if (!egg) helper.fail("the one-at-a-time rule is for spawns on their own, not eggs");
        else if (aloneOff || listOff) helper.fail("with shieldbearerSpawns off, outposts shouldn't bring one (rule " + aloneOff + ", list " + listOff + ")");
        else helper.succeed();
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    /** The loader's config with Shieldbearer spawns switched off. */
    private static FullDrawConfig.Source spawnsOff(FullDrawConfig.Source before) {
        return new FullDrawConfig.Source() {
            @Override public boolean vanillaCritOnFullDraw() { return before.vanillaCritOnFullDraw(); }
            @Override public com.panda.fulldraw.logic.ShotMath.Tuning tuning() { return before.tuning(); }
            @Override public boolean blastArrowBreaksBlocks() { return before.blastArrowBreaksBlocks(); }
            @Override public int ropeArrowLength() { return before.ropeArrowLength(); }
            @Override public double arrowRecoveryChance() { return before.arrowRecoveryChance(); }
            @Override public int arrowPickupLifetimeSeconds() { return before.arrowPickupLifetimeSeconds(); }
            @Override public boolean shieldbearerSpawns() { return false; }
        };
    }

    /** A Shieldbearer facing -X, its goals cleared so it holds still. */
    private static Shieldbearer bearer(GameTestHelper helper, double x, double z) {
        Shieldbearer bearer = helper.spawnWithNoFreeWill(ModEntities.SHIELDBEARER.get(), new Vec3(x, 1, z));
        bearer.setYRot(90.0F);
        bearer.setYHeadRot(90.0F);
        bearer.yBodyRot = 90.0F;
        bearer.yBodyRotO = 90.0F;
        return bearer;
    }

    private static LivingEntity husk(GameTestHelper helper, double x, double z) {
        Mob husk = helper.spawnWithNoFreeWill(EntityTypes.HUSK, new Vec3(x, 1, z));
        for (EquipmentSlot slot : EquipmentSlot.values()) husk.setItemSlot(slot, ItemStack.EMPTY);
        return husk;
    }

    /** An arrow of this item from the player, at a relative point, moving without gravity. */
    private static AbstractArrow shot(GameTestHelper helper, Player player, net.minecraft.world.item.Item ammo, Vec3 at, Vec3 motion) {
        ItemStack stack = new ItemStack(ammo);
        AbstractArrow arrow = ((ArrowItem) ammo).createArrow(helper.getLevel(), stack, player, null);
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
