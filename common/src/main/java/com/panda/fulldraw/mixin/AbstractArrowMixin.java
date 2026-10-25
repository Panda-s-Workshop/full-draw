package com.panda.fulldraw.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.panda.fulldraw.FullDrawConfig;
import com.panda.fulldraw.entity.FullDrawArrow;
import com.panda.fulldraw.entity.GaleHarrier;
import com.panda.fulldraw.entity.StuckArrows;
import com.panda.fulldraw.item.FullDrawCrossbowItem;
import com.panda.fulldraw.logic.ArrowTraits;
import com.panda.fulldraw.logic.BoltTraits;
import com.panda.fulldraw.logic.ShotMath;
import com.panda.fulldraw.shot.ShotHolder;
import com.panda.fulldraw.shot.ShotRecord;
import com.panda.fulldraw.shot.Shots;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives every arrow a {@link ShotRecord} slot (saved with it), applies the weapon's gravity, and replaces a player
 * arrow's damage with the shot pipeline's at impact. Arrows without a record (mobs', dispensers') keep vanilla's damage,
 * times their head's if they have one.
 */
@Mixin(AbstractArrow.class)
public abstract class AbstractArrowMixin implements ShotHolder {
    @Shadow private double baseDamage;

    @Shadow public abstract boolean isCritArrow();

    @Shadow protected abstract boolean canHitEntity(Entity entity);

    @Unique @Nullable private ShotRecord fulldraw$shot;
    @Unique @Nullable private Shots.Impact fulldraw$impact;
    @Unique @Nullable private Entity fulldraw$target;
    /** On a client: the gravity multiplier the server sent (the record never reaches the client). */
    @Unique private float fulldraw$clientGravity = 1.0F;
    @Unique private boolean fulldraw$gravitySent;

    @Override
    public @Nullable ShotRecord fulldraw$shot() {
        return fulldraw$shot;
    }

    @Override
    public void fulldraw$setShot(@Nullable ShotRecord shot) {
        fulldraw$shot = shot;
    }

    @Override
    public void fulldraw$setClientGravity(float mult) {
        fulldraw$clientGravity = mult;
    }

    /**
     * An arrow with its own gravity tells the clients near it on its first tick, after its spawn packet has gone out:
     * clients run the arrow's flight themselves between the server's updates, so without it they'd draw it falling at
     * vanilla's rate and snap it back up.
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void fulldraw$sendGravity(CallbackInfo ci) {
        if (fulldraw$gravitySent || fulldraw$shot == null || fulldraw$shot.gravityMult() == 1.0) return;
        AbstractArrow self = (AbstractArrow) (Object) this;
        if (!(self.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        fulldraw$gravitySent = true;
        var payload = new com.panda.fulldraw.network.FullDrawNetwork.ArrowGravity(self.getId(), (float) fulldraw$shot.gravityMult());
        for (net.minecraft.server.level.ServerPlayer player : level.players())
            if (player.distanceToSqr(self) < 96 * 96) com.panda.fulldraw.network.FullDrawNetwork.send(player, payload);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void fulldraw$save(ValueOutput output, CallbackInfo ci) {
        output.storeNullable("fulldraw_shot", ShotRecord.CODEC, fulldraw$shot);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void fulldraw$load(ValueInput input, CallbackInfo ci) {
        fulldraw$shot = input.read("fulldraw_shot", ShotRecord.CODEC).orElse(null);
    }

    @Inject(method = "getDefaultGravity", at = @At("RETURN"), cancellable = true)
    private void fulldraw$gravity(CallbackInfoReturnable<Double> cir) {
        double mult = fulldraw$shot != null ? fulldraw$shot.gravityMult() : fulldraw$clientGravity;
        if (mult != 1.0) cir.setReturnValue(cir.getReturnValueD() * mult);
    }

    /** A player's crit trail starts on the arrow's third tick: drawn from the eye, its first motes cover the target. */
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private void fulldraw$lateTrail(Level level, ParticleOptions particle, double x, double y, double z, double dx, double dy, double dz,
                                    Operation<Void> original) {
        AbstractArrow self = (AbstractArrow) (Object) this;
        boolean players = self.getOwner() instanceof net.minecraft.world.entity.player.Player;
        // the Perfect trail is vanilla's crit trail: a client toggle hides it on player arrows
        if (players && particle == net.minecraft.core.particles.ParticleTypes.CRIT && !com.panda.fulldraw.FullDrawClientConfig.get().perfectTrail()) return;
        if (self.tickCount >= 3 || !players) original.call(level, particle, x, y, z, dx, dy, dz);
    }

