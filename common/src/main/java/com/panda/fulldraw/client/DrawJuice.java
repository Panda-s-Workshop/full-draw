package com.panda.fulldraw.client;

import com.panda.fulldraw.FullDrawClientConfig;
import com.panda.fulldraw.logic.ShotMath;
import com.panda.fulldraw.logic.WeaponStats;
import com.panda.fulldraw.registry.ModSounds;
import com.panda.fulldraw.shot.Focus;
import com.panda.fulldraw.shot.Weapons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;

/**
 * The draw you can hear and see in the hand: the limbs creak in three steps as the bow bends, the wood
 * strains while a held draw trembles, the bow shakes in the hand, and the string twangs on release, pitched by the
 * weapon's weight. All of it is the archer's own (non-positional) and each part has a client toggle.
 */
public final class DrawJuice {
    /** The draw fractions where the limbs creak, rising in pitch. */
    private static final double[] CREAK_AT = {0.34, 0.67, 1.0};
    private static final float[] CREAK_PITCH = {0.85F, 1.0F, 1.18F};
    /** The bow's shake in the hand at full tremble, in blocks. */
    public static final float SHAKE = 0.012F;

    private static int creaks, strainIn;

    private DrawJuice() {}

    /** After each client tick (FocusClient has already ticked the breath). */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.isPaused()) return;
        if (!Focus.drawing(p)) {
            creaks = 0;
            strainIn = 0;
            return;
        }
        if (!FullDrawClientConfig.get().drawSounds()) return;
        WeaponStats stats = Weapons.stats(p.getUseItem());
        double fraction = ShotMath.drawFraction(p.getTicksUsingItem(), stats);
        while (creaks < CREAK_AT.length && fraction >= CREAK_AT[creaks]) {
            play(mc, ModSounds.DRAW_CREAK.get(), CREAK_PITCH[creaks], 0.22F + 0.06F * creaks);
            creaks++;
        }
        double tremble = FocusClient.trembleNow(p);
        if (tremble > 0 && --strainIn <= 0) {
            play(mc, ModSounds.STRAIN.get(), 0.55F + p.getRandom().nextFloat() * 0.15F, (float) (0.12 + 0.3 * tremble));
            strainIn = Math.max(6, (int) Math.round(16 - 10 * tremble));
        }
    }

    /** The string, when FocusClient sees a real release of a draw held {@code ticksHeld} ticks. */
    static void onRelease(LocalPlayer p, WeaponStats stats, int ticksHeld) {
        if (!FullDrawClientConfig.get().drawSounds()) return;
        double fraction = ShotMath.drawFraction(ticksHeld, stats);
        play(Minecraft.getInstance(), ModSounds.TWANG.get(), twangPitch(stats), (float) (0.25 + 0.35 * fraction));
    }

    /** Light bows (a draw of 14 ticks or less) twang high, heavy ones (26 or more) low. */
    public static float twangPitch(WeaponStats stats) {
        return stats.drawTicks() <= 14 ? 1.35F : stats.drawTicks() >= 26 ? 0.8F : 1.1F;
    }

    /** Frames the hand renderer moved the bow by a non-zero shake (for tests). */
    public static int shakenFrames;

    /** The bow's offset in the hand this frame (x, y in blocks), or zeros. */
    public static float[] shake(LocalPlayer p, float partialTick) {
        if (!FullDrawClientConfig.get().bowShake()) return new float[] {0, 0};
        float k = (float) FocusClient.trembleNow(p);
        if (k <= 0) return new float[] {0, 0};
        float t = p.tickCount + partialTick;
        float amp = SHAKE * k;
        return new float[] {amp * Mth.sin(t * 2.9F), amp * Mth.sin(t * 3.7F + 1.3F)};
    }

    private static void play(Minecraft mc, SoundEvent sound, float pitch, float volume) {
        mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }
}
