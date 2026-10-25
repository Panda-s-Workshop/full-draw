package com.panda.fulldraw.gametest;

import com.panda.fulldraw.FullDrawConfig;
import com.panda.fulldraw.FullDrawMod;
import com.panda.fulldraw.entity.GaleHarrier;
import com.panda.fulldraw.logic.HarrierFlight;
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
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
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
 * The Gale Harrier: what grounds it (a Perfect release or a headshot, and only in its dive, level or steep), its dive's
 * strike and the climb after it, its fall, double melee damage while it is down and getting up 3 s after it lands, its
 * state through a save, where and when it spawns, what it drops (with and without Looting) and the recipes its parts go
 * into.
 */
final class HarrierGameTests {
    private HarrierGameTests() {}

    /** The height of the level dives, above the arena floor: the arrows fly at the middle of the harrier's body. */
    private static final double DIVE_Y = 4.0;

    /**
     * Two harriers diving east along level lines. An arrow head on into the first one's face is a headshot (not
     * Perfect) and grounds it: the two close by 4.1 blocks a tick, and the harrier's own move carries it over the
     * arrow's point between ticks, which still counts as the hit. A plain arrow into the second one's tail from behind
     * hits it and leaves it in the air.
     */
    static void harrierGroundedByDiveHeadshot(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        GaleHarrier headshot = diving(helper, 3.5);
        GaleHarrier plain = diving(helper, 8.5);
        AbstractArrow headOn = shot(helper, player, false, new Vec3(11.0, DIVE_Y + 0.3, 3.5), new Vec3(-3.0, 0.0, 0.0));
        shot(helper, player, false, new Vec3(0.2, DIVE_Y + 0.3, 8.5), new Vec3(3.0, 0.0, 0.0));
        helper.runAfterDelay(8, () -> {
            if (lost(headshot) <= 0) helper.fail("the arrow should hit the diving harrier head on: " + where(helper, headOn, headshot));
            else if (headshot.phase() != GaleHarrier.Phase.GROUNDED) helper.fail("a headshot in the dive should ground it, it is " + headshot.phase().id());
            else if (lost(plain) <= 0) helper.fail("the arrow from behind should hit the diving harrier");
            else if (plain.phase() == GaleHarrier.Phase.GROUNDED) helper.fail("a plain hit in the dive shouldn't ground it");
            else done(helper, headshot, plain);
        });
    }

    /**
     * A harrier diving steeply (70 degrees down) meets an arrow loosed at it head on from 20 blocks below along its line,
     * falling as arrows do (aimed a little high for the drop). The arrow comes up into its leading face, the underside
     * of its box: along the dive that is its head, a headshot, and it grounds it. Needs the open sky: it dives from 22 up.
     */
    static void harrierSteepDiveHeadshot(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        double pitch = Math.toRadians(70.0);
        Vec3 line = new Vec3(Math.cos(pitch), -Math.sin(pitch), 0.0);
        GaleHarrier harrier = helper.spawn(ModEntities.GALE_HARRIER.get(), new Vec3(2.5, 22.0, 6.5));
        harrier.startDive(harrier.position().add(line.scale(40.0)));
        Vec3 middle = helper.relativeVec(harrier.getBoundingBox().getCenter());
        AbstractArrow arrow = shot(helper, player, false, middle.add(line.scale(20.0)), line.scale(-3.0).add(0.0, 0.13, 0.0));
        arrow.setNoGravity(false);
        helper.runAfterDelay(10, () -> {
            if (lost(harrier) <= 0) helper.fail("the arrow should hit the steeply diving harrier: " + where(helper, arrow, harrier));
            else if (harrier.phase() != GaleHarrier.Phase.GROUNDED) helper.fail("head on into a steep dive is a headshot and should ground it, it is " + harrier.phase().id());
            else done(helper, harrier);
        });
    }

