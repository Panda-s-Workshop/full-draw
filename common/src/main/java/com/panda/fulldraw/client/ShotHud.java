package com.panda.fulldraw.client;

import com.panda.fulldraw.FullDrawClientConfig;
import com.panda.fulldraw.FullDrawConfig;
import com.panda.fulldraw.FullDrawMod;
import com.panda.fulldraw.logic.ShotMath;
import com.panda.fulldraw.logic.WeaponStats;
import com.panda.fulldraw.quiver.QuiverContents;
import com.panda.fulldraw.quiver.Quivers;
import com.panda.fulldraw.shot.Focus;
import com.panda.fulldraw.shot.Weapons;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import com.panda.fulldraw.network.FullDrawNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;

/**
 * The HUD: the draw ring around the crosshair, and the Quiver strip beside the hotbar. Both are thin
 * and small; nothing else on screen changes.
 */
public final class ShotHud {
    public static final Identifier RING_LAYER = FullDrawMod.id("draw_ring");
    public static final Identifier QUIVER_LAYER = FullDrawMod.id("quiver_strip");

    private static final int RING_RADIUS = 9;
    private static final int RING_DOTS = 28;
    private static final int WHITE = 0xDDFFFFFF, DIM = 0x88FFFFFF, GOLD = 0xFFFFD34D, GOLD_GLOW = 0x99FFB000, SHADOW = 0x66000000;
    private static final int HIT = 0xFFFFFFFF, PRECISE = 0xFFFF4040;
    /** The breath arc: right of the ring, from 60° above to 60° below the crosshair's level, emptying top-down. */
    private static final int BREATH_RADIUS = 13, BREATH_DOTS = 12;
    private static final int BREATH = 0xFF4FB4FF, BREATH_EMPTY = 0x44FFFFFF, BREATH_OUT = 0xEEFF5040, BREATH_EDGE = 0x99001830;
    private static final Identifier SLOT = Identifier.withDefaultNamespace("hud/hotbar_offhand_left");
    private static final Identifier SLOT_RIGHT = Identifier.withDefaultNamespace("hud/hotbar_offhand_right");
    private static final int SLOT_W = 29;

    /** "early" or "late" under the ring after a full draw the server scored outside the window. */
    private static long missedAt = -100;
    private static String missKey;

    private ShotHud() {}

