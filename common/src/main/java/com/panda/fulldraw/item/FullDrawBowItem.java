package com.panda.fulldraw.item;

import com.panda.fulldraw.logic.WeaponStats;
import com.panda.fulldraw.shot.Shots;
import com.panda.fulldraw.shot.Weapons;
import net.minecraft.world.entity.LivingEntity;
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

    public FullDrawBowItem(Properties properties, WeaponStats stats) {
        super(properties);
        this.stats = stats;
    }

    @Override
    public WeaponStats stats() {
        return stats;
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
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