    /**
     * A Perfect arrow into a diving harrier's tail (a body shot) grounds it. A Perfect arrow into the face of one that
     * isn't diving (held still) hits it and leaves it flying.
     */
    static void harrierPerfectGroundsOnlyInDive(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        GaleHarrier dive = diving(helper, 3.5);
        GaleHarrier circling = still(helper, new Vec3(6.5, DIVE_Y, 8.5));
        circling.setYRot(-90.0F);
        circling.yBodyRot = -90.0F;
        circling.yBodyRotO = -90.0F;
        shot(helper, player, true, new Vec3(0.2, DIVE_Y + 0.3, 3.5), new Vec3(3.0, 0.0, 0.0));
        AbstractArrow headOn = shot(helper, player, true, new Vec3(11.0, DIVE_Y + 0.3, 8.5), new Vec3(-3.0, 0.0, 0.0));
        helper.runAfterDelay(8, () -> {
            if (lost(dive) <= 0) helper.fail("the Perfect arrow should hit the diving harrier");
            else if (dive.phase() != GaleHarrier.Phase.GROUNDED) helper.fail("a Perfect hit in the dive should ground it, it is " + dive.phase().id());
            else if (lost(circling) <= 0) helper.fail("the Perfect arrow should hit the circling harrier: " + where(helper, headOn, circling));
            else if (circling.phase() != GaleHarrier.Phase.CIRCLE) helper.fail("a Perfect headshot outside a dive shouldn't ground it, it is " + circling.phase().id());
            else done(helper, dive);
        });
    }