    /** A heavy crossbow's bolt adds its own knockback to the weapon's (Punch on a bow; nothing on a crossbow). */
    @WrapOperation(method = "doKnockback", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;modifyKnockback(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;F)F"))
    private float fulldraw$boltKnockback(ServerLevel level, net.minecraft.world.item.ItemStack weapon, Entity target, DamageSource source, float base,
                                         Operation<Float> original) {
        float knockback = original.call(level, weapon, target, source, base);
        BoltTraits bolts = FullDrawCrossbowItem.boltsOf(weapon);
        return bolts == null ? knockback : knockback + bolts.knockback();
    }

    /**
     * The arrow about to strike a living creature, remembered on it as vanilla will count it there if the hit lands (a
     * server-side, non-piercing hit). Kept before the hit, so the arrow that kills is in the list when the death drops
     * run inside it; the caller forgets it again when the hit is turned away. Only an arrow its shooter paid for (one
     * that can be picked up) has anything to give back: mobs' and Creative players' arrows, Infinity's and Multishot's
     * copies, and the heads spent on impact hold their place in the count with nothing. Null when vanilla won't count it.
     */
    @Unique
    @Nullable
    private StuckArrows.Stuck fulldraw$keep(Entity target) {
        AbstractArrow self = (AbstractArrow) (Object) this;
        if (!(target instanceof StuckArrows stuck) || !(self.level() instanceof ServerLevel) || self.getPierceLevel() > 0) return null;
        StuckArrows.Stuck kept;
        if (self.pickup == AbstractArrow.Pickup.ALLOWED) {
            ArrowTraits head = FullDrawArrow.traitsOf(self);
            kept = new StuckArrows.Stuck(self.getPickupItemStackOrigin().copyWithCount(1), head == null ? ArrowTraits.PLAIN_RECOVERY : head.recovery());
        } else kept = new StuckArrows.Stuck(net.minecraft.world.item.ItemStack.EMPTY, 0.0);
        stuck.fulldraw$stick(kept);
        return kept;
    }

    /** An arrow a player can pick up stays in its block for the config's lifetime; all others keep vanilla's minute. */
    @ModifyExpressionValue(method = "tickDespawn", at = @At(value = "CONSTANT", args = "intValue=1200"))
    private int fulldraw$pickupLifetime(int vanilla) {
        AbstractArrow self = (AbstractArrow) (Object) this;
        return self.pickup == AbstractArrow.Pickup.ALLOWED && self.typeHolder().is(net.minecraft.tags.EntityTypeTags.ARROWS)
                ? FullDrawConfig.arrowPickupLifetimeSeconds() * 20 : vanilla;
    }

    /**
     * A diving Gale Harrier and an arrow coming at it head on close by more than four blocks a tick, so the harrier's
     * move can carry its box over the arrow between two ticks, and a box the arrow starts inside is passed over: such a
     * harrier is hit where the arrow went into its box, as it would have been a moment sooner. Only a diving harrier the arrow is
     * closing on: any other box an arrow starts inside is passed over, as in vanilla.
     */
    @ModifyReturnValue(method = "findHitEntities", at = @At("RETURN"))
    private Collection<EntityHitResult> fulldraw$harrierOverTheArrow(Collection<EntityHitResult> hits, Vec3 from, Vec3 to) {
        AbstractArrow self = (AbstractArrow) (Object) this;
        List<GaleHarrier> over = self.level().getEntitiesOfClass(GaleHarrier.class, AABB.ofSize(from, 1.0E-3, 1.0E-3, 1.0E-3),
                h -> h.phase() == GaleHarrier.Phase.DIVE && self.getDeltaMovement().dot(h.getDeltaMovement()) < 0.0
                        && h.getBoundingBox().contains(from) && canHitEntity(h) && hits.stream().noneMatch(hit -> hit.getEntity() == h));
        if (over.isEmpty()) return hits;
        List<EntityHitResult> all = new ArrayList<>(hits);
        for (GaleHarrier harrier : over) {
            // back along the two's closing motion to where the arrow met the box: its leading face
            Vec3 closing = self.getDeltaMovement().subtract(harrier.getDeltaMovement());
            all.add(new EntityHitResult(harrier, harrier.getBoundingBox().clip(from.subtract(closing), from).orElse(from)));
        }
        return all;
    }

    @Inject(method = "onHitEntity", at = @At("HEAD"))
    private void fulldraw$measureImpact(EntityHitResult hit, CallbackInfo ci) {
        fulldraw$impact = fulldraw$shot == null ? null : Shots.impact(fulldraw$shot, hit.getEntity(), hit.getLocation(), ((AbstractArrow) (Object) this).getDeltaMovement());
        fulldraw$target = hit.getEntity();
    }

    /**
     * Replaces a player arrow's damage with the pipeline's, and tells the shooter only about hits that landed: a raised
     * shield, an enderman's dodge or invulnerability make {@code hurtOrSimulate} return false, and vanilla then deflects
     * the arrow, which loses its record so the rebound isn't scored as the original shot.
     */
    @WrapOperation(method = "onHitEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean fulldraw$hurt(Entity target, DamageSource source, float vanillaAmount, Operation<Boolean> original) {
        ShotRecord shot = fulldraw$shot;
        Shots.Impact impact = fulldraw$impact;
        fulldraw$vanillaAmount = vanillaAmount;
        ShotMath.Breakdown breakdown = fulldraw$damage(source);
        // the arrow's head: its share of the pipeline's damage, or of vanilla's for an arrow without a shot, against the
        // armour that counts (a heavy crossbow's bolt already goes through part of it)
        ArrowTraits head = FullDrawArrow.traitsOf((AbstractArrow) (Object) this);
        if (head != null) {
            BoltTraits weaponBolts = FullDrawCrossbowItem.boltsOf(((AbstractArrow) (Object) this).getWeaponItem());
            double armour = (target instanceof net.minecraft.world.entity.LivingEntity living ? living.getArmorValue() : 0.0)
                    * (weaponBolts == null ? 1.0 : weaponBolts.armourKept());
            if (breakdown != null) breakdown = breakdown.withArrowMult(head.multFor(breakdown.total(), armour));
            else vanillaAmount = (float) (vanillaAmount * head.multFor(vanillaAmount, armour));
        }
        float damage = breakdown == null ? vanillaAmount : (float) breakdown.total();
        net.minecraft.world.item.ItemStack cracked = fulldraw$crackableShield(target, source, damage);
        StuckArrows.Stuck kept = fulldraw$keep(target);
        boolean hurt = original.call(target, source, damage);
        if (!hurt && kept != null) ((StuckArrows) target).fulldraw$stuck().removeIf(s -> s == kept);
        // a hit the damage cooldown turned away (not a shield, not invulnerability) still lands what a head does besides damage
        if (!hurt && (Object) this instanceof FullDrawArrow arrow && target instanceof net.minecraft.world.entity.LivingEntity living
                && living.damageCooldownTime > 10 && living.getItemBlockingWith() == null && target.level() instanceof ServerLevel level
                && !living.isInvulnerableTo(level, source))
            arrow.onTurnedAway(living, source);
        // a heavy bolt's own sound on every creature it goes into, under vanilla's hit sound
        BoltTraits bolts = FullDrawCrossbowItem.boltsOf(((AbstractArrow) (Object) this).getWeaponItem());
        if (hurt && bolts != null && bolts.pierce() > 0 && target instanceof net.minecraft.world.entity.LivingEntity)
            FullDrawCrossbowItem.boltSound((AbstractArrow) (Object) this, target, com.panda.fulldraw.registry.ModSounds.BOLT_PIERCE.get(), 0.8F);
        // not when the hit was cancelled (an invulnerable or creative target)
        if (cracked != null && target instanceof net.minecraft.world.entity.LivingEntity living && target.level() instanceof ServerLevel level
                && !living.isInvulnerableTo(level, source))
            cracked.get(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS).disable(level, living, BoltTraits.SHIELD_CRACK_SECONDS, cracked);
        fulldraw$target = null;
        if (shot != null && impact != null && breakdown != null && target.level() instanceof ServerLevel) {
            if (hurt) {
                Shots.reportHit(((AbstractArrow) (Object) this).getOwner(), shot, impact, breakdown);
                // a Perfect Release or a headshot brings a diving Gale Harrier down
                if (target instanceof GaleHarrier harrier) harrier.struckByShot(shot.perfect(), impact.precision());
            } else fulldraw$shot = null;
        }
        return hurt;
    }

    /**
     * The raised shield a shield-cracking weapon's bolt is about to knock down (as an axe does), or null. Only a shield
     * that faces the bolt, by vanilla's own blocking angle: a bolt in the back leaves it up. Read before the hit, since
     * a piercing bolt goes through the shield and the hit can lower it.
     */
    @Unique
    @Nullable
    private net.minecraft.world.item.ItemStack fulldraw$crackableShield(Entity target, DamageSource source, float damage) {
        AbstractArrow self = (AbstractArrow) (Object) this;
        net.minecraft.world.item.ItemStack weapon = self.getWeaponItem();
        if (weapon == null || !weapon.is(com.panda.fulldraw.shot.Weapons.SHIELD_PIERCING)
                || !(target instanceof net.minecraft.world.entity.LivingEntity living)) return null;
        net.minecraft.world.item.ItemStack shield = living.getItemBlockingWith();
        var blocks = shield == null ? null : shield.get(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS);
        if (blocks == null || blocks.bypassedBy().map(types -> types.contains(source.typeHolder())).orElse(false)) return null;
        // vanilla's angle (LivingEntity.applyItemBlocking): between the target's facing and the source, flat
        net.minecraft.world.phys.Vec3 from = source.getSourcePosition();
        if (from == null) return null;
        net.minecraft.world.phys.Vec3 look = Entity.calculateViewVector(0.0F, living.getYHeadRot());
        net.minecraft.world.phys.Vec3 to = from.subtract(living.position());
        double angle = Math.acos(new net.minecraft.world.phys.Vec3(to.x, 0.0, to.z).normalize().dot(look));
        return blocks.resolveBlockedDamage(source, damage, angle) > 0 ? shield : null;
    }

    /** What vanilla would deal for the hit in progress (its crit bonus included). */
    @Unique
    private float fulldraw$vanillaAmount;

    @Unique
    @Nullable
    private ShotMath.Breakdown fulldraw$damage(DamageSource source) {
        AbstractArrow self = (AbstractArrow) (Object) this;
        if (fulldraw$shot == null || fulldraw$impact == null || !(self.level() instanceof ServerLevel level)) return null;
        // vanilla's damage without its random crit bonus: ceil(speed × base damage after enchantments)
        float base = (float) baseDamage;
        // as vanilla: only arrows fired from a weapon get its enchantments' damage (commands and tests make arrows without one)
        if (fulldraw$target != null && self.getWeaponItem() != null)
            base = EnchantmentHelper.modifyDamage(level, self.getWeaponItem(), fulldraw$target, source, base);
        int noCrit = ShotMath.vanillaDamage(self.getDeltaMovement().length(), base);
        return Shots.damage(fulldraw$shot, fulldraw$impact, noCrit, fulldraw$vanillaAmount, isCritArrow());
    }
}
