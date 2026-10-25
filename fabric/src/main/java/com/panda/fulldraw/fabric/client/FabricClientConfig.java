package com.panda.fulldraw.fabric.client;

import com.panda.fulldraw.FullDrawClientConfig;
import com.panda.fulldraw.fabric.PropertiesConfig;

/** Each player's presentation settings on Fabric: config/fulldraw-client.properties. */
final class FabricClientConfig implements FullDrawClientConfig.Source {
    private final boolean ring, readout, fov, trail, draw, chime, hit, shake;
    private final FullDrawClientConfig.QuiverStrip strip;

    private FabricClientConfig(PropertiesConfig c) {
        FullDrawClientConfig.Source d = FullDrawClientConfig.DEFAULTS;
        ring = c.bool("showRing", d.showRing(), "The draw ring, the hit tick and early/late around the crosshair");
        readout = c.bool("distanceReadout", d.distanceReadout(), "\"Long shot: N m\" on the action bar");
        fov = c.bool("fovEffects", d.fovEffects(), "Bow zoom on the bows the shot rules cover, Steady Aim's narrower view and the release kick");
        trail = c.bool("perfectTrail", d.perfectTrail(), "The particle trail behind Perfect arrows");
        draw = c.bool("drawSounds", d.drawSounds(), "The draw creak, the strain of a held draw, the twang on release and your own breath");
        chime = c.bool("perfectChime", d.perfectChime(), "The chime and the flash at the bow of your own Perfect Release");
        hit = c.bool("hitSounds", d.hitSounds(), "The hit tick and the precision thock");
        shake = c.bool("bowShake", d.bowShake(), "The bow shaking in the hand when a draw is held too long");
        strip = c.choice("quiverStrip", d.quiverStrip(), "Where the Quiver strip sits beside the hotbar");
        c.save("Full Draw: client settings (only how things look and sound; the server decides every shot)");
    }

    static FabricClientConfig load() {
        return new FabricClientConfig(new PropertiesConfig("fulldraw-client.properties"));
    }

    @Override public boolean showRing() { return ring; }
    @Override public boolean distanceReadout() { return readout; }
    @Override public boolean fovEffects() { return fov; }
    @Override public boolean perfectTrail() { return trail; }
    @Override public boolean drawSounds() { return draw; }
    @Override public boolean perfectChime() { return chime; }
    @Override public boolean hitSounds() { return hit; }
    @Override public boolean bowShake() { return shake; }
    @Override public FullDrawClientConfig.QuiverStrip quiverStrip() { return strip; }
}
