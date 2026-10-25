package com.panda.fulldraw.gametest;

import com.panda.fulldraw.FullDrawConfig;
import com.panda.fulldraw.FullDrawMod;
import com.panda.fulldraw.entity.BogLurker;
import com.panda.fulldraw.logic.LurkerRules;
import com.panda.fulldraw.registry.ModEntities;
import com.panda.fulldraw.registry.ModItems;
import com.panda.fulldraw.shot.ShotHolder;
import com.panda.fulldraw.shot.ShotRecord;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The Bog Lurker: its eyes as a weak spot at ×2 and its body through the water at half; it spits at a mob in range and
 * holds its fire inside it; a player close by sends it under and away, and it comes back up; its state through a save;
 * where it spawns and what it drops.
 *
 * <p>Each test that needs water pours a pond over the arena floor, three deep inside a stone bank: water set in a test
 * flows, and would spread over the dry side.
 */
final class LurkerGameTests {
    private LurkerGameTests() {}

    /** The top water block of the pond, and the height of its surface (a source block is 8/9 full). */
    private static final int POND_TOP = 3;
    private static final double SURFACE = POND_TOP + 8.0 / 9.0;

    /**
     * An arrow from a player into a lurking lurker's eyes, which stand above the water, is a precision hit at ×2: twice
     * what the same arrow does to the body of one on land. The same arrow into the body of one under the water does
     * half. All three arrows fly level and fast from 2.4 blocks off, so they meet the same pipeline but for where they
     * strike.
     */
    static void bogLurkerEyeWeakSpot(GameTestHelper helper) {
        pond(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BogLurker eyes = still(helper, new Vec3(3.5, lurkY(), 8.5));
        BogLurker underwater = still(helper, new Vec3(8.5, lurkY(), 8.5));
        BogLurker land = still(helper, new Vec3(2.5, 1.0, 1.5));
        double eyeY = lurkY() + 0.6, bodyY = lurkY() + 0.25;
        // the lurkers face -z (yaw 180), toward arrows coming down +z
        AbstractArrow atEyes = shot(helper, player, new Vec3(3.5, eyeY, 8.5 - 0.45 - 2.4), new Vec3(0.0, 0.0, 3.0));
        AbstractArrow atBody = shot(helper, player, new Vec3(8.5, bodyY, 8.5 - 0.45 - 2.4), new Vec3(0.0, 0.0, 3.0));
        helper.runAfterDelay(4, () -> shot(helper, player, new Vec3(2.5 + 0.45 + 2.4, 1.25, 1.5), new Vec3(-3.0, 0.0, 0.0)));
        helper.runAfterDelay(8, () -> {
            float e = lost(eyes), u = lost(underwater), l = lost(land);
            if (e <= 0 || u <= 0 || l <= 0)
                helper.fail("every arrow should hit: eyes " + e + " (" + atEyes.position() + "), under water " + u + " (" + atBody.position() + "), on land " + l);
            else if (Math.abs(e - 2.0F * l) > 1.0F) helper.fail("the eyes should take twice the body's damage: eyes " + e + ", body on land " + l);
            else if (Math.abs(u - 0.5F * l) > 1.0F) helper.fail("the body under water should take half: under water " + u + ", on land " + l);
            else helper.succeed();
        });
    }

    /**
     * Any other projectile (a skeleton's arrow, with no shot of a player's behind it) does half to its body under the
     * water, against the same arrow into one on land.
     */
    static void bogLurkerBodyThroughWater(GameTestHelper helper) {
        pond(helper);
        BogLurker underwater = still(helper, new Vec3(6.5, lurkY(), 8.5));
        BogLurker land = still(helper, new Vec3(2.5, 1.0, 1.5));
        plainArrow(helper, new Vec3(6.5, lurkY() + 0.25, 8.5 - 0.45 - 1.0), new Vec3(0.0, 0.0, 2.0));
        plainArrow(helper, new Vec3(2.5 + 0.45 + 1.0, 1.25, 1.5), new Vec3(-2.0, 0.0, 0.0));
        helper.runAfterDelay(6, () -> {
            float u = lost(underwater), l = lost(land);
            if (u <= 0 || l <= 0) helper.fail("both arrows should hit: under water " + u + ", on land " + l);
            else if (Math.abs(u - 0.5F * l) > 0.5F) helper.fail("under water its body should take half: " + u + " against " + l);
            else helper.succeed();
        });
    }

    /**
     * A lurker with an iron golem on the bank 9 blocks off as its target swells its throat a second after it is given it,
     * spits, and the glob strikes the golem for 2 (credited to the lurker) and leaves Slowness I behind. The next tell
     * comes 2.5 to 3.5 s after that one ended.
     */
    static void bogLurkerSpitsInRange(GameTestHelper helper) {
        pond(helper);
        // on the bank, as high as the water's surface
        for (int x = 5; x <= 7; x++) for (int z = 0; z <= 2; z++) for (int y = 1; y <= POND_TOP; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        Mob golem = helper.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, new Vec3(6.5, POND_TOP + 1, 1.5));
        golem.setNoAi(true);
        BogLurker lurker = helper.spawn(ModEntities.BOG_LURKER.get(), new Vec3(6.5, lurkY(), 10.5));
        lurker.setTarget(golem);
        List<Integer> tells = new ArrayList<>();
        int[] tick = {0};
        float[] firstStrike = {0.0F};
        boolean[] telling = {false};
        helper.onEachTick(() -> {
            tick[0]++;
            boolean now = lurker.phase() == BogLurker.Phase.TELL;
            if (now && !telling[0]) tells.add(tick[0]);
            telling[0] = now;
            if (firstStrike[0] == 0.0F) firstStrike[0] = lost(golem);
        });
        helper.runAfterDelay(150, () -> {
            var slow = golem.getEffect(MobEffects.SLOWNESS);
            int gapMin = LurkerRules.SPIT_TELL_TICKS + LurkerRules.SPIT_GAP_MIN, gapMax = LurkerRules.SPIT_TELL_TICKS + LurkerRules.SPIT_GAP_MAX + 2;
            if (tells.isEmpty()) helper.fail("it should swell its throat before it spits");
            else if (tells.get(0) < 18 || tells.get(0) > 24) helper.fail("its first tell should come a second after it has a target, it came on tick " + tells.get(0));
            else if (tells.size() < 2 || tells.get(1) - tells.get(0) < gapMin || tells.get(1) - tells.get(0) > gapMax)
                helper.fail("the next tell should come " + gapMin + " to " + gapMax + " ticks after the first, tells came on ticks " + tells);
            else if (Math.abs(firstStrike[0] - LurkerRules.SPIT_DAMAGE) > 1.0E-3) helper.fail("the glob should strike for 2, the golem lost " + firstStrike[0]);
            else if (golem.getLastHurtByMob() != lurker) helper.fail("the strike should be the lurker's, it was " + golem.getLastHurtByMob());
            else if (slow == null || slow.getAmplifier() != 0) helper.fail("the glob should leave Slowness I, it left " + slow);
            else helper.succeed();
        });
    }

    /**
     * A target 6 blocks off is inside its spitting range: it never starts a spit closer than
     * {@link LurkerRules#SPIT_MIN}, and it backs away across the water (to where it may spit again).
     */
    static void bogLurkerHoldsFireTooClose(GameTestHelper helper) {
        pond(helper);
        Mob pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, new Vec3(6.5, 1, 1.5));
        pig.setNoAi(true);
        BogLurker lurker = helper.spawn(ModEntities.BOG_LURKER.get(), new Vec3(6.5, lurkY(), 7.5));
        lurker.setTarget(pig);
        double start = lurker.distanceTo(pig);
        double[] closestTell = {Double.MAX_VALUE};
        helper.onEachTick(() -> {
            if (lurker.phase() == BogLurker.Phase.TELL) closestTell[0] = Math.min(closestTell[0], lurker.distanceTo(pig));
        });
        helper.runAfterDelay(60, () -> {
            if (closestTell[0] < LurkerRules.SPIT_MIN) helper.fail("it started a spit " + closestTell[0] + " blocks off, inside its range");
            else if (lurker.distanceTo(pig) < start + 1.0) helper.fail("it should back away: from " + start + " to " + lurker.distanceTo(pig));
            else helper.succeed();
        });
    }

