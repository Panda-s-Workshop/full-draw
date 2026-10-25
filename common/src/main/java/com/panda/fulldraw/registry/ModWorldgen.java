package com.panda.fulldraw.registry;

import com.panda.fulldraw.FullDrawMod;
import com.panda.fulldraw.platform.registry.DeferredRegister;
import com.panda.fulldraw.worldgen.DryLandStructure;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;

/** The mod's structure types; the structures themselves are data. */
public final class ModWorldgen {
    private static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, FullDrawMod.MODID);

    /** A jigsaw structure that never generates in water (the Ranger's Watchtower). */
    public static final Supplier<StructureType<DryLandStructure>> DRY_LAND = STRUCTURE_TYPES.register("dry_land", () -> () -> DryLandStructure.CODEC);

    private ModWorldgen() {}
}
