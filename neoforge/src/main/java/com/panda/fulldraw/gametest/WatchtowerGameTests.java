package com.panda.fulldraw.gametest;

import com.panda.fulldraw.FullDrawMod;
import com.panda.fulldraw.worldgen.DryLandStructure;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The Ranger's Watchtower: its template, chests and placement data, and where it starts on the real Overworld
 * generator (the GameTest server's own levels are flat).
 */
final class WatchtowerGameTests {
    private WatchtowerGameTests() {}

    private static final String NAME = "rangers_watchtower";

    /**
     * The template loads at 27×21×27 (the tower in a yard 6 wide) with four chests, each naming a chest table that
     * exists, the better one on the battlements; no waterlogged block, and nothing in the yard's ground a sapling could
     * root in. The structure is a dry-land jigsaw in a random spread of spacing 48 and separation 16, in forests and
     * taiga, not plains or the dark forest. Then a survey on the NORMAL preset's Overworld generator: over three seeds,
     * the 49 placement cells within about 2.5 km of the origin; every seed gets a start, 5 to 25% of all cells do, and
     * none is levelled below the sea. And with the seed {@code "shots".hashCode()}, a start on a shore that would be
     * levelled a block below the sea is refused, and one in an old-growth pine taiga is kept.
     */
    static void watchtowerPlaces(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        var access = level.registryAccess();
        List<String> bad = new ArrayList<>();

        var template = server.getStructureTemplateManager().get(FullDrawMod.id(NAME));
        if (template.isEmpty()) {
            helper.fail("no " + NAME + " template");
            return;
        }
        var size = template.get().getSize();
        if (size.getX() != 27 || size.getY() != 21 || size.getZ() != 27) bad.add("the template is " + size + ", not 27×21×27");
        var settings = new StructurePlaceSettings();
        var chests = template.get().filterBlocks(BlockPos.ZERO, settings, Blocks.CHEST);
        if (chests.size() != 4) bad.add(chests.size() + " chests, not one on each of 3 floors and one on the top");
        for (var chest : chests) {
            String table = chest.nbt() == null ? "" : chest.nbt().getStringOr("LootTable", "");
            if (table.endsWith("_top") != chest.pos().getY() > 17) bad.add("the chest at " + chest.pos() + " has the wrong table: " + table);
            var key = ResourceKey.create(Registries.LOOT_TABLE, net.minecraft.resources.Identifier.parse(table.isEmpty() ? "minecraft:empty" : table));
            if (table.isEmpty() || server.reloadableRegistries().getLootTable(key) == LootTable.EMPTY)
                bad.add("the chest at " + chest.pos() + " names no table that exists: '" + table + "'");
        }
        int wet = 0;
        // the template's blocks that can hold water: its stairs, chests and lanterns
        for (var kind : List.of(Blocks.SPRUCE_STAIRS, Blocks.CHEST, Blocks.LANTERN))
            for (var block : template.get().filterBlocks(BlockPos.ZERO, settings, kind))
                if (block.state().getValue(BlockStateProperties.WATERLOGGED)) wet++;
        if (wet > 0) bad.add(wet + " waterlogged blocks");
        // the yard's ground, outside the tower's foundation: paths, gravel and stone throughout, nothing a sapling roots
        // in, so the forest's trees stand back from the walls
        java.util.Set<BlockPos> trodden = new java.util.HashSet<>();
        for (var kind : List.of(Blocks.DIRT_PATH, Blocks.GRAVEL, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE))
            for (var block : template.get().filterBlocks(BlockPos.ZERO, settings, kind))
                if (block.pos().getY() == 2) trodden.add(block.pos());
        int bare = 0;
        for (int x = 0; x < 27; x++)
            for (int z = 0; z < 27; z++)
                if ((x < 7 || x > 19 || z < 7 || z > 19) && !trodden.contains(new BlockPos(x, 2, z))) bare++;
        if (bare > 0) bad.add(bare + " cells of the yard's ground aren't path, gravel or stone");

        var structureKey = ResourceKey.create(Registries.STRUCTURE, FullDrawMod.id(NAME));
        var structure = access.lookupOrThrow(Registries.STRUCTURE).getOrThrow(structureKey);
        if (!(structure.value() instanceof DryLandStructure)) bad.add("the structure isn't a dry-land jigsaw: " + structure.value());
        var biomes = structure.value().biomes();
        var biomeLookup = access.lookupOrThrow(Registries.BIOME);
        for (var biome : List.of(Biomes.FOREST, Biomes.TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA))
            if (!biomes.contains(biomeLookup.getOrThrow(biome))) bad.add("it should stand in " + biome.identifier());
        for (var biome : List.of(Biomes.PLAINS, Biomes.DARK_FOREST))
            if (biomes.contains(biomeLookup.getOrThrow(biome))) bad.add("it shouldn't stand in " + biome.identifier());
        var set = access.lookupOrThrow(Registries.STRUCTURE_SET).getOrThrow(ResourceKey.create(Registries.STRUCTURE_SET, FullDrawMod.id(NAME)));
        if (!(set.value().placement() instanceof RandomSpreadStructurePlacement placement)) {
            bad.add("the placement isn't a random spread: " + set.value().placement());
            helper.fail("watchtower: " + bad);
            return;
        }
        if (placement.spacing() != 48 || placement.separation() != 16)
            bad.add("spacing " + placement.spacing() + " and separation " + placement.separation() + ", not 48 and 16");

        ChunkGenerator generator = access.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).value()
                .createWorldDimensions().get(LevelStem.OVERWORLD).orElseThrow().generator();
        var noise = (NoiseBasedChunkGenerator) generator;
        List<String> report = new ArrayList<>();
        int cells = 0, total = 0;
        for (long seed = 1; seed <= 3; seed++) {
            RandomState random = RandomState.create(access.lookupOrThrow(Registries.NOISE), seed, noise.generatorSettings().value());
            int starts = 0;
            for (int gx = -3; gx <= 3; gx++) {
                for (int gz = -3; gz <= 3; gz++) {
                    var chunk = placement.getPotentialStructureChunk(seed, gx * placement.spacing(), gz * placement.spacing());
                    cells++;
                    var start = structure.value().generate(structure, Level.OVERWORLD, access, generator, generator.getBiomeSource(),
                            random.createClimateSampler(net.minecraft.world.level.levelgen.densityfunction.SamplerContext.builder().enableCaches().build()),
                            random, server.getStructureTemplateManager(), seed, chunk, 0, level, biomes::contains);
                    if (!start.isValid()) continue;
                    starts++;
                    int levelled = start.getPieces().getFirst().getBoundingBox().minY() + 1;
                    if (levelled < generator.getSeaLevel()) bad.add("a start in " + chunk + " (seed " + seed + ") is levelled to " + levelled + ", below the sea");
                }
            }
            report.add("seed " + seed + ": " + starts);
            total += starts;
            if (starts == 0) bad.add("seed " + seed + " has no watchtower in 49 cells");
        }
        FullDrawMod.LOGGER.info("watchtower survey: {} starts in {} cells of 768 blocks ({})", total, cells, report);
        if (total < cells * 0.05 || total > cells * 0.25) bad.add(total + " starts in " + cells + " cells, not 5 to 25%");

        // a shore start that would be levelled a block below the sea, where the generator floods the cut, is refused;
        // one in an old-growth pine taiga is kept
        long harnessSeed = "shots".hashCode();
        RandomState harness = RandomState.create(access.lookupOrThrow(Registries.NOISE), harnessSeed, noise.generatorSettings().value());
        for (int[] spot : new int[][] {{-2295, -713, 0}, {-2115, 957, 1}}) {
            int cx = spot[0] >> 4, cz = spot[1] >> 4;
            var chunk = placement.getPotentialStructureChunk(harnessSeed, Math.floorDiv(cx, placement.spacing()) * placement.spacing(),
                    Math.floorDiv(cz, placement.spacing()) * placement.spacing());
            var start = structure.value().generate(structure, Level.OVERWORLD, access, generator, generator.getBiomeSource(),
                    harness.createClimateSampler(net.minecraft.world.level.levelgen.densityfunction.SamplerContext.builder().enableCaches().build()),
                    harness, server.getStructureTemplateManager(), harnessSeed, chunk, 0, level, biomes::contains);
            boolean kept = spot[2] == 1;
            if (start.isValid() != kept)
                bad.add("the start in " + chunk + " near " + spot[0] + ", " + spot[1] + " should be " + (kept ? "kept" : "refused"));
        }
        if (!bad.isEmpty()) helper.fail("watchtower: " + bad);
        else helper.succeed();
    }
}
