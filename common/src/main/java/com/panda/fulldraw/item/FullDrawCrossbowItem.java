package com.panda.fulldraw.item;

import com.panda.fulldraw.FullDrawConfig;
import com.panda.fulldraw.logic.BoltTraits;
import com.panda.fulldraw.logic.WeaponStats;
import com.panda.fulldraw.shot.Weapons;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * A Full Draw crossbow. It is a vanilla crossbow with its own load time (set where vanilla reads it, in
 * {@code CrossbowItem.getChargeDuration}), launch speed, spread and damage, and bolts that carry {@link BoltTraits}.
 * Vanilla's loading, ammo and multishot all still run, and its loading sounds unless the crossbow has its own crank.
 * A bolt that pierces or bites through armour is heard where it hits. A player's bolt doesn't get vanilla's random crit bonus
 * (as a full-drawn bow's arrow doesn't, unless {@code vanillaCritOnFullDraw} is on). In {@code fulldraw:no_shot_pipeline}
 * it is a plain vanilla crossbow.
 */
public class FullDrawCrossbowItem extends CrossbowItem implements Weapons.Ranged {
    private final WeaponStats stats;
    private final BoltTraits bolts;
    @Nullable
    private final Supplier<ChargingSounds> crank;
    @Nullable
    private ChargingSounds crankSounds;

    public FullDrawCrossbowItem(Properties properties, WeaponStats stats, BoltTraits bolts) {
        this(properties, stats, bolts, null);
    }

    /** @param crank its own loading sounds in place of vanilla's (a Quick Charge crossbow still plays Quick Charge's) */
    public FullDrawCrossbowItem(Properties properties, WeaponStats stats, BoltTraits bolts, @Nullable Supplier<ChargingSounds> crank) {
        super(properties);
        this.stats = stats;
        this.bolts = bolts;
        this.crank = crank;
    }

    /** Its own loading sounds, or null for vanilla's. */
    @Nullable
    public ChargingSounds crankSounds() {
        // built on first use: the sound holders aren't bound when the item is constructed
        if (crankSounds == null && crank != null) crankSounds = crank.get();
        return crankSounds;
    }

    /**
     * A heavy bolt's sound where it struck, for anyone near, under its shooter's sound category. A shooting player too
     * far away to hear it there gets it too, as it is how they learn what the bolt did.
     */
    public static void boltSound(AbstractArrow bolt, net.minecraft.world.entity.Entity target, net.minecraft.sounds.SoundEvent sound, float volume) {
        if (!(target.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        net.minecraft.world.entity.Entity owner = bolt.getOwner();
        net.minecraft.sounds.SoundSource category = owner != null ? owner.getSoundSource() : net.minecraft.sounds.SoundSource.NEUTRAL;
        long seed = level.getRandom().nextLong();
        level.playSeededSound(null, target.getX(), target.getY(), target.getZ(), net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), category, volume, 1.0F, seed);
        // a sound is heard up to 16 blocks away (further only when louder than 1)
        if (owner instanceof net.minecraft.server.level.ServerPlayer player && player.level() == level && player.distanceToSqr(target) > 16 * 16)
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), category,
                    player.getX(), player.getY(), player.getZ(), volume, 1.0F, seed));
    }

    @Override
    public WeaponStats stats() {
        return stats;
    }

    /** The bolt traits of the weapon an arrow was fired from, or null when it wasn't a Full Draw crossbow (or is opted out). */
    @Nullable
    public static BoltTraits boltsOf(@Nullable ItemStack weapon) {
        return weapon != null && weapon.getItem() instanceof FullDrawCrossbowItem crossbow && Weapons.inPipeline(weapon) ? crossbow.bolts : null;
    }

    /** Vanilla passes 3.15 for a bolt (1.6 for a rocket) and an inaccuracy of 1.0; both scale by the weapon's numbers. */
    @Override
    public void performShooting(Level level, LivingEntity shooter, InteractionHand hand, ItemStack weapon, float velocity, float inaccuracy,
                                @Nullable LivingEntity target) {
        if (!Weapons.inPipeline(weapon)) super.performShooting(level, shooter, hand, weapon, velocity, inaccuracy, target);
        else super.performShooting(level, shooter, hand, weapon, (float) (velocity * stats.velocityMult()), (float) (inaccuracy * stats.spread()), target);
    }

    @Override
    protected Projectile createProjectile(Level level, LivingEntity shooter, ItemStack weapon, ItemStack ammo, boolean critical) {
        if (!Weapons.inPipeline(weapon)) return super.createProjectile(level, shooter, weapon, ammo, critical);
        // vanilla passes critical = true for players
        Projectile projectile = super.createProjectile(level, shooter, weapon, ammo, critical && FullDrawConfig.vanillaCritOnFullDraw());
        if (projectile instanceof AbstractArrow arrow) {
            arrow.setBaseDamage(arrow.baseDamage * stats.damageMult());
            arrow.setPierceLevel((byte) Math.min(Byte.MAX_VALUE, arrow.getPierceLevel() + bolts.pierce()));
        }
        return projectile;
    }

    /**
     * How it differs from vanilla's crossbow, which vanilla's tooltip can't say: its load, its bolts' weight and what
     * they do, that a bolt takes no head or range bonus, and the Bow Bash.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);
        if (!Weapons.inPipeline(stack)) return;
        WeaponStats stats = Weapons.stats(stack);
        lines.accept(Component.translatable("tooltip.fulldraw.crossbow.load", FullDrawBowItem.number(stats.drawTicks() / 20.0)).withStyle(ChatFormatting.GRAY));
        if (stats.damageMult() != 1.0)
            lines.accept(Component.translatable("tooltip.fulldraw.crossbow.damage", FullDrawBowItem.number(stats.damageMult())).withStyle(ChatFormatting.GRAY));
        var use = stack.get(net.minecraft.core.component.DataComponents.USE_EFFECTS);
        if (use != null && use.speedMultiplier() <= 0.0F) lines.accept(Component.translatable("tooltip.fulldraw.crossbow.rooted").withStyle(ChatFormatting.GRAY));
        else if (use != null && use.speedMultiplier() >= 1.0F) lines.accept(Component.translatable("tooltip.fulldraw.crossbow.walk").withStyle(ChatFormatting.GRAY));
        if (bolts.pierce() > 0) lines.accept(Component.translatable("tooltip.fulldraw.bolts.pierce", bolts.pierce() + 1).withStyle(ChatFormatting.GRAY));
        if (bolts.armourKept() < 1.0)
            lines.accept(Component.translatable("tooltip.fulldraw.bolts.armour", Math.round((1.0 - bolts.armourKept()) * 100)).withStyle(ChatFormatting.GRAY));
        if (stack.is(Weapons.SHIELD_PIERCING))
            lines.accept(Component.translatable("tooltip.fulldraw.bolts.shield", Math.round(BoltTraits.SHIELD_CRACK_SECONDS)).withStyle(ChatFormatting.GRAY));
        if (bolts.knockback() > 0) lines.accept(Component.translatable("tooltip.fulldraw.bolts.knockback").withStyle(ChatFormatting.GRAY));
        lines.accept(Component.translatable("tooltip.fulldraw.crossbow.no_bonus").withStyle(ChatFormatting.GRAY));
        moreLines(stack, lines);
        lines.accept(FullDrawBowItem.bashLine());
    }

    /** A kind's own lines, between the bolts' and the Bow Bash's. */
    protected void moreLines(ItemStack stack, Consumer<Component> lines) {}
}
