package com.panda.fulldraw.client;

import com.panda.fulldraw.FullDrawClientConfig;
import com.panda.fulldraw.FullDrawConfig;
import com.panda.fulldraw.logic.Breath;
import com.panda.fulldraw.logic.ShotMath;
import com.panda.fulldraw.logic.WeaponStats;
import com.panda.fulldraw.network.FullDrawNetwork;
import com.panda.fulldraw.registry.ModSounds;
import com.panda.fulldraw.shot.Focus;
import com.panda.fulldraw.shot.Weapons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The archer's own view of the draw: the local breath prediction, the FOV (bow zoom for every bow,
 * Steady Aim's ×0.8), the release kick and the tremble's sway. All of it is client-only; the server decides the spread.
 */
public final class FocusClient {
    /** Steady Aim narrows the view to this. */
    public static final float FOCUS_FOV = 0.8F;
    /** The release kick: the view springs out by this much and settles back over {@link #KICK_TICKS} ticks. */
    public static final float KICK_PUNCH = 0.04F;
    public static final int KICK_TICKS = 3;
    /** The tremble's sway at its fullest, in degrees. */
    public static final float SWAY_DEGREES = 0.5F;
    /** How fast the sway's strength follows the tremble, per tick: no jerk when the focus ends. */
    private static final float SWAY_EASE = 0.1F;

    private static Breath breath = Breath.FULL;
    private static long lastInhale = -100;
    private static boolean wasDrawing;
    private static int drawSlot, kickAge = -1, lastHeld;
    private static WeaponStats drawStats = WeaponStats.VANILLA_BOW;
    /** The sway's strength, eased: this tick's and the last. */
    private static float swayK, swayKO;
    /** The sway offset already turned into the player's aim, so each frame applies only the change. */
    private static float swayYaw, swayPitch;

    private FocusClient() {}

    public static Breath breath() {
        return breath;
    }

