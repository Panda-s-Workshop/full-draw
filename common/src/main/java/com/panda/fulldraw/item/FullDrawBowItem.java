package com.panda.fulldraw.item;

import com.panda.fulldraw.logic.WeaponStats;
import com.panda.fulldraw.shot.Shots;
import com.panda.fulldraw.shot.Weapons;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A Full Draw bow. It is a vanilla bow with its own draw time: the release opens the shot pipeline with
 * the real ticks held, then hands vanilla the ticks a 20-tick bow would have needed for the same draw, so vanilla's
 * power curve, sounds, ammo use and the loaders' arrow events all still run.
 */
public class FullDrawBowItem extends BowItem implements Weapons.Ranged {
    private final WeaponStats stats;
    private final boolean startsWhileSprinting;

    public FullDrawBowItem(Properties properties, WeaponStats stats) {
        this(properties, stats, true);
    }

    /** @param startsWhileSprinting false for a bow too long to raise at a run: sprinting players can't start a draw */
    public FullDrawBowItem(Properties properties, WeaponStats stats, boolean startsWhileSprinting) {
        super(properties);
        this.stats = stats;
        this.startsWhileSprinting = startsWhileSprinting;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!startsWhileSprinting && player.isSprinting()) return InteractionResult.FAIL;
        return super.use(level, player, hand);
    }

    @Override
    public WeaponStats stats() {
        return stats;
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        // opted out (fulldraw:no_shot_pipeline): a plain vanilla bow, so the quick draw doesn't come without its penalties
        if (!Weapons.inPipeline(stack)) return super.releaseUsing(stack, level, entity, timeLeft);
        int duration = getUseDuration(stack, entity);
        int held = duration - timeLeft;
        Shots.beginRelease(entity, stack, held);
        try {
            return super.releaseUsing(stack, level, entity, duration - stats.toVanillaTicks(held));
        } finally {
            Shots.endRelease();
        }
    }
}
