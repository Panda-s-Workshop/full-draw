package com.panda.fulldraw.mixin;

import com.panda.fulldraw.FullDrawConfig;
import com.panda.fulldraw.entity.Shieldbearer;
import com.panda.fulldraw.logic.TowerShield;
import com.panda.fulldraw.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.raid.Raid;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shieldbearers join a raid's waves from the third on, beside vanilla's raiders, at the wave's spawn point. Added
 * after vanilla's own so the wave's leader stays one of them.
 */
@Mixin(Raid.class)
public abstract class RaidMixin {
    @Shadow private int groupsSpawned;
    @Shadow @Final private RandomSource random;

    @Shadow
    public abstract void joinRaid(ServerLevel level, int groupNumber, net.minecraft.world.entity.raid.Raider raider, BlockPos pos, boolean exists);

    @Shadow
    public abstract void updateBossbar();

    @Inject(method = "spawnGroup", at = @At("TAIL"))
    private void fulldraw$shieldbearers(ServerLevel level, BlockPos pos, CallbackInfo ci) {
        if (!FullDrawConfig.shieldbearerSpawns()) return;
        // vanilla has counted the wave by now
        int wave = groupsSpawned;
        int count = TowerShield.perWave(wave, level.getDifficulty().getId(), random.nextDouble());
        for (int i = 0; i < count; i++) {
            Shieldbearer bearer = ModEntities.SHIELDBEARER.get().create(level, EntitySpawnReason.EVENT);
            if (bearer == null) break;
            joinRaid(level, wave, bearer, pos, false);
        }
        if (count > 0) updateBossbar();
    }
}