    /** The dive strikes its target for 6 (its attack, through no armour) and turns it to a climb, straight back up. */
    static void harrierDiveStrikesAndClimbs(GameTestHelper helper) {
        Mob pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, new Vec3(6.5, 1, 6.5));
        GaleHarrier harrier = helper.spawn(ModEntities.GALE_HARRIER.get(), new Vec3(3.5, 7, 6.5));
        harrier.setTarget(pig);
        harrier.startDive(pig.position().add(0.0, pig.getBbHeight() * 0.5, 0.0));
        double[] lowest = {Double.MAX_VALUE};
        helper.onEachTick(() -> lowest[0] = Math.min(lowest[0], harrier.getY()));
        helper.runAfterDelay(30, () -> {
            if (Math.abs(lost(pig) - 6.0F) > 1.0E-3) helper.fail("the dive should strike for 6, the pig lost " + lost(pig));
            else if (harrier.phase() != GaleHarrier.Phase.CLIMB) helper.fail("after the strike it should climb, it is " + harrier.phase().id());
            else if (harrier.getY() < lowest[0] + 6.0) helper.fail("it should climb away: lowest " + lowest[0] + ", now " + harrier.getY());
            else done(helper, harrier);
        });
    }

    /**
     * Grounded, a melee hit does double (4 → 8) and an arrow's damage stays as it is; a harrier in the air takes melee as
     * it comes. After {@link HarrierFlight#GROUNDED_TICKS} ticks the grounded one takes off.
     */
    static void harrierGroundedTakesDoubleMelee(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        GaleHarrier struck = helper.spawn(ModEntities.GALE_HARRIER.get(), new Vec3(3.5, 1, 3.5));
        GaleHarrier shot = still(helper, new Vec3(6.5, 1, 6.5));
        GaleHarrier flying = still(helper, new Vec3(9.5, 3, 9.5));
        struck.ground();
        shot.ground();
        struck.hurtServer(level, level.damageSources().playerAttack(player), 4.0F);
        Arrow arrow = EntityTypes.ARROW.create(level, EntitySpawnReason.COMMAND);
        if (arrow == null) throw helper.assertionException("couldn't make an arrow");
        shot.hurtServer(level, level.damageSources().arrow(arrow, player), 4.0F);
        flying.hurtServer(level, level.damageSources().playerAttack(player), 4.0F);
        if (Math.abs(lost(struck) - 8.0F) > 1.0E-3) throw helper.assertionException("grounded, a melee hit of 4 should do 8, it did " + lost(struck));
        if (Math.abs(lost(shot) - 4.0F) > 1.0E-3) throw helper.assertionException("grounded, an arrow's 4 should stay 4, it did " + lost(shot));
        if (Math.abs(lost(flying) - 4.0F) > 1.0E-3) throw helper.assertionException("in the air, a melee hit of 4 should do 4, it did " + lost(flying));
        helper.runAfterDelay(HarrierFlight.GROUNDED_TICKS - 5, () -> {
            if (struck.phase() != GaleHarrier.Phase.GROUNDED) helper.fail("it should stay down for " + HarrierFlight.GROUNDED_TICKS + " ticks, it is " + struck.phase().id());
        });
        helper.runAfterDelay(HarrierFlight.GROUNDED_TICKS + 3, () -> {
            if (struck.phase() != GaleHarrier.Phase.CLIMB) helper.fail("after " + HarrierFlight.GROUNDED_TICKS + " ticks it should take off, it is " + struck.phase().id());
            else done(helper, struck);
        });
    }

    /**
     * Grounded 6 blocks up, it falls, and its time down counts from when it lands: still down {@link
     * HarrierFlight#GROUNDED_TICKS} ticks after it was hit, it takes off that long after landing.
     */
    static void harrierGroundedCountsFromLanding(GameTestHelper helper) {
        GaleHarrier harrier = helper.spawn(ModEntities.GALE_HARRIER.get(), new Vec3(6.5, 7, 6.5));
        harrier.ground();
        int[] at = {-1, -1};   // the tick it landed, the tick it took off
        int[] tick = {0};
        helper.onEachTick(() -> {
            tick[0]++;
            if (at[0] < 0 && harrier.landed()) at[0] = tick[0];
            if (at[1] < 0 && harrier.phase() == GaleHarrier.Phase.CLIMB) at[1] = tick[0];
        });
        helper.runAfterDelay(HarrierFlight.GROUNDED_TICKS + 3, () -> {
            if (at[0] < 0) helper.fail("it should have hit the ground from 6 up");
            else if (harrier.phase() != GaleHarrier.Phase.GROUNDED)
                helper.fail("it landed at tick " + at[0] + ", so " + (HarrierFlight.GROUNDED_TICKS + 3) + " ticks after it was hit it should still be down, it is " + harrier.phase().id());
        });
        helper.runAfterDelay(95, () -> {
            int down = at[1] - at[0];
            if (at[1] < 0) helper.fail("it should take off " + HarrierFlight.GROUNDED_TICKS + " ticks after landing (landed at tick " + at[0] + "), it is " + harrier.phase().id());
            else if (Math.abs(down - HarrierFlight.GROUNDED_TICKS) > 2) helper.fail("it should stay down " + HarrierFlight.GROUNDED_TICKS + " ticks after landing, it stayed " + down);
            else done(helper, harrier);
        });
    }

    /**
     * Through a save and load a grounded harrier stays grounded with the time it has had down, one circling keeps its
     * countdown to the next dive, and one diving comes back climbing: a dive doesn't carry over a reload.
     */
    static void harrierSaves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GaleHarrier grounded = still(helper, new Vec3(3.5, 1, 3.5));
        GaleHarrier dive = still(helper, new Vec3(6.5, 5, 6.5));
        GaleHarrier circling = still(helper, new Vec3(9.5, 5, 9.5));
        grounded.ground();
        dive.startDive(dive.position().add(4.0, -4.0, 0.0));
        List<String> wrong = new ArrayList<>();
        for (GaleHarrier h : List.of(grounded, dive, circling)) {
            var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
            h.saveWithoutId(out);
            GaleHarrier copy = ModEntities.GALE_HARRIER.get().create(level, EntitySpawnReason.LOAD);
            if (copy == null) throw helper.assertionException("couldn't make a Gale Harrier");
            copy.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), out.buildResult()));
            GaleHarrier.Phase expected = h == dive ? GaleHarrier.Phase.CLIMB : h.phase();
            if (copy.phase() != expected || copy.nextDive() != h.nextDive() || copy.isNoGravity() != (expected != GaleHarrier.Phase.GROUNDED)
                    || (expected == h.phase() && copy.phaseTicks() != h.phaseTicks()))
                wrong.add(h.phase().id() + " came back " + copy.phase().id() + " (next dive " + h.nextDive() + "/" + copy.nextDive()
                        + ", ticks " + h.phaseTicks() + "/" + copy.phaseTicks() + ", no gravity " + copy.isNoGravity() + ")");
        }
        if (!wrong.isEmpty()) helper.fail("a harrier should load as it was saved: " + wrong);
        else helper.succeed();
    }

    /**
     * Its home is the windswept hills and the high peaks (the biome tag NeoForge's modifier and Fabric's code both read),
     * at weight {@link ModEntities#HARRIER_WEIGHT} in groups of 1 or 2. On its own it spawns only by day, under open
     * sky, with fewer than {@link ModEntities#HARRIER_CAP} about, and with the server's switch on; a spawn egg works
     * at night. Runs alone: it counts every harrier within {@link ModEntities#HARRIER_CAP_RADIUS} blocks, and it sets
     * the sky's darkness for a moment (putting it back in the same tick).
     */
    static void harrierSpawnRules(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var access = level.registryAccess();
        var tag = access.lookupOrThrow(Registries.BIOME).get(ModEntities.HARRIER_BIOMES);
        List<ResourceKey<Biome>> homes = List.of(Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WINDSWEPT_FOREST,
                Biomes.JAGGED_PEAKS, Biomes.FROZEN_PEAKS, Biomes.STONY_PEAKS);
        if (tag.isEmpty() || tag.get().size() != homes.size() || !homes.stream().allMatch(k -> tag.get().stream().anyMatch(b -> b.is(k))))
            throw helper.assertionException("its biome tag should hold the windswept hills and the high peaks: " + tag.map(t -> t.stream().map(b -> b.getRegisteredName()).toList()));
        var modifier = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, FullDrawMod.id("spawns/gale_harrier")));
        if (modifier.isEmpty() || !(modifier.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier spawns))
            throw helper.assertionException("it should have a NeoForge spawn modifier");
        Weighted<MobSpawnSettings.SpawnerData> entry = spawns.spawners().unwrap().stream()
                .filter(w -> w.value().type() == ModEntities.GALE_HARRIER.get()).findFirst().orElse(null);
        if (entry == null || entry.weight() != ModEntities.HARRIER_WEIGHT || entry.value().count().minInclusive() != 1 || entry.value().count().maxInclusive() != 2)
            throw helper.assertionException("the modifier should spawn it at weight " + ModEntities.HARRIER_WEIGHT + " in groups of 1 to 2: " + entry);
        // and the biomes' own spawn lists carry it once the modifiers have run (26.3 keeps them as an environment attribute)
        List<String> without = new ArrayList<>();
        for (ResourceKey<Biome> home : homes) {
            MobSpawnSettings settings = access.lookupOrThrow(Registries.BIOME).getOrThrow(home).value().getModifiedEnvironmentAttributes()
                    .applyModifier(net.minecraft.world.attribute.EnvironmentAttributes.NATURAL_MOB_SPAWNS, MobSpawnSettings.EMPTY);
            if (settings.getMobsInCategory(net.minecraft.world.entity.MobCategory.MONSTER).unwrap().stream()
                    .noneMatch(w -> w.value().type() == ModEntities.GALE_HARRIER.get() && w.weight() == ModEntities.HARRIER_WEIGHT))
                without.add(home.identifier().getPath());
        }
        if (!without.isEmpty()) throw helper.assertionException("these biomes' monster spawns should list it: " + without);
        // a roof over one spot; sky light takes a few ticks to follow a block
        helper.setBlock(new BlockPos(9, 5, 9), Blocks.STONE);
        helper.runAfterDelay(10, () -> {
            BlockPos open = helper.absolutePos(new BlockPos(3, 1, 3)), roofed = helper.absolutePos(new BlockPos(9, 1, 9));
            // a harrier from an earlier test that flew off before it was cleared would count toward the cap
            level.getEntitiesOfClass(GaleHarrier.class, new net.minecraft.world.phys.AABB(open).inflate(ModEntities.HARRIER_CAP_RADIUS)).forEach(GaleHarrier::discard);
            List<String> wrong = new ArrayList<>();
            try {
                skyDarken(level, 0);
                if (!rule(level, EntitySpawnReason.NATURAL, open))
                    wrong.add("by day under open sky it should spawn (bright " + level.isBrightOutside() + ", sky " + level.canSeeSky(open)
                            + ", difficulty " + level.getDifficulty() + ", switch " + FullDrawConfig.galeHarrierSpawns() + ")");
                if (rule(level, EntitySpawnReason.NATURAL, roofed)) wrong.add("under a roof it shouldn't");
                FullDrawConfig.Source before = FullDrawConfig.source();
                FullDrawConfig.use(harriersOff(before));
                try {
                    if (rule(level, EntitySpawnReason.NATURAL, open)) wrong.add("with galeHarrierSpawns off it shouldn't");
                } finally {
                    FullDrawConfig.use(before);
                }
                skyDarken(level, 11);
                if (rule(level, EntitySpawnReason.NATURAL, open)) wrong.add("at night it shouldn't");
                if (!rule(level, EntitySpawnReason.SPAWN_ITEM_USE, open)) wrong.add("a spawn egg should work at night");
                skyDarken(level, 0);
                List<GaleHarrier> about = new ArrayList<>();
                for (int i = 0; i < ModEntities.HARRIER_CAP; i++)
                    about.add(still(helper, new Vec3(6.5 + i, 3, 6.5)));
                if (rule(level, EntitySpawnReason.NATURAL, open)) wrong.add("with " + ModEntities.HARRIER_CAP + " about it shouldn't");
                about.forEach(GaleHarrier::discard);
            } finally {
                level.updateSkyBrightness();
            }
            if (!wrong.isEmpty()) helper.fail("spawn rule: " + wrong);
            else helper.succeed();
        });
    }

    /** Over 400 deaths: 1 to 3 Harrier Feathers every time (each count seen), and 0 or 1 Harrier Talon (both seen). */
    static void harrierDrops(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GaleHarrier harrier = still(helper, new Vec3(6.5, 1, 6.5));
        LootTable table = level.getServer().reloadableRegistries().getLootTable(harrier.getType().getDefaultLootTable().orElseThrow());
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, harrier)
                .withParameter(LootContextParams.ORIGIN, harrier.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic())
                .create(LootContextParamSets.ENTITY);
        RandomSource random = RandomSource.create(20261025L);
        int[] feathers = new int[5], talons = new int[3];
        List<String> other = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            int f = 0, t = 0;
            for (ItemStack stack : table.getRandomItems(params, random)) {
                if (stack.isEmpty()) continue;   // a roll of 0 talons comes back as an empty stack
                if (stack.is(ModItems.HARRIER_FEATHER.get())) f += stack.getCount();
                else if (stack.is(ModItems.HARRIER_TALON.get())) t += stack.getCount();
                else other.add(stack.toString());
            }
            feathers[Math.min(f, 4)]++;
            talons[Math.min(t, 2)]++;
        }
        harrier.discard();
        if (!other.isEmpty()) helper.fail("it should drop only feathers and talons, also dropped " + other);
        else if (feathers[0] > 0 || feathers[4] > 0 || feathers[1] == 0 || feathers[2] == 0 || feathers[3] == 0)
            helper.fail("feathers per death, 0 to 4+: " + java.util.Arrays.toString(feathers) + "; expected 1 to 3, each seen");
        else if (talons[2] > 0 || talons[0] == 0 || talons[1] == 0)
            helper.fail("talons per death, 0 to 2+: " + java.util.Arrays.toString(talons) + "; expected 0 or 1, both seen");
        else helper.succeed();
    }

    /**
     * Killed by a player holding a Looting III sword, over 200 deaths: a talon half the time and three more for sure (3
     * or 4, both seen), and 1 to 3 feathers with up to three more (more than 3 seen).
     */
    static void harrierDropsWithLooting(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING), 3);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, sword);
        GaleHarrier harrier = still(helper, new Vec3(6.5, 1, 6.5));
        LootTable table = level.getServer().reloadableRegistries().getLootTable(harrier.getType().getDefaultLootTable().orElseThrow());
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, harrier)
                .withParameter(LootContextParams.ORIGIN, harrier.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().playerAttack(player))
                .withParameter(LootContextParams.ATTACKING_ENTITY, player)
                .withParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, player)
                .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player)
                .create(LootContextParamSets.ENTITY);
        RandomSource random = RandomSource.create(20261025L);
        int[] feathers = new int[8], talons = new int[6];
        for (int i = 0; i < 200; i++) {
            int f = 0, t = 0;
            for (ItemStack stack : table.getRandomItems(params, random)) {
                if (stack.is(ModItems.HARRIER_FEATHER.get())) f += stack.getCount();
                else if (stack.is(ModItems.HARRIER_TALON.get())) t += stack.getCount();
            }
            feathers[Math.min(f, 7)]++;
            talons[Math.min(t, 5)]++;
        }
        harrier.discard();
        int moreFeathers = feathers[4] + feathers[5] + feathers[6];
        if (talons[3] == 0 || talons[4] == 0 || talons[3] + talons[4] != 200)
            helper.fail("with Looting III, talons per death 0 to 5+: " + java.util.Arrays.toString(talons) + "; expected 3 or 4, both seen");
        else if (feathers[0] > 0 || feathers[7] > 0 || moreFeathers == 0)
            helper.fail("with Looting III, feathers per death 0 to 7+: " + java.util.Arrays.toString(feathers) + "; expected 1 to 6, more than 3 seen");
        else helper.succeed();
    }

    /**
     * The Repeating Crossbow crafts from a crossbow, three iron, two redstone and a Harrier Talon, and not without the
     * talon; its unlock comes with holding a talon.
     */
    static void harrierPartsCraft(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack iron = new ItemStack(Items.IRON_INGOT), redstone = new ItemStack(Items.REDSTONE), crossbow = new ItemStack(Items.CROSSBOW);
        ItemStack talon = new ItemStack(ModItems.HARRIER_TALON.get()), none = ItemStack.EMPTY;
        ItemStack made = craft(level, List.of(iron, talon, iron, redstone, crossbow, redstone, none, iron, none));
        ItemStack without = craft(level, List.of(iron, none, iron, redstone, crossbow, redstone, none, iron, none));
        var unlock = level.getServer().getAdvancements().get(FullDrawMod.id("recipes/combat/repeating_crossbow"));
        if (!made.is(ModItems.REPEATING_CROSSBOW.get())) helper.fail("a crossbow, 3 iron, 2 redstone and a Harrier Talon should make a Repeating Crossbow, made " + made);
        else if (without.is(ModItems.REPEATING_CROSSBOW.get())) helper.fail("without the talon it shouldn't");
        else if (unlock == null) helper.fail("the Repeating Crossbow's recipe-book unlock is missing");
        else helper.succeed();
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    /** A harrier held still: its flight is its own AI step, which this leaves out. */
    private static GaleHarrier still(GameTestHelper helper, Vec3 at) {
        GaleHarrier harrier = helper.spawn(ModEntities.GALE_HARRIER.get(), at);
        harrier.setNoAi(true);
        return harrier;
    }

    /** A harrier on the arena's west side diving east along a level line; with no target it flies the whole line. */
    private static GaleHarrier diving(GameTestHelper helper, double z) {
        GaleHarrier harrier = helper.spawn(ModEntities.GALE_HARRIER.get(), new Vec3(1.5, DIVE_Y, z));
        harrier.startDive(harrier.position().add(20.0, 0.0, 0.0));
        return harrier;
    }

    /** An arrow loosed by the player from a bow, Perfect or not, at a relative point, moving without gravity. */
    private static AbstractArrow shot(GameTestHelper helper, Player player, boolean perfect, Vec3 at, Vec3 motion) {
        Arrow arrow = EntityTypes.ARROW.create(helper.getLevel(), EntitySpawnReason.COMMAND);
        if (arrow == null) throw helper.assertionException("couldn't make an arrow");
        Vec3 from = helper.absoluteVec(at);
        arrow.setPos(from);
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(motion);
        arrow.setBaseDamage(2.0);
        arrow.setOwner(player);
        ((ShotHolder) arrow).fulldraw$setShot(new ShotRecord(perfect, from, 1.0, 1.0, 0.0, BuiltInRegistries.ITEM.getKey(Items.BOW)));
        helper.getLevel().addFreshEntity(arrow);
        return arrow;
    }

    /** Harriers that took off would fly on out of the arena past the test: they go with the test. */
    private static void done(GameTestHelper helper, GaleHarrier... harriers) {
        for (GaleHarrier h : harriers) h.discard();
        helper.succeed();
    }

    private static String where(GameTestHelper helper, AbstractArrow arrow, GaleHarrier harrier) {
        return "arrow at " + helper.relativeVec(arrow.position()) + " moving " + arrow.getDeltaMovement() + (arrow.isRemoved() ? " (removed)" : "")
                + ", harrier at " + helper.relativeVec(harrier.position()) + " " + harrier.getBoundingBox();
    }

    private static boolean rule(ServerLevel level, EntitySpawnReason reason, BlockPos pos) {
        return SpawnPlacements.checkSpawnRules(ModEntities.GALE_HARRIER.get(), level, reason, pos, level.getRandom());
    }

    /** Sets the level's sky darkness (0 midday, 11 night) until the next {@link Level#updateSkyBrightness()}. */
    private static void skyDarken(Level level, int value) {
        try {
            var field = Level.class.getDeclaredField("skyDarken");
            field.setAccessible(true);
            field.setInt(level, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ItemStack craft(ServerLevel level, List<ItemStack> grid) {
        CraftingInput input = CraftingInput.of(3, 3, grid);
        return level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level).map(r -> r.value().assemble(input)).orElse(ItemStack.EMPTY);
    }

    /** The loader's config with Gale Harrier spawns switched off. */
    private static FullDrawConfig.Source harriersOff(FullDrawConfig.Source before) {
        return new FullDrawConfig.Source() {
            @Override public boolean vanillaCritOnFullDraw() { return before.vanillaCritOnFullDraw(); }
            @Override public com.panda.fulldraw.logic.ShotMath.Tuning tuning() { return before.tuning(); }
            @Override public boolean blastArrowBreaksBlocks() { return before.blastArrowBreaksBlocks(); }
            @Override public int ropeArrowLength() { return before.ropeArrowLength(); }
            @Override public double arrowRecoveryChance() { return before.arrowRecoveryChance(); }
            @Override public int arrowPickupLifetimeSeconds() { return before.arrowPickupLifetimeSeconds(); }
            @Override public boolean shieldbearerSpawns() { return before.shieldbearerSpawns(); }
            @Override public boolean galeHarrierSpawns() { return false; }
        };
    }

    private static float lost(LivingEntity entity) {
        return entity.getMaxHealth() - entity.getHealth();
    }
}
