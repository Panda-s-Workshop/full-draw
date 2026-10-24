package com.panda.fulldraw.shot;

import com.panda.fulldraw.registry.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;

/**
 * Bow Bash: a melee hit with a bow or crossbow shoves the target back about three blocks, then the weapon
 * rests for a second (no bash, no draw), shown as the item's cooldown. The hit's damage is vanilla's empty-hand 1.
 */
public final class BowBash {
    /**
     * The shove, on top of the hit's own 0.4: knockback 1.2 in vanilla's attack units, which
     * {@code LivingEntity.knockback} takes halved (a sprint hit adds 0.5, Knockback I another 0.5). 
     */
    public static final double SHOVE = 0.6;
    public static final int COOLDOWN_TICKS = 20;

    private BowBash() {}

    /** After a melee hit lands, on the server. */
    public static void onHit(ItemStack weapon, LivingEntity target, LivingEntity attacker) {
        if (!(weapon.getItem() instanceof ProjectileWeaponItem) || !(attacker instanceof Player player)) return;
        if (!(player.level() instanceof ServerLevel level) || player.getCooldowns().isOnCooldown(weapon)) return;
        double yaw = Math.toRadians(player.getYRot());
        target.knockback(SHOVE, Math.sin(yaw), -Math.cos(yaw), player.damageSources().playerAttack(player), 1.0F);
        player.getCooldowns().addCooldown(weapon, COOLDOWN_TICKS);
        level.playSound(null, target.getX(), target.getY(0.5), target.getZ(), ModSounds.BOW_BASH.get(), SoundSource.PLAYERS, 0.9F, 1.0F);
    }
}
