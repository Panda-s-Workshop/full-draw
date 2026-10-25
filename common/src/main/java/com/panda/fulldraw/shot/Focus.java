package com.panda.fulldraw.shot;

import com.panda.fulldraw.logic.Breath;
import com.panda.fulldraw.logic.WeaponStats;
import com.panda.fulldraw.network.FullDrawNetwork;
import com.panda.fulldraw.registry.ModSounds;
import com.panda.fulldraw.registry.ModTriggers;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;

/**
 * Steady Aim's server side: each player's breath, ticked from what the server sees (sneaking at full draw).
 * It isn't saved: a relog starts with a full breath. The client runs the same {@link Breath} for its own HUD, FOV and
 * sounds and is corrected whenever the breath changes phase; the server's copy decides the spread.
 */
public final class Focus {
    private static final Map<Player, Breath> BREATH = new WeakHashMap<>();
    /** When each player last drew a breath in: the inhale sounds at most once per {@link #INHALE_SOUND_GAP} ticks. */
    private static final Map<Player, Long> LAST_INHALE = new WeakHashMap<>();
    public static final int INHALE_SOUND_GAP = 15;

    private Focus() {}

    public static Breath of(Player player) {
        return BREATH.getOrDefault(player, Breath.FULL);
    }

    /** Whether a player is drawing a bow (at any stage). */
    public static boolean drawing(Player player) {
        return player.isUsingItem() && player.getUseItem().getItem() instanceof BowItem && Weapons.inPipeline(player.getUseItem());
    }

    /** Whether a player is holding a full draw while sneaking: the input Steady Aim reads, on either side. */
    public static boolean wantsFocus(Player player) {
        if (!player.isShiftKeyDown() || !drawing(player)) return false;
        WeaponStats stats = Weapons.stats(player.getUseItem());
        return player.getTicksUsingItem() >= stats.drawTicks();
    }

    /** Sets a player's breath outright: GameTests only. */
    public static void set(Player player, Breath breath) {
        if (breath.equals(Breath.FULL)) BREATH.remove(player);
        else BREATH.put(player, breath);
    }

    /** Every server tick, for every player. */
    public static void tick(Player player) {
        Breath before = of(player);
        Breath after = before.tick(wantsFocus(player), drawing(player), Weapons.steadyLevel(player.getUseItem()));
        set(player, after);
        if (before.focusing() != after.focusing() || before.exhausted() != after.exhausted())
            if (player instanceof ServerPlayer sp)
                FullDrawNetwork.send(sp, new FullDrawNetwork.BreathState((float) after.fill(), after.focusing(), after.exhaustedTicks()));
        if (!before.focusing() && after.focusing() && player instanceof ServerPlayer sp) ModTriggers.EVENT.get().trigger(sp, "hold_your_breath");
        Breath.Event event = Breath.event(before, after);
        if (event == Breath.Event.NONE) return;
        if (event == Breath.Event.INHALE) {
            long now = player.level().getGameTime();
            Long last = LAST_INHALE.put(player, now);
            if (last != null && now - last < INHALE_SOUND_GAP) return;
        }
        // everyone near hears it; the archer's own client plays its prediction, so it's left out here
        var sound = event == Breath.Event.INHALE ? ModSounds.INHALE.get() : ModSounds.EXHALE.get();
        player.level().playSound(player, player.getX(), player.getEyeY(), player.getZ(), sound, SoundSource.PLAYERS, 0.5F, 1.0F);
    }
}