    /**
     * The server's breath, taken as it is. A correction that gets somewhere first (the server ran out before the
     * prediction did) plays that sound; the prediction then finds nothing new and stays quiet.
     */
    public static void onBreathState(FullDrawNetwork.BreathState state) {
        Breath before = breath;
        breath = new Breath(state.fill(), state.focusing(), state.exhaustedTicks());
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) playEvent(mc, Breath.event(before, breath), mc.player.level().getGameTime());
    }

    private static void playEvent(Minecraft mc, Breath.Event event, long now) {
        if (event == Breath.Event.INHALE && now - lastInhale >= Focus.INHALE_SOUND_GAP) {
            lastInhale = now;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.INHALE.get(), 1.0F, 0.6F));
        } else if (event == Breath.Event.EXHALE) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.EXHALE.get(), 1.0F, 0.6F));
        }
    }

    /** After each client tick. Nothing moves while the game is paused (the server's breath doesn't either). */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) {
            breath = Breath.FULL;
            return;
        }
        if (mc.isPaused()) return;
        Breath before = breath;
        boolean drawing = Focus.drawing(p);
        breath = breath.tick(Focus.wantsFocus(p), drawing, Weapons.steadyLevel(p.getUseItem()));
        playEvent(mc, Breath.event(before, breath), p.level().getGameTime());

        // the kick: only a release (the use key let go, same hotbar slot) of a draw long enough to fire
        if (kickAge >= 0 && ++kickAge >= KICK_TICKS) kickAge = -1;
        if (wasDrawing && !drawing && lastHeld >= 3 && !mc.options.keyUse.isDown() && p.getInventory().getSelectedSlot() == drawSlot) {
            kickAge = 0;
            DrawJuice.onRelease(p, drawStats, lastHeld);
        }
        if (drawing) {
            lastHeld = p.getTicksUsingItem();
            drawSlot = p.getInventory().getSelectedSlot();
            drawStats = Weapons.stats(p.getUseItem());
        }
        wasDrawing = drawing;

        swayKO = swayK;
        swayK = Mth.approach(swayK, (float) trembleNow(p), SWAY_EASE);
    }

    /**
     * Multiplies vanilla's FOV modifier (already scaled by the FOV-effects option): bow zoom for bows that aren't
     * vanilla's (it checks {@code Items.BOW} by identity) and Steady Aim, each scaled by that option. The camera eases
     * toward the result by half each tick, so nothing here snaps.
     */
    public static float fovModifier(LocalPlayer p, float vanilla, float effectScale) {
        if (!FullDrawClientConfig.get().fovEffects()) return vanilla;
        float mult = 1.0F;
        ItemStack using = p.isUsingItem() ? p.getUseItem() : ItemStack.EMPTY;
        // our zoom for bows vanilla doesn't zoom, unless the bow is opted out of the pipeline (fulldraw:no_shot_pipeline)
        if (Focus.drawing(p) && !using.is(Items.BOW)) {
            WeaponStats stats = Weapons.stats(using);
            float g = Math.min(1.0F, p.getTicksUsingItem() / (float) stats.drawTicks());
            mult *= 1.0F - g * g * 0.15F;
        }
        if (breath.focusing()) mult *= FOCUS_FOV;
        return vanilla * Mth.lerp(effectScale, 1.0F, mult);
    }

    /** The largest kick factor the camera has applied since it was last reset (for tests). */
    public static float appliedKick = 1.0F;

    /** The release kick's FOV factor this frame: 1 + {@link #KICK_PUNCH} at the release, back to 1 over three ticks. */
    public static float kick(float partialTick) {
        if (kickAge < 0 || !FullDrawClientConfig.get().fovEffects()) return 1.0F;
        float t = (kickAge + partialTick) / KICK_TICKS;
        if (t >= 1.0F) return 1.0F;
        float scale = Minecraft.getInstance().options.fovEffectScale().get().floatValue();
        float k = 1.0F + KICK_PUNCH * scale * (1.0F - t);
        appliedKick = Math.max(appliedKick, k);
        return k;
    }

    /** How far the tremble has grown, 0 to 1, by the same rule as the server's spread (the HUD dims the ring by it). */
    public static double trembleNow(LocalPlayer p) {
        if (!Focus.drawing(p) || breath.focusing()) return 0;
        WeaponStats stats = Weapons.stats(p.getUseItem());
        return ShotMath.tremble(stats, p.getTicksUsingItem() - stats.drawTicks(), breath.exhaustedTicks(), FullDrawConfig.tuning());
    }

    /**
     * The tremble's sway (yaw, pitch offsets in degrees): it grows with the server's spread rule, fades out while
     * focusing, and starts at once when the breath runs out.
     */
    public static float[] sway(LocalPlayer p, float partialTick) {
        if (!Focus.drawing(p)) return new float[] {0, 0};
        float k = Mth.lerp(partialTick, swayKO, swayK);
        if (k <= 0) return new float[] {0, 0};
        float time = (p.tickCount + partialTick) * 0.35F;
        float amp = SWAY_DEGREES * k;
        return new float[] {amp * Mth.sin(time * 0.9F), amp * 0.7F * Mth.sin(time * 1.3F + 1.1F)};
    }

    /**
     * Once per frame, before the camera reads the player's rotation: turns the player's aim by the change in sway, so the
     * crosshair always shows where the arrow will go and the archer has to steady it by hand. When the
     * sway stops, the aim stays where it drifted to rather than snapping back.
     */
    public static void applySway(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.getCameraEntity() != p || mc.isPaused()) return;
        float[] s = sway(p, partialTick);
        if (s[0] == 0 && s[1] == 0) {
            swayYaw = swayPitch = 0;
            return;
        }
        float dy = s[0] - swayYaw, dp = s[1] - swayPitch;
        swayYaw = s[0];
        swayPitch = s[1];
        // Entity.turn scales by 0.15 (mouse units)
        p.turn(dy / 0.15, dp / 0.15);
    }
}
