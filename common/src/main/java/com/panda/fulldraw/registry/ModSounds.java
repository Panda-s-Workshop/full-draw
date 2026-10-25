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
    public static final DeferredHolder<SoundEvent, SoundEvent> ROPE_PLACE = sound("rope_place");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROPE_SLIP = sound("rope_slip");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLEED = sound("bleed");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHIELDBEARER_BLOCK = sound("shieldbearer_block");
    public static final DeferredHolder<SoundEvent, SoundEvent> MARKSMAN_DRAW = sound("marksman_draw");
    public static final DeferredHolder<SoundEvent, SoundEvent> RICOCHET = sound("ricochet");
    public static final DeferredHolder<SoundEvent, SoundEvent> MARKSMAN_PARAPET = sound("marksman_parapet");
    public static final DeferredHolder<SoundEvent, SoundEvent> MARKSMAN_PHASE_TWO = sound("marksman_phase_two");
    public static final DeferredHolder<SoundEvent, SoundEvent> MARKSMAN_AMBIENT = sound("marksman_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> MARKSMAN_HURT = sound("marksman_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> MARKSMAN_DEATH = sound("marksman_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHIELDBEARER_STAGGER = sound("shieldbearer_stagger");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHIELDBEARER_CRACK = sound("shieldbearer_crack");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHIELDBEARER_BASH = sound("shieldbearer_bash");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHIELDBEARER_AMBIENT = sound("shieldbearer_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHIELDBEARER_HURT = sound("shieldbearer_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHIELDBEARER_DEATH = sound("shieldbearer_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHIELDBEARER_CELEBRATE = sound("shieldbearer_celebrate");
    public static final DeferredHolder<SoundEvent, SoundEvent> HARRIER_AMBIENT = sound("gale_harrier_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> HARRIER_SHRIEK = sound("gale_harrier_shriek");
    public static final DeferredHolder<SoundEvent, SoundEvent> HARRIER_DIVE = sound("gale_harrier_dive");
    public static final DeferredHolder<SoundEvent, SoundEvent> HARRIER_FLAP = sound("gale_harrier_flap");
    public static final DeferredHolder<SoundEvent, SoundEvent> HARRIER_GROUNDED = sound("gale_harrier_grounded");
    public static final DeferredHolder<SoundEvent, SoundEvent> HARRIER_LAND = sound("gale_harrier_land");
    public static final DeferredHolder<SoundEvent, SoundEvent> HARRIER_HURT = sound("gale_harrier_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> HARRIER_DEATH = sound("gale_harrier_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> LURKER_AMBIENT = sound("bog_lurker_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> LURKER_GURGLE = sound("bog_lurker_gurgle");
    public static final DeferredHolder<SoundEvent, SoundEvent> LURKER_SPIT = sound("bog_lurker_spit");
    public static final DeferredHolder<SoundEvent, SoundEvent> LURKER_SPIT_HIT = sound("bog_lurker_spit_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> LURKER_SINK = sound("bog_lurker_sink");
    public static final DeferredHolder<SoundEvent, SoundEvent> LURKER_HURT = sound("bog_lurker_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> LURKER_DEATH = sound("bog_lurker_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLARE = sound("flare");

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
