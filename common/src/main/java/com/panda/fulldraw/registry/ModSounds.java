package com.panda.fulldraw.registry;

import com.panda.fulldraw.FullDrawMod;
import com.panda.fulldraw.platform.registry.DeferredHolder;
import com.panda.fulldraw.platform.registry.DeferredRegister;
import net.minecraft.core.registries.Registries;
import java.util.Optional;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.CrossbowItem;

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
    public static final DeferredHolder<SoundEvent, SoundEvent> ARBALEST_CRANK_START = sound("arbalest_crank_start");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARBALEST_CRANK_MIDDLE = sound("arbalest_crank_middle");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARBALEST_CRANK_END = sound("arbalest_crank_end");
    public static final DeferredHolder<SoundEvent, SoundEvent> REPEATER_CLICK = sound("repeater_click");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOLT_PIERCE = sound("bolt_pierce");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOLT_ARMOUR = sound("bolt_armour");

    private ModSounds() {}

    /** The Arbalest's crank: vanilla's loading set, slower and lower, as the three stages vanilla plays. */
    public static CrossbowItem.ChargingSounds arbalestCrank() {
        return new CrossbowItem.ChargingSounds(Optional.of(ARBALEST_CRANK_START.holder()), Optional.of(ARBALEST_CRANK_MIDDLE.holder()),
                Optional.of(ARBALEST_CRANK_END.holder()));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(FullDrawMod.id(name)));
    }
}
