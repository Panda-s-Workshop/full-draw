package com.panda.fulldraw.shot;

import com.panda.fulldraw.FullDrawConfig;
import com.panda.fulldraw.logic.ShotMath;
import com.panda.fulldraw.logic.WeakSpot;
import com.panda.fulldraw.logic.WeaponStats;
import com.panda.fulldraw.network.FullDrawNetwork;
import com.panda.fulldraw.registry.ModSounds;
import com.panda.fulldraw.registry.ModTriggers;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The shot pipeline's game side. The bow mixins open a {@link Release} while a bow is being
 * released, each arrow it fires gets a {@link ShotRecord}, and the arrow mixin asks {@link #damage} at impact.
 */
public final class Shots {
    /** The bow release in progress on this thread: who released which weapon after how many ticks. */
    public record Release(LivingEntity shooter, ItemStack weapon, WeaponStats stats, int ticksHeld, long gameTime) {}

    private static final ThreadLocal<Release> RELEASE = new ThreadLocal<>();
    /** Whether this release was already announced (chime, verdict): a multishot weapon fires several arrows from one. */
    private static final ThreadLocal<Boolean> ANNOUNCED = new ThreadLocal<>();

    private Shots() {}

    /**
     * Starts a release. One already open for the same shooter, weapon and tick is kept (a Full Draw bow opens its own
     * before calling vanilla's); anything else is stale, left by a release that threw before it could close, and goes.
     */
    public static void beginRelease(LivingEntity shooter, ItemStack weapon, int ticksHeld) {
        // a weapon packs have opted out (fulldraw:no_shot_pipeline) fires as vanilla's
        if (!Weapons.inPipeline(weapon)) return;
        Release open = RELEASE.get();
        long now = shooter.level().getGameTime();
        if (open != null && open.shooter() == shooter && open.weapon() == weapon && open.gameTime() == now) return;
        ANNOUNCED.remove();
        RELEASE.set(new Release(shooter, weapon, Weapons.stats(weapon), ticksHeld, now));
    }

    public static void endRelease() {
        RELEASE.remove();
        ANNOUNCED.remove();
    }

    /** The shot a player's projectile is about to be fired with, or null when the pipeline leaves it alone. */
    @Nullable
    public static ShotRecord plan(LivingEntity shooter, Projectile projectile) {
        Release release = RELEASE.get();
        if (release == null || release.shooter() != shooter || !(shooter instanceof Player)) return null;
        if (!(projectile instanceof AbstractArrow arrow) || !(arrow instanceof ShotHolder holder)) return null;
        if (holder.fulldraw$shot() != null) return holder.fulldraw$shot();
        ShotMath.Tuning t = FullDrawConfig.tuning();
        WeaponStats stats = release.stats();
        boolean perfect = ShotMath.isPerfectOnServer(release.ticksHeld(), stats, t, 0);
        // Steady Aim: the breath as the server last ticked it
        com.panda.fulldraw.logic.Breath breath = Focus.of((Player) shooter);
        double spread = ShotMath.spread(stats, perfect, release.ticksHeld() - stats.drawTicks(), movementMult(shooter),
                breath.focusing(), breath.exhaustedTicks(), t);
        ShotRecord shot = new ShotRecord(perfect, shooter.getEyePosition(), stats.damageMult(), stats.gravityMult(), spread,
                BuiltInRegistries.ITEM.getKey(release.weapon().getItem()));
        holder.fulldraw$setShot(shot);
        // vanilla's random full-draw crit becomes the Perfect crit; its particle trail now marks Perfect arrows
        if (perfect) arrow.setCritArrow(true);
        else if (!FullDrawConfig.vanillaCritOnFullDraw()) arrow.setCritArrow(false);
        if (ANNOUNCED.get() == null) {
            ANNOUNCED.set(true);
            announce(shooter, release, stats, perfect, t);
        }
        return shot;
    }

    /**
     * Once per release: a Perfect chimes; the shooter hears the server's verdict on a full draw (perfect, early, late),
     * which the HUD shows under the ring, so the client never contradicts what the server decided.
     * A full draw that missed, by someone who has never hit a Perfect, also gets the hint on the action bar.
     */
    private static void announce(LivingEntity shooter, Release release, WeaponStats stats, boolean perfect, ShotMath.Tuning t) {
        if (perfect) perfectRelease(shooter);
        if (!(shooter instanceof ServerPlayer player) || release.ticksHeld() < stats.drawTicks()) return;
        int verdict = perfect ? FullDrawNetwork.ReleaseResult.PERFECT
                : release.ticksHeld() < stats.drawTicks() + t.perfectDelay() ? FullDrawNetwork.ReleaseResult.EARLY : FullDrawNetwork.ReleaseResult.LATE;
        FullDrawNetwork.send(player, new FullDrawNetwork.ReleaseResult(verdict));
        if (!perfect && !knowsPerfect(player))
            player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.fulldraw.perfect_hint"));
    }

    /**
     * A Perfect Release chimes for everyone nearby and flashes at the bow. The shooter's own client plays both when it
     * hears the verdict, so a player can switch them off for themselves; everyone else gets them from here.
     */
    private static void perfectRelease(LivingEntity shooter) {
        if (!(shooter.level() instanceof ServerLevel level)) return;
        Player self = shooter instanceof Player p ? p : null;
        level.playSound(self, shooter.getX(), shooter.getEyeY(), shooter.getZ(), ModSounds.PERFECT_RELEASE.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
        if (shooter instanceof ServerPlayer player) ModTriggers.EVENT.get().trigger(player, "perfect_release");
        Vec3 bow = flashPoint(shooter);
        for (ServerPlayer watcher : level.players())
            if (watcher != self) level.sendParticles(watcher, ParticleTypes.FIREWORK, false, false, bow.x, bow.y, bow.z, 2, 0.02, 0.02, 0.02, 0.01);
    }

    /**
     * Where the Perfect flash shows: where the bow is in first person, right of the crosshair and above the HUD, so it's
     * never on the line to the target, the hotbar or the action bar.
     */
    public static Vec3 flashPoint(Entity shooter) {
        double yaw = Math.toRadians(shooter.getYRot());
        return shooter.getEyePosition().add(shooter.getLookAngle().scale(1.6)).add(-Math.cos(yaw) * 0.55, -0.2, -Math.sin(yaw) * 0.55);
    }

    /** Whether the player has earned Perfect Release (the hint stops then). */
    private static boolean knowsPerfect(ServerPlayer player) {
        var advancement = player.level().getServer().getAdvancements().get(com.panda.fulldraw.FullDrawMod.id("perfect_release"));
        return advancement == null || player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    /** Tells the shooter how their arrow landed: the client plays the hit sounds and the readout. */
    public static void reportHit(Entity owner, ShotRecord shot, Impact impact, ShotMath.Breakdown breakdown) {
        float damage = (float) breakdown.total();
        if (owner instanceof Player p) LAST.put(p.getUUID(), new LastShot(shot.weapon(), impact.precision(), breakdown));
        if (impact.precision() && owner != null && owner.level() instanceof ServerLevel level) {
            // a small burst where the weak spot was struck, for the shooter and anyone watching
            Vec3 at = impact.location();
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 10, 0.05, 0.05, 0.05, 0.35);
        }
        if (!(owner instanceof ServerPlayer player)) return;
        FullDrawNetwork.send(player, new FullDrawNetwork.ShotResult(shot.perfect(), impact.precision(), (float) impact.distance(), damage));
        if (impact.precision()) ModTriggers.EVENT.get().trigger(player, "precision_hit", (int) impact.distance());
        ModTriggers.EVENT.get().trigger(player, "long_shot", (int) impact.distance());
    }

    /** The launch speed for a planned shot: vanilla's speed times the weapon's multiplier. */
    public static float velocity(LivingEntity shooter, Projectile projectile, float vanillaVelocity) {
        Release release = RELEASE.get();
        ShotRecord shot = plan(shooter, projectile);
        return shot == null || release == null ? vanillaVelocity : (float) (vanillaVelocity * release.stats().velocityMult());
    }

    /** The launch inaccuracy for a planned shot (vanilla passes 1.0). */
    public static float spread(LivingEntity shooter, Projectile projectile, float vanillaSpread) {
        ShotRecord shot = plan(shooter, projectile);
        return shot == null ? vanillaSpread : (float) (shot.spread() * vanillaSpread);
    }

    /** Sprinting or airborne ×2, standing still or sneaking ×0.5. */
    static double movementMult(LivingEntity shooter) {
        double dx = shooter.getX() - shooter.xo, dz = shooter.getZ() - shooter.zo;
        return ShotMath.movementMult(shooter.isSprinting(), !shooter.onGround(), shooter.isShiftKeyDown(), dx * dx + dz * dz);
    }

    /** Where an arrow hit: whether it found a weak spot, and how far it flew. */
    public record Impact(boolean precision, double precisionMult, double distance, Vec3 location) {}

    /** Measures an impact against the target's weak spots and the shot's origin (3.5). */
    public static Impact impact(ShotRecord shot, Entity target, Vec3 location, Vec3 direction) {
        double distance = shot.origin().distanceTo(location);
        if (!(target instanceof LivingEntity living)) return new Impact(false, 1.0, distance, location);
        WeakSpot spot = WeakSpots.of(living);
        double dx = location.x - target.getX(), dy = location.y - target.getY(), dz = location.z - target.getZ();
        boolean hit = WeakSpot.onSilhouette(dx, dy, dz, direction.x, direction.z, target.getBbWidth(), target.getBbHeight())
                && spot.hit(dx, dy, dz, living.yBodyRot, target.getBbWidth(), target.getBbHeight());
        if (!hit) return new Impact(false, 1.0, distance, location);
        ShotMath.Tuning t = FullDrawConfig.tuning();
        double mult = spot.multiplier() > 0 ? spot.multiplier() : target instanceof Player ? t.precisionPlayers() : t.precisionMobs();
        return new Impact(true, mult, distance, location);
    }

    /**
     * The damage a player's arrow deals.
     *
     * @param vanillaNoCrit vanilla's damage without its random crit bonus
     * @param vanillaAmount what vanilla would deal, crit bonus included
     */
    public static ShotMath.Breakdown damage(ShotRecord shot, Impact impact, int vanillaNoCrit, float vanillaAmount, boolean critArrow) {
        int base = !shot.perfect() && critArrow && FullDrawConfig.vanillaCritOnFullDraw() ? (int) vanillaAmount : vanillaNoCrit;
        return ShotMath.breakdown(base, shot.weaponMult(), 1.0, shot.perfect(), impact.precisionMult(), impact.distance(),
                FullDrawConfig.tuning());
    }

    /** A player's last arrow that landed, for {@code /fulldraw stats}: which weapon, and every factor of its damage. */
    public record LastShot(net.minecraft.resources.Identifier weapon, boolean precision, ShotMath.Breakdown damage) {}

    /** By UUID, so a respawn (a new player object) keeps it; dropped when the player leaves. */
    private static final java.util.Map<java.util.UUID, LastShot> LAST = new java.util.concurrent.ConcurrentHashMap<>();

    @Nullable
    public static LastShot lastShot(Player player) {
        return LAST.get(player.getUUID());
    }

    /** When a player leaves the server. */
    public static void forget(Player player) {
        LAST.remove(player.getUUID());
    }
}