    /** The draw ring: fills clockwise as the bow draws, glows gold while the Perfect window is open. */
    public static void drawRing(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.gui.hud.isHidden() || mc.gui.screen() != null) return;
        // vanilla hides its crosshair in third person; the ring and ticks belong to it
        if (!mc.options.getCameraType().isFirstPerson() || !FullDrawClientConfig.get().showRing()) return;
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        drawHitTick(g, p, cx, cy);
        boolean drawing = Focus.drawing(p);
        if (!drawing) {
            drawMiss(g, mc, p, cx, cy);
            drawBreath(g, cx, cy);
            return;
        }
        WeaponStats stats = Weapons.stats(p.getUseItem());
        int held = p.getTicksUsingItem();
        double fraction = ShotMath.drawFraction(held, stats);
        boolean perfect = ShotMath.isPerfect(held, stats, FullDrawConfig.clientTuning(), 0);
        boolean full = fraction >= 1.0;
        int lit = (int) Math.round(fraction * RING_DOTS);
        // the tremble: the ring fades and its dots shiver, more as the hold drags on
        double tremble = FocusClient.trembleNow(p);
        int dim = tremble > 0 ? fade(DIM, 1.0 - 0.15 * tremble) : DIM;
        long tick = p.level().getGameTime();
        for (int i = 0; i < RING_DOTS; i++) {
            double a = -Math.PI / 2 + i * 2 * Math.PI / RING_DOTS;
            double r = RING_RADIUS + (tremble > 0 ? Math.sin(tick * 1.7 + i * 2.3) * tremble : 0);
            int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
            int colour = i >= lit ? 0x22FFFFFF : perfect ? GOLD : full ? dim : WHITE;
            // a dark edge under every lit dot keeps the ring readable against sky and snow
            if (i < lit) g.fill(x, y + 1, x + 1, y + 2, SHADOW);
            g.fill(x, y, x + 1, y + 1, colour);
            if (perfect) {
                g.fill(x - 1, y, x, y + 1, GOLD_GLOW);
                g.fill(x + 1, y, x + 2, y + 1, GOLD_GLOW);
            }
        }
        drawBreath(g, cx, cy);
    }

    /**
     * Steady Aim's breath: a thin arc right of the ring, shown while the breath is held or isn't full (faded
     * while it refills between shots). It empties from the top; when the breath has run out, it turns red for the rest of
     * the draw. A dark edge right and below each dot keeps it readable against sky and cloud.
     */
    private static void drawBreath(GuiGraphicsExtractor g, int cx, int cy) {
        com.panda.fulldraw.logic.Breath b = FocusClient.breath();
        if (!b.focusing() && !b.exhausted() && b.fill() >= 1.0) return;
        int lit = (int) Math.ceil(b.fill() * BREATH_DOTS - 1e-6);
        for (int i = 0; i < BREATH_DOTS; i++) {
            // i = 0 at the bottom, so the arc drains from the top
            double a = Math.toRadians(60 - i * 120.0 / (BREATH_DOTS - 1));
            int x = cx + (int) Math.round(Math.cos(a) * BREATH_RADIUS), y = cy + (int) Math.round(Math.sin(a) * BREATH_RADIUS);
            int colour = b.exhausted() ? BREATH_OUT : i < lit ? (b.focusing() ? BREATH : fade(BREATH, 0.6)) : BREATH_EMPTY;
            if (i < lit || b.exhausted()) {
                g.fill(x, y + 1, x + 1, y + 2, BREATH_EDGE);
                g.fill(x + 1, y, x + 2, y + 1, BREATH_EDGE);
            }
            g.fill(x, y, x + 1, y + 1, colour);
        }
    }

    /** The colour with its alpha scaled. */
    private static int fade(int argb, double by) {
        int alpha = (int) Math.round((argb >>> 24) * Math.clamp(by, 0.0, 1.0));
        return alpha << 24 | (argb & 0xFFFFFF);
    }

    /** The server's verdict on a release: a miss shows under the ring; a Perfect clears the hint. */
    public static void onReleaseResult(FullDrawNetwork.ReleaseResult result) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (result.verdict() == FullDrawNetwork.ReleaseResult.PERFECT) {
            missKey = null;
            mc.gui.hud.setOverlayMessage(Component.empty(), false);
            if (com.panda.fulldraw.FullDrawClientConfig.get().perfectChime()) {
                mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        com.panda.fulldraw.registry.ModSounds.PERFECT_RELEASE.get(), 1.0F, 0.8F));
                var bow = com.panda.fulldraw.shot.Shots.flashPoint(mc.player);
                var random = mc.level.getRandom();
                for (int i = 0; i < 2; i++)
                    mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.FIREWORK, bow.x, bow.y, bow.z,
                            random.nextGaussian() * 0.01, random.nextGaussian() * 0.01, random.nextGaussian() * 0.01);
            }
            return;
        }
        missKey = result.verdict() == FullDrawNetwork.ReleaseResult.EARLY ? "hud.fulldraw.early" : "hud.fulldraw.late";
        missedAt = mc.player.level().getGameTime();
    }

    private static void drawMiss(GuiGraphicsExtractor g, Minecraft mc, LocalPlayer p, int cx, int cy) {
        long age = p.level().getGameTime() - missedAt;
        if (age < 0 || age > 14 || missKey == null) return;
        g.centeredText(mc.font, Component.translatable(missKey), cx, cy + RING_RADIUS + 3, 0xCCFFB0A0);
    }

    /** A short tick on the crosshair's corners after a hit: white, or red for a precision hit. */
    private static void drawHitTick(GuiGraphicsExtractor g, LocalPlayer p, int cx, int cy) {
        long age = p.level().getGameTime() - ShotFeedback.lastHitTick;
        if (age < 0 || age > 8) return;
        int colour = ShotFeedback.lastHitPrecise ? PRECISE : HIT;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                for (int k = 3; k <= 5; k++) g.fill(cx + sx * k, cy + sy * k, cx + sx * k + 1, cy + sy * k + 1, colour);
            }
        }
    }

    /**
     * The Quiver strip: vanilla's offhand slot sprite, the selected arrows nearest the hotbar and the next kinds behind
     * them, dimmed. It keeps clear of where the offhand slot draws, and greys out while arrows held in a hand will fire
     * first.
     */
    public static void drawQuiver(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        FullDrawClientConfig.QuiverStrip place = FullDrawClientConfig.get().quiverStrip();
        if (p == null || p.isSpectator() || mc.gui.hud.isHidden() || mc.gui.screen() != null || place == FullDrawClientConfig.QuiverStrip.HIDDEN) return;
        ItemStack weapon = p.getMainHandItem().getItem() instanceof ProjectileWeaponItem ? p.getMainHandItem() : p.getOffhandItem();
        if (!(weapon.getItem() instanceof ProjectileWeaponItem item)) return;
        ItemStack quiver = Quivers.find(p);
        if (quiver.isEmpty()) return;
        QuiverContents contents = Quivers.contents(quiver);
        boolean handFirst = !ProjectileWeaponItem.getHeldProjectile(p, item.getSupportedHeldProjectiles()).isEmpty();
        // left: beyond where the offhand slot draws, the next kinds further out; right: the mirror image
        boolean right = place == FullDrawClientConfig.QuiverStrip.RIGHT;
        int x0 = right ? g.guiWidth() / 2 + 91 + SLOT_W + 4 : g.guiWidth() / 2 - 91 - SLOT_W - 4 - SLOT_W;
        int step = right ? 22 : -22;
        // vanilla's right-hand slot sprite puts the item 10 px in, the left one 3
        Identifier sprite = right ? SLOT_RIGHT : SLOT;
        int in = right ? 10 : 3;
        int y = g.guiHeight() - 23;
        int n = contents.stacks().size();
        if (n == 0) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x0, y, SLOT_W, 24, 0x88FFFFFF);
            g.item(quiver, x0 + in, y + 4);
            g.fill(x0 + in, y + 4, x0 + in + 16, y + 20, 0x88000000);
            return;
        }
        for (int i = Math.min(3, n) - 1; i >= 0; i--) {
            ItemStack stack = contents.stacks().get((contents.selected() + i) % n).create();
            int x = x0 + i * step;
            boolean dim = i > 0 || handFirst;
            g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, SLOT_W, 24, dim ? 0xAAFFFFFF : 0xFFFFFFFF);
            g.item(stack, x + in, y + 4);
            g.itemDecorations(mc.font, stack, x + in, y + 4);
            if (dim) g.fill(x + in, y + 4, x + in + 16, y + 20, i > 0 ? 0x77000000 : 0x99000000);
        }
    }
}
