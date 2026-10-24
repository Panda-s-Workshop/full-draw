package com.panda.fulldraw.registry;

import com.panda.fulldraw.FullDrawMod;
import com.panda.fulldraw.platform.registry.DeferredHolder;
import com.panda.fulldraw.platform.registry.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;

/** The mod's sound events; {@code assets/fulldraw/sounds.json} says which files each one plays. */
public final class ModSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, FullDrawMod.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> PERFECT_RELEASE = sound("perfect_release");
    public static final DeferredHolder<SoundEvent, SoundEvent> PRECISION_HIT = sound("precision_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> HIT_TICK = sound("hit_tick");
    public static final DeferredHolder<SoundEvent, SoundEvent> QUIVER_CYCLE = sound("quiver_cycle");
    public static final DeferredHolder<SoundEvent, SoundEvent> QUIVER_INSERT = sound("quiver_insert");
    public static final DeferredHolder<SoundEvent, SoundEvent> QUIVER_REMOVE = sound("quiver_remove");
    public static final DeferredHolder<SoundEvent, SoundEvent> INHALE = sound("inhale");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXHALE = sound("exhale");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOW_BASH = sound("bow_bash");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRAW_CREAK = sound("draw_creak");
    public static final DeferredHolder<SoundEvent, SoundEvent> STRAIN = sound("strain");
    public static final DeferredHolder<SoundEvent, SoundEvent> TWANG = sound("twang");

    private ModSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(FullDrawMod.id(name)));
    }
}
