package com.panda.fulldraw.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.panda.fulldraw.registry.ModWorldgen;
import java.util.Optional;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;

/**
 * A jigsaw structure that only generates on dry land. The biome check alone looks at one column, and the start is
 * projected onto the water surface, so a land biome at the edge of an ocean, river or lake could put a tower in the
 * water. Where a valid biome could stand at the chunk's middle, this samples a grid over the footprint (the template
 * can be rotated around its start corner, so the grid covers {@code footprint} blocks on every side, every 2 blocks)
 * and gives up wherever water lies on the ground. It also gives up where the ground would be levelled below sea
 * level: the generator floods such a cut, though no heightmap shows water there beforehand.
 */
public final class DryLandStructure extends Structure {
    public static final MapCodec<DryLandStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            JigsawStructure.CODEC.codec().fieldOf("jigsaw").forGetter(s -> s.jigsaw),
            com.mojang.serialization.Codec.intRange(0, 64).optionalFieldOf("footprint", 16).forGetter(s -> s.footprint)
    ).apply(i, DryLandStructure::new));

    /** Grid spacing of the dry check, in blocks. */
    private static final int STEP = 2;

    private final JigsawStructure jigsaw;
    private final int footprint;

    private DryLandStructure(JigsawStructure jigsaw, int footprint) {
        super(new StructureSettings(jigsaw.biomes(), jigsaw.spawnOverrides(), jigsaw.step(), jigsaw.terrainAdaptation()));
        this.jigsaw = jigsaw;
        this.footprint = footprint;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        // the cheap check first: most chunks are in some other biome, and the grid below costs a few hundred columns
        if (!context.couldValidBiomeExistOnTopOfChunkCenter()) return Optional.empty();
        int x0 = context.chunkPos().getMinBlockX();
        int z0 = context.chunkPos().getMinBlockZ();
        for (int dx = -footprint; dx <= footprint; dx += STEP) {
            for (int dz = -footprint; dz <= footprint; dz += STEP) {
                if (wet(context, x0 + dx, z0 + dz)) return Optional.empty();
            }
        }
        // the start's height is where the terrain round it is levelled to (its middle's surface, less the sink)
        var stub = jigsaw.findGenerationPoint(context);
        if (stub.isPresent() && stub.get().position().getY() < context.chunkGenerator().getSeaLevel()) return Optional.empty();
        return stub;
    }

    /** Water above the ground: the first free block above everything differs from the first above solid ground. */
    static boolean wet(GenerationContext context, int x, int z) {
        var generator = context.chunkGenerator();
        int surface = generator.getFirstFreeHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
        int ground = generator.getFirstFreeHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
        return surface != ground;
    }

    @Override
    public StructureType<?> type() {
        return ModWorldgen.DRY_LAND.get();
    }
}
