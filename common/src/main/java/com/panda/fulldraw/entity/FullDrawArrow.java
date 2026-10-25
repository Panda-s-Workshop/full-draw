package com.panda.fulldraw.entity;

import com.panda.fulldraw.logic.ArrowTraits;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * An arrow with its own head. Its {@link ArrowTraits} change the hit: the arrow mixin puts them on the damage, the
 * armour step counts only their share of the armour, and the arrow adds their knockback here. A subclass adds what the
 * head does after a hit lands. It flies, sticks, is picked up and is fired from any bow, crossbow or dispenser as a
 * vanilla arrow is.
 */
public abstract class FullDrawArrow extends AbstractArrow {
    protected FullDrawArrow(EntityType<? extends FullDrawArrow> type, Level level) {
        super(type, level);
    }

    protected FullDrawArrow(EntityType<? extends FullDrawArrow> type, LivingEntity owner, Level level, ItemStack pickup, @Nullable ItemStack weapon) {
        super(type, owner, level, pickup, weapon);
    }

    protected FullDrawArrow(EntityType<? extends FullDrawArrow> type, double x, double y, double z, Level level, ItemStack pickup) {
        super(type, x, y, z, level, pickup, null);
    }

    public abstract ArrowTraits traits();

    /** The head of what struck, or null for anything but a Full Draw arrow. */
    @Nullable
    public static ArrowTraits traitsOf(@Nullable Entity direct) {
        return direct instanceof FullDrawArrow arrow ? arrow.traits() : null;
    }

    /**
     * A hit on a living target that its damage cooldown turned away (the hit was no harder than the one before it):
     * what the head does besides damage. Nothing, unless the head says otherwise.
     */
    public void onTurnedAway(LivingEntity target, DamageSource source) {}

    /** The head's own shove on top of the weapon's, the way vanilla pushes for Punch. */
    @Override
    protected void doKnockback(LivingEntity target, DamageSource source) {
        super.doKnockback(target, source);
        float extra = traits().knockback();
        if (extra <= 0) return;
        double resist = Math.max(0.0, 1.0 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        Vec3 push = getDeltaMovement().multiply(1.0, 0.0, 1.0).normalize().scale(extra * 0.6 * resist);
        if (push.lengthSqr() > 0.0) target.push(push.x, 0.1, push.z);
    }
}
