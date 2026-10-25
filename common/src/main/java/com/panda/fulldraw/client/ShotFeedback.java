package com.panda.fulldraw.client;

import com.panda.fulldraw.network.FullDrawNetwork;
import com.panda.fulldraw.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;

/**
 * The shooter's side of a hit: a hit tick, the precision "thock", a red tick on the crosshair, and
 * the distance on the action bar for long shots. The server already dealt the damage; this only tells the player.
 */
public final class ShotFeedback {
    /** Hits from this far show their distance on the action bar. */
    public static final float LONG_SHOT = 30.0F;

    /** Game time of the last hit, and whether it was a precision hit: the crosshair tick reads them. */
    static long lastHitTick = -100;
    static boolean lastHitPrecise;
    private static FullDrawNetwork.ShotResult last;

    private ShotFeedback() {}

    /** The last hit the server reported (for tests), or null. */
    public static FullDrawNetwork.ShotResult last() {
        return last;
    }

    public static void onShotResult(FullDrawNetwork.ShotResult result) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var config = com.panda.fulldraw.FullDrawClientConfig.get();
        if (config.hitSounds()) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.HIT_TICK.get(), result.precision() ? 1.1F : 1.6F, 0.45F));
            if (result.precision()) mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.PRECISION_HIT.get(), 1.0F, 0.9F));
        }
        lastHitTick = mc.player.level().getGameTime();
        lastHitPrecise = result.precision();
        last = result;
        if (result.distance() >= LONG_SHOT && config.distanceReadout()) {
            mc.gui.hud.setOverlayMessage(Component.translatable("message.fulldraw.long_shot", Math.round(result.distance())), false);
        }
    }

    /** A creeper's fuse the server put out: this client's count goes back to 0 with it. */
    public static void onFuseOut(FullDrawNetwork.FuseOut payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getEntity(payload.entityId()) instanceof net.minecraft.world.entity.monster.Creeper creeper) {
            creeper.swell = 0;
            creeper.oldSwell = 0;
        }
    }

    /** The server's word on an arrow's gravity: set on this client's copy so its flight matches. */
    public static void onArrowGravity(FullDrawNetwork.ArrowGravity payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getEntity(payload.entityId()) instanceof com.panda.fulldraw.shot.ShotHolder arrow)
            arrow.fulldraw$setClientGravity(payload.mult());
    }
}
