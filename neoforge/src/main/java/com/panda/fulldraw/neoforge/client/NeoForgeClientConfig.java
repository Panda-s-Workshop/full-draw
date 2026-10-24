package com.panda.fulldraw.neoforge.client;

import com.panda.fulldraw.FullDrawClientConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Each player's presentation settings as a NeoForge client config. */
public final class NeoForgeClientConfig implements FullDrawClientConfig.Source {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    private static final FullDrawClientConfig.Source D = FullDrawClientConfig.DEFAULTS;

    private static final ModConfigSpec.BooleanValue RING = B.comment("The draw ring, the hit tick and early/late around the crosshair.")
            .define("showRing", D.showRing());
    private static final ModConfigSpec.BooleanValue READOUT = B.comment("\"Long shot: N m\" on the action bar.")
            .define("distanceReadout", D.distanceReadout());
    private static final ModConfigSpec.BooleanValue FOV = B.comment("Bow zoom on every bow, Steady Aim's narrower view and the release kick.")
            .define("fovEffects", D.fovEffects());
    private static final ModConfigSpec.BooleanValue TRAIL = B.comment("The particle trail behind Perfect arrows.")
            .define("perfectTrail", D.perfectTrail());
    private static final ModConfigSpec.BooleanValue DRAW = B.comment("The draw creak, the strain of a held draw and the twang on release.")
            .define("drawSounds", D.drawSounds());
    private static final ModConfigSpec.BooleanValue HIT = B.comment("The hit tick and the precision thock.")
            .define("hitSounds", D.hitSounds());
    private static final ModConfigSpec.BooleanValue SHAKE = B.comment("The bow shaking in the hand when a draw is held too long.")
            .define("bowShake", D.bowShake());
    private static final ModConfigSpec.EnumValue<FullDrawClientConfig.QuiverStrip> STRIP = B
            .comment("Where the Quiver strip sits beside the hotbar: LEFT, RIGHT or HIDDEN.")
            .defineEnum("quiverStrip", D.quiverStrip());

    public static final ModConfigSpec SPEC = B.build();

    private static boolean on(ModConfigSpec.BooleanValue value, boolean fallback) {
        return SPEC.isLoaded() ? value.get() : fallback;
    }

    @Override public boolean showRing() { return on(RING, D.showRing()); }
    @Override public boolean distanceReadout() { return on(READOUT, D.distanceReadout()); }
    @Override public boolean fovEffects() { return on(FOV, D.fovEffects()); }
    @Override public boolean perfectTrail() { return on(TRAIL, D.perfectTrail()); }
    @Override public boolean drawSounds() { return on(DRAW, D.drawSounds()); }
    @Override public boolean hitSounds() { return on(HIT, D.hitSounds()); }
    @Override public boolean bowShake() { return on(SHAKE, D.bowShake()); }

    @Override
    public FullDrawClientConfig.QuiverStrip quiverStrip() {
        return SPEC.isLoaded() ? STRIP.get() : D.quiverStrip();
    }
}
