package com.panda.fulldraw.registry;

import com.panda.fulldraw.FullDrawConfig;
import com.panda.fulldraw.FullDrawMod;
import com.panda.fulldraw.entity.BluntArrow;
import com.panda.fulldraw.entity.BlastArrow;
import com.panda.fulldraw.entity.BodkinArrow;
import com.panda.fulldraw.entity.FlareArrow;
import com.panda.fulldraw.entity.BroadheadArrow;
import com.panda.fulldraw.entity.FullDrawArrow;
import com.panda.fulldraw.entity.RopeArrow;
import com.panda.fulldraw.entity.Shieldbearer;
import com.panda.fulldraw.logic.TowerShield;
import com.panda.fulldraw.platform.registry.DeferredHolder;
import com.panda.fulldraw.platform.registry.DeferredRegister;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;
import net.minecraft.world.phys.AABB;

public final class ModEntities {
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(FullDrawMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<BroadheadArrow>> BROADHEAD_ARROW = arrow("broadhead_arrow", BroadheadArrow::new);
    public static final DeferredHolder<EntityType<?>, EntityType<BodkinArrow>> BODKIN_ARROW = arrow("bodkin_arrow", BodkinArrow::new);
    public static final DeferredHolder<EntityType<?>, EntityType<BluntArrow>> BLUNT_ARROW = arrow("blunt_arrow", BluntArrow::new);
    public static final DeferredHolder<EntityType<?>, EntityType<RopeArrow>> ROPE_ARROW = arrow("rope_arrow", RopeArrow::new);
    public static final DeferredHolder<EntityType<?>, EntityType<FlareArrow>> FLARE_ARROW = arrow("flare_arrow", FlareArrow::new);
    public static final DeferredHolder<EntityType<?>, EntityType<BlastArrow>> BLAST_ARROW = arrow("blast_arrow", BlastArrow::new);

    /** An illager, sized and tracked as vanilla's vindicator. */
    public static final DeferredHolder<EntityType<?>, EntityType<Shieldbearer>> SHIELDBEARER = ENTITIES.registerEntityType("shieldbearer",
            Shieldbearer::new, MobCategory.MONSTER, b -> b.canSpawnFarFromPlayer().sized(0.6F, 1.95F).passengerAttachments(2.0F)
                    .ridingOffset(-0.6F).clientTrackingRange(8).notInPeaceful());

    /** Outposts keep at most one Shieldbearer about them, among their pillagers. */
    public static final double OUTPOST_GUARD_RADIUS = 48.0;

    /** The last outpost spawn list extended, and what it became: spawning asks for it on every attempt. */
    private static volatile Extended lastOutpost;

    private record Extended(WeightedList<MobSpawnSettings.SpawnerData> from, WeightedList<MobSpawnSettings.SpawnerData> to) {}

    private ModEntities() {}

    /** Every mob's attributes, for the loader's attribute registration. */
    public static void attributes(BiConsumer<EntityType<? extends LivingEntity>, AttributeSupplier> sink) {
        sink.accept(SHIELDBEARER.get(), Shieldbearer.createAttributes().build());
    }

    /** Where a mob may spawn on its own, for the loader's spawn placement registration. */
    public interface SpawnRules {
        <T extends Mob> void set(EntityType<T> type, SpawnPlacementType placement, Heightmap.Types heightmap, SpawnPlacements.SpawnPredicate<T> predicate);
    }

    /** The Shieldbearer spawns on its own only at pillager outposts: on the ground, as a pillager, one at a time. */
    public static void spawnRules(SpawnRules rules) {
        rules.set(SHIELDBEARER.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModEntities::outpostGuard);
    }

    private static boolean outpostGuard(EntityType<Shieldbearer> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        if (!PatrollingMonster.checkPatrollingMonsterSpawnRules(type, level, reason, pos, random)) return false;
        if (reason != EntitySpawnReason.NATURAL) return true;
        return FullDrawConfig.shieldbearerSpawns() && level.getEntitiesOfClass(Shieldbearer.class, new AABB(pos).inflate(OUTPOST_GUARD_RADIUS)).isEmpty();
    }

    /**
     * The monsters that may spawn at a spot, with the Shieldbearer added when they are the pillager outpost's own list:
     * one spawn in {@link TowerShield#OUTPOST_SHARE_IN} is a Shieldbearer, the rest keep their weights among themselves.
     * Any other list, and every list with {@code shieldbearerSpawns} off, comes back as it was.
     */
    public static WeightedList<MobSpawnSettings.SpawnerData> outpostSpawns(StructureManager structures, MobCategory category,
                                                                          WeightedList<MobSpawnSettings.SpawnerData> found) {
        if (category != MobCategory.MONSTER || found.isEmpty() || !FullDrawConfig.shieldbearerSpawns()) return found;
        Extended last = lastOutpost;
        if (last != null && last.from() == found) return last.to();
        Structure outpost = structures.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(BuiltinStructures.PILLAGER_OUTPOST);
        StructureSpawnOverride own = outpost == null ? null : outpost.spawnOverrides().get(MobCategory.MONSTER);
        if (own == null || !own.spawns().equals(found)) return found;
        WeightedList.Builder<MobSpawnSettings.SpawnerData> extended = WeightedList.builder();
        int total = 0;
        for (Weighted<MobSpawnSettings.SpawnerData> entry : found.unwrap()) {
            extended.add(entry.value(), entry.weight() * (TowerShield.OUTPOST_SHARE_IN - 1));
            total += entry.weight();
        }
        extended.add(new MobSpawnSettings.SpawnerData(SHIELDBEARER.get(), ConstantInt.of(1)), total);
        WeightedList<MobSpawnSettings.SpawnerData> to = extended.build();
        lastOutpost = new Extended(found, to);
        return to;
    }

    /** Sized, tracked and updated as vanilla's arrow. */
    private static <E extends FullDrawArrow> DeferredHolder<EntityType<?>, EntityType<E>> arrow(String name, EntityType.EntityFactory<E> factory) {
        return ENTITIES.registerEntityType(name, factory, MobCategory.MISC,
                b -> b.noLootTable().sized(0.5F, 0.5F).eyeHeight(0.13F).clientTrackingRange(4).updateInterval(20));
    }
}
