package com.panda.fulldraw.registry;

import com.panda.fulldraw.FullDrawMod;
import com.panda.fulldraw.advancement.FullDrawTrigger;
import com.panda.fulldraw.platform.registry.DeferredRegister;
import java.util.function.Supplier;
import net.minecraft.advancements.triggers.CriterionTrigger;
import net.minecraft.core.registries.Registries;

public final class ModTriggers {
    private static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, FullDrawMod.MODID);

    /** {@code fulldraw:event}: perfect_release, hold_your_breath, precision_hit, long_shot (value: metres), rope_trick (value: ropes hung). */
    public static final Supplier<FullDrawTrigger> EVENT = TRIGGERS.register("event", FullDrawTrigger::new);

    private ModTriggers() {}
}