    /**
     * A player 3 blocks off sends it under: it sinks below the surface and swims away at its full speed, then, far enough off or after 4 s,
     * comes back up to lurk with the top of its box {@link LurkerRules#SHOWS_ABOVE} over the surface again. The pond is
     * too small to get 10 blocks away, so here it is the time that brings it up.
     */
    static void bogLurkerFleesClose(GameTestHelper helper) {
        pond(helper);
        for (int y = 1; y <= POND_TOP; y++) helper.setBlock(new BlockPos(6, y, 1), Blocks.STONE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.snapTo(helper.absoluteVec(new Vec3(6.5, POND_TOP + 1, 1.5)));
        BogLurker lurker = helper.spawn(ModEntities.BOG_LURKER.get(), new Vec3(6.5, lurkY(), 4.5));
        lurker.setTarget(player);
        double start = lurker.distanceTo(player);
        double surface = helper.absoluteVec(new Vec3(0, SURFACE, 0)).y;
        double[] deepest = {Double.MAX_VALUE};
        int[] fleeTicks = {0};
        double[] endedAt = {-1.0};
        // where it is 10 and 20 ticks into its flee, for its swimming speed across the pond
        Vec3[] swum = new Vec3[2];
        helper.onEachTick(() -> {
            if (lurker.phase() == BogLurker.Phase.FLEE) {
                fleeTicks[0]++;
                if (fleeTicks[0] == 10) swum[0] = lurker.position();
                if (fleeTicks[0] == 20) swum[1] = lurker.position();
            }
            else if (fleeTicks[0] > 0 && endedAt[0] < 0.0) endedAt[0] = lurker.distanceTo(player);
            deepest[0] = Math.min(deepest[0], lurker.getBoundingBox().maxY - surface);
        });
        helper.runAfterDelay(30, () -> {
            if (fleeTicks[0] == 0) helper.fail("a player " + String.format("%.1f", start) + " blocks off should send it fleeing, it is " + lurker.phase().id());
            else if (deepest[0] > -0.5) helper.fail("fleeing, it should sink under the surface; the top of it was at most " + deepest[0] + " from it");
            else if (lurker.distanceTo(player) < start + 2.0) helper.fail("it should swim away: from " + start + " to " + lurker.distanceTo(player));
            else if (swum[1] == null || swum[1].subtract(swum[0]).horizontalDistance() / 10.0 < 0.9 * LurkerRules.SWIM_SPEED)
                helper.fail("it should swim away at " + LurkerRules.SWIM_SPEED + " a tick, the water's drag and all: "
                        + (swum[1] == null ? "it stopped fleeing" : swum[1].subtract(swum[0]).horizontalDistance() / 10.0));
        });
        helper.runAfterDelay(LurkerRules.FLEE_MAX_TICKS + 70, () -> {
            double top = lurker.getBoundingBox().maxY - surface;
            // back up, it may already be swelling for a spit at the player now in range
            if (lurker.phase() == BogLurker.Phase.FLEE) helper.fail("it should come back up to lurk, it is still fleeing");
            else if (endedAt[0] < LurkerRules.FLEE_UNTIL && fleeTicks[0] < LurkerRules.FLEE_MAX_TICKS)
                helper.fail("it came up after " + fleeTicks[0] + " ticks, " + endedAt[0] + " blocks off: neither far enough nor long enough");
            else if (Math.abs(top - LurkerRules.SHOWS_ABOVE) > 0.05) helper.fail("back up, the top of it should be " + LurkerRules.SHOWS_ABOVE + " over the surface, it is " + top);
            else helper.succeed();
        });
    }

    /** Through a save and load one fleeing keeps fleeing, with its time, and one in its tell comes back lurking. */
    static void bogLurkerSaves(GameTestHelper helper) {
        pond(helper);
        ServerLevel level = helper.getLevel();
        BogLurker telling = still(helper, new Vec3(3.5, lurkY(), 8.5));
        telling.startTell();
        List<String> wrong = new ArrayList<>();
        for (BogLurker l : List.of(telling)) {
            var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
            l.saveWithoutId(out);
            BogLurker copy = ModEntities.BOG_LURKER.get().create(level, EntitySpawnReason.LOAD);
            if (copy == null) throw helper.assertionException("couldn't make a Bog Lurker");
            copy.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), out.buildResult()));
            if (copy.phase() != BogLurker.Phase.LURK || copy.nextSpit() != l.nextSpit())
                wrong.add(l.phase().id() + " came back " + copy.phase().id() + " (next spit " + l.nextSpit() + "/" + copy.nextSpit() + ")");
        }
        // a fleeing one, made by a player close by
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.snapTo(helper.absoluteVec(new Vec3(9.5, POND_TOP + 1, 5.5)));
        BogLurker fleeing = helper.spawn(ModEntities.BOG_LURKER.get(), new Vec3(9.5, lurkY(), 7.5));
        fleeing.setTarget(player);
        helper.runAfterDelay(5, () -> {
            if (fleeing.phase() != BogLurker.Phase.FLEE) {
                helper.fail("set up: it should be fleeing, it is " + fleeing.phase().id());
                return;
            }
            var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
            fleeing.saveWithoutId(out);
            BogLurker copy = ModEntities.BOG_LURKER.get().create(level, EntitySpawnReason.LOAD);
            if (copy == null) throw helper.assertionException("couldn't make a Bog Lurker");
            copy.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), out.buildResult()));
            if (copy.phase() != BogLurker.Phase.FLEE || copy.phaseTicks() != fleeing.phaseTicks())
                wrong.add("flee came back " + copy.phase().id() + " (ticks " + fleeing.phaseTicks() + "/" + copy.phaseTicks() + ")");
            if (!wrong.isEmpty()) helper.fail("a lurker should load as it was saved: " + wrong);
            else helper.succeed();
        });
    }

    /**
     * Its home is the swamps (the biome tag NeoForge's modifier and Fabric's code both read), at weight
     * {@link ModEntities#LURKER_WEIGHT} in groups of 1 or 2. On its own it spawns only in water, by day or night, with
     * fewer than {@link ModEntities#LURKER_CAP} about and the server's switch on, and never in water under a roof; a spawn
     * egg works on land. Runs alone, under the open sky: it counts every lurker within
     * {@link ModEntities#LURKER_CAP_RADIUS} blocks.
     */
    static void bogLurkerSpawnRules(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var access = level.registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(ModEntities.LURKER_BIOMES);
        List<ResourceKey<Biome>> homes = List.of(Biomes.SWAMP, Biomes.MANGROVE_SWAMP);
        if (tag.isEmpty() || tag.get().size() != homes.size() || !homes.stream().allMatch(k -> tag.get().stream().anyMatch(b -> b.is(k))))
            throw helper.assertionException("its biome tag should hold the swamps: " + tag.map(t -> t.stream().map(b -> b.getRegisteredName()).toList()));
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, FullDrawMod.id("spawns/bog_lurker")));
        if (modifier.isEmpty() || !(modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier spawns))
            throw helper.assertionException("it should have a NeoForge spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = spawns.spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModEntities.BOG_LURKER.get()).findFirst().orElse(null);
        if (entry == null || entry.weight() != ModEntities.LURKER_WEIGHT || entry.value().count().minInclusive() != 1 || entry.value().count().maxInclusive() != 2)
            throw helper.assertionException("the modifier should spawn it at weight " + ModEntities.LURKER_WEIGHT + " in groups of 1 to 2: " + entry);
        List<String> without = new ArrayList<>();
        for (ResourceKey<Biome> home : homes) {
            MobSpawnSettings settings = access.lookupOrThrow(Registries.BIOME).getOrThrow(home).value().getModifiedEnvironmentAttributes()
                    .applyModifier(net.minecraft.world.attribute.EnvironmentAttributes.NATURAL_MOB_SPAWNS, MobSpawnSettings.EMPTY);
            if (settings.getMobsInCategory(net.minecraft.world.entity.MobCategory.MONSTER).unwrap().stream()
                    .noneMatch(w -> w.value().type() == ModEntities.BOG_LURKER.get() && w.weight() == ModEntities.LURKER_WEIGHT))
                without.add(home.identifier().getPath());
        }
        if (!without.isEmpty()) throw helper.assertionException("these biomes' monster spawns should list it: " + without);
        // two columns of water two deep in stone rings (water set in a test flows), the second under a stone roof as a
        // flooded cave's would be
        for (int y = 1; y <= 2; y++)
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    var block = dx == 0 && dz == 0 ? Blocks.WATER : Blocks.STONE;
                    helper.setBlock(new BlockPos(3 + dx, y, 3 + dz), block);
                    helper.setBlock(new BlockPos(8 + dx, y, 3 + dz), block);
                }
        helper.setBlock(new BlockPos(8, 3, 3), Blocks.STONE);
        BlockPos water = helper.absolutePos(new BlockPos(3, 1, 3)), cave = helper.absolutePos(new BlockPos(8, 1, 3));
        BlockPos land = helper.absolutePos(new BlockPos(8, 1, 8));
        // the sky check reads the sky light, which follows the roof some ticks later (more under load): the checks wait
        // until the roofed pool no longer sees the sky
        BlockPos overCave = helper.absolutePos(new BlockPos(8, 3, 3));
        boolean[] settled = {false};
        helper.onEachTick(() -> {
            if (settled[0] || level.canSeeSky(overCave)) return;
            settled[0] = true;
            // a lurker from an earlier test would count toward the cap
            level.getEntitiesOfClass(BogLurker.class, new net.minecraft.world.phys.AABB(water).inflate(ModEntities.LURKER_CAP_RADIUS)).forEach(BogLurker::discard);
            List<String> wrong = new ArrayList<>();
            if (!rule(level, EntitySpawnReason.NATURAL, water)) wrong.add("in water it should spawn");
            if (rule(level, EntitySpawnReason.NATURAL, cave)) wrong.add("in water under a roof it shouldn't");
            if (rule(level, EntitySpawnReason.NATURAL, land)) wrong.add("on land it shouldn't");
            // the placement the spawner checks first (in water, as the drowned), and room for its box there
            if (!SpawnPlacements.isSpawnPositionOk(ModEntities.BOG_LURKER.get(), level, water)) wrong.add("its placement should take water");
            if (SpawnPlacements.isSpawnPositionOk(ModEntities.BOG_LURKER.get(), level, land)) wrong.add("its placement shouldn't take dry land");
            BogLurker placed = ModEntities.BOG_LURKER.get().create(level, EntitySpawnReason.NATURAL);
            if (placed == null) throw helper.assertionException("couldn't make a lurker");
            placed.snapTo(water.getX() + 0.5, water.getY(), water.getZ() + 0.5, 0.0F, 0.0F);
            if (!placed.checkSpawnObstruction(level)) wrong.add("a lurker in the water should have room");
            if (!rule(level, EntitySpawnReason.SPAWN_ITEM_USE, land)) wrong.add("a spawn egg should work on land");
            FullDrawConfig.Source before = FullDrawConfig.source();
            FullDrawConfig.use(lurkersOff(before));
            try {
                if (rule(level, EntitySpawnReason.NATURAL, water)) wrong.add("with bogLurkerSpawns off it shouldn't");
            } finally {
                FullDrawConfig.use(before);
            }
            List<BogLurker> about = new ArrayList<>();
            for (int i = 0; i < ModEntities.LURKER_CAP; i++) about.add(still(helper, new Vec3(6.5 + i, 1, 6.5)));
            if (rule(level, EntitySpawnReason.NATURAL, water)) wrong.add("with " + ModEntities.LURKER_CAP + " about it shouldn't");
            about.forEach(BogLurker::discard);
            if (!wrong.isEmpty()) helper.fail("spawn rule: " + wrong);
            else helper.succeed();
        });
    }

    /**
     * Over 400 deaths: a Bog Eye half the time (0 and 1 both seen) and 0 to 2 slimeballs (each seen); killed with a
     * Looting III sword, 3 or 4 Bog Eyes (both seen).
     */
    static void bogLurkerDrops(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BogLurker lurker = still(helper, new Vec3(6.5, 1, 6.5));
        LootTable table = level.getServer().reloadableRegistries().getLootTable(lurker.getType().getDefaultLootTable().orElseThrow());
        LootParams plain = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, lurker)
                .withParameter(LootContextParams.ORIGIN, lurker.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic())
                .create(LootContextParamSets.ENTITY);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING), 3);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, sword);
        LootParams looting = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, lurker)
                .withParameter(LootContextParams.ORIGIN, lurker.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().playerAttack(player))
                .withParameter(LootContextParams.ATTACKING_ENTITY, player)
                .withParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, player)
                .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player)
                .create(LootContextParamSets.ENTITY);
        RandomSource random = RandomSource.create(20261025L);
        int[] eyes = new int[3], slime = new int[4], lootedEyes = new int[6];
        List<String> other = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            int e = 0, s = 0;
            for (ItemStack stack : table.getRandomItems(plain, random)) {
                if (stack.isEmpty()) continue;
                if (stack.is(ModItems.BOG_EYE.get())) e += stack.getCount();
                else if (stack.is(Items.SLIME_BALL)) s += stack.getCount();
                else other.add(stack.toString());
            }
            eyes[Math.min(e, 2)]++;
            slime[Math.min(s, 3)]++;
            int le = 0;
            for (ItemStack stack : table.getRandomItems(looting, random)) if (stack.is(ModItems.BOG_EYE.get())) le += stack.getCount();
            lootedEyes[Math.min(le, 5)]++;
        }
        lurker.discard();
        if (!other.isEmpty()) helper.fail("it should drop only Bog Eyes and slimeballs, also dropped " + other);
        else if (eyes[2] > 0 || eyes[0] == 0 || eyes[1] == 0)
            helper.fail("Bog Eyes per death, 0 to 2+: " + java.util.Arrays.toString(eyes) + "; expected 0 or 1, both seen");
        else if (slime[3] > 0 || slime[0] == 0 || slime[1] == 0 || slime[2] == 0)
            helper.fail("slimeballs per death, 0 to 3+: " + java.util.Arrays.toString(slime) + "; expected 0 to 2, each seen");
        else if (lootedEyes[3] == 0 || lootedEyes[4] == 0 || lootedEyes[3] + lootedEyes[4] != 400)
            helper.fail("with Looting III, Bog Eyes per death 0 to 5+: " + java.util.Arrays.toString(lootedEyes) + "; expected 3 or 4, both seen");
        else helper.succeed();
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    /** Water three deep from x = 1 to 10 and z = 4 to 10, inside a stone bank (water set in a test flows). */
    private static void pond(GameTestHelper helper) {
        for (int x = 0; x < 12; x++)
            for (int z = 3; z < 12; z++)
                for (int y = 1; y <= POND_TOP; y++) {
                    boolean bank = x == 0 || x == 11 || z == 3 || z == 11;
                    helper.setBlock(new BlockPos(x, y, z), bank ? Blocks.STONE : Blocks.WATER);
                }
    }

    /** Where its feet go to lurk in the pond, relative to the arena. */
    private static double lurkY() {
        return LurkerRules.lurkY(SURFACE, ModEntities.BOG_LURKER.get().getHeight());
    }

    /** A lurker held still, facing -z (toward the arena's near side). */
    private static BogLurker still(GameTestHelper helper, Vec3 at) {
        BogLurker lurker = helper.spawn(ModEntities.BOG_LURKER.get(), at);
        lurker.setNoAi(true);
        lurker.setYRot(180.0F);
        lurker.yBodyRot = 180.0F;
        lurker.yBodyRotO = 180.0F;
        lurker.yHeadRot = 180.0F;
        return lurker;
    }

    /** An arrow loosed by the player from a bow (not Perfect), at a relative point, moving without gravity. */
    private static AbstractArrow shot(GameTestHelper helper, Player player, Vec3 at, Vec3 motion) {
        Arrow arrow = EntityTypes.ARROW.create(helper.getLevel(), EntitySpawnReason.COMMAND);
        if (arrow == null) throw helper.assertionException("couldn't make an arrow");
        Vec3 from = helper.absoluteVec(at);
        arrow.setPos(from);
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(motion);
        arrow.setBaseDamage(2.0);
        arrow.setOwner(player);
        ((ShotHolder) arrow).fulldraw$setShot(new ShotRecord(false, from, 1.0, 1.0, 0.0, BuiltInRegistries.ITEM.getKey(Items.BOW)));
        helper.getLevel().addFreshEntity(arrow);
        return arrow;
    }

    /** An arrow with no player's shot behind it, as a skeleton's, at a relative point, moving without gravity. */
    private static void plainArrow(GameTestHelper helper, Vec3 at, Vec3 motion) {
        Arrow arrow = EntityTypes.ARROW.create(helper.getLevel(), EntitySpawnReason.COMMAND);
        if (arrow == null) throw helper.assertionException("couldn't make an arrow");
        arrow.setPos(helper.absoluteVec(at));
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(motion);
        arrow.setBaseDamage(2.0);
        helper.getLevel().addFreshEntity(arrow);
    }

    private static boolean rule(ServerLevel level, EntitySpawnReason reason, BlockPos pos) {
        return SpawnPlacements.checkSpawnRules(ModEntities.BOG_LURKER.get(), level, reason, pos, level.getRandom());
    }

    /** The loader's config with Bog Lurker spawns switched off. */
    private static FullDrawConfig.Source lurkersOff(FullDrawConfig.Source before) {
        return new FullDrawConfig.Source() {
            @Override public boolean vanillaCritOnFullDraw() { return before.vanillaCritOnFullDraw(); }
            @Override public com.panda.fulldraw.logic.ShotMath.Tuning tuning() { return before.tuning(); }
            @Override public boolean blastArrowBreaksBlocks() { return before.blastArrowBreaksBlocks(); }
            @Override public int ropeArrowLength() { return before.ropeArrowLength(); }
            @Override public double arrowRecoveryChance() { return before.arrowRecoveryChance(); }
            @Override public int arrowPickupLifetimeSeconds() { return before.arrowPickupLifetimeSeconds(); }
            @Override public boolean shieldbearerSpawns() { return before.shieldbearerSpawns(); }
            @Override public boolean galeHarrierSpawns() { return before.galeHarrierSpawns(); }
            @Override public boolean bogLurkerSpawns() { return false; }
        };
    }

    private static float lost(LivingEntity entity) {
        return entity.getMaxHealth() - entity.getHealth();
    }
}
