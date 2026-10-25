package com.panda.fulldraw.mixin;

import com.panda.fulldraw.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shieldbearers among a pillager outpost's spawns, added to whatever list the outpost has (vanilla's, or a pack's), so
 * its structure file stays its own. Both the pick of a mob and the check that it may still spawn there read this list.
 */
@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerMixin {
    @Inject(method = "mobsAt", at = @At("RETURN"), cancellable = true)
    private static void fulldraw$outpostShieldbearers(ServerLevel level, StructureManager structures, ChunkGenerator generator, MobCategory category,
                                                      BlockPos pos, CallbackInfoReturnable<WeightedList<MobSpawnSettings.SpawnerData>> cir) {
        cir.setReturnValue(ModEntities.outpostSpawns(structures, category, cir.getReturnValue()));
    }
}
