package com.panda.fulldraw.quiver;

import com.panda.fulldraw.FullDrawMod;
import com.panda.fulldraw.item.QuiverItem;
import com.panda.fulldraw.registry.ModComponents;
import com.panda.fulldraw.registry.ModSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.jetbrains.annotations.Nullable;

/**
 * The Quiver's game side: finding it, handing its selected arrows to a bow, writing back what the bow used,
 * and the cycle key's server handler.
 */
public final class Quivers {
    /** The quiver stack a bow is drawing from on this thread, and the copy of its arrows the bow was handed. */
    private record Pending(Player player, ItemStack quiver, ItemStack ammo) {}

    private static final ThreadLocal<Pending> PENDING = new ThreadLocal<>();

    /** What a Quiver takes (data: defaults to every arrow). */
    public static final TagKey<net.minecraft.world.item.Item> AMMO = TagKey.create(Registries.ITEM, FullDrawMod.id("quiver_ammo"));

    private Quivers() {}

    public static QuiverContents contents(ItemStack quiver) {
        return quiver.getOrDefault(ModComponents.QUIVER_CONTENTS.get(), QuiverContents.EMPTY);
    }

    public static void set(ItemStack quiver, QuiverContents contents) {
        quiver.set(ModComponents.QUIVER_CONTENTS.get(), contents);
    }

    /** Arrows of any kind, vanilla's or another mod's: the {@link #AMMO} tag. */
    public static boolean accepts(ItemStack stack) {
        return stack.is(AMMO);
    }

    /** The first Quiver in the player's inventory, or EMPTY. Only one quiver counts at a time. */
    public static ItemStack find(Player player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof QuiverItem) return stack;
        }
        return ItemStack.EMPTY;
    }

    /**
     * What a weapon should fire, or null to let vanilla decide. Arrows held in a hand still win, as in vanilla; then
     * the quiver's selected stack, if this weapon can fire it.
     */
    @Nullable
    public static ItemStack projectileFor(Player player, ItemStack weapon) {
        if (!(weapon.getItem() instanceof ProjectileWeaponItem item)) return null;
        if (!ProjectileWeaponItem.getHeldProjectile(player, item.getSupportedHeldProjectiles()).isEmpty()) return null;
        ItemStack quiver = find(player);
        if (quiver.isEmpty()) return null;
        ItemStack arrows = contents(quiver).selectedStack();
        if (arrows.isEmpty() || !item.getAllSupportedProjectiles().test(arrows)) return null;
        // the server writes back what the bow used; the client only asks whether there is ammo, and keeps nothing
        if (!player.level().isClientSide()) PENDING.set(new Pending(player, quiver, arrows));
        return arrows;
    }

    /** After a weapon drew its ammo: if it came from the quiver, store what's left of the stack back in it. */
    public static void afterDraw(LivingEntity shooter, ItemStack ammo) {
        Pending pending = PENDING.get();
        if (pending == null) return;
        PENDING.remove();
        if (pending.player() != shooter || pending.ammo() != ammo) return;
        set(pending.quiver(), contents(pending.quiver()).withSelected(ammo));
    }

    /** Drops a draw that never happened (a release too short to fire, a use check): called after every bow release. */
    public static void clearPending() {
        PENDING.remove();
    }

    /** Game time of each player's last cycle: a client spamming the key can't flood its neighbours with sound. */
    private static final java.util.Map<ServerPlayer, Long> LAST_CYCLE = new java.util.WeakHashMap<>();

    /** The cycle key's server side: the next kind of arrow in the player's quiver. */
    public static void cycle(ServerPlayer player, int direction) {
        long now = player.level().getGameTime();
        Long last = LAST_CYCLE.get(player);
        if (last != null && now - last >= 0 && now - last < 2) return;
        LAST_CYCLE.put(player, now);
        ItemStack quiver = find(player);
        if (quiver.isEmpty()) return;
        QuiverContents contents = contents(quiver);
        QuiverContents next = contents.cycle(direction);
        if (next == contents) return;
        set(quiver, next);
        ItemStack arrows = next.selectedStack();
        player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.fulldraw.quiver_selected", arrows.getHoverName(), arrows.getCount()));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.QUIVER_CYCLE.get(), SoundSource.PLAYERS, 0.5F, 1.25F);
    }
}
