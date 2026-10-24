package com.panda.fulldraw.item;

import com.panda.fulldraw.logic.BoltTraits;
import com.panda.fulldraw.logic.Magazine;
import com.panda.fulldraw.logic.WeaponStats;
import com.panda.fulldraw.registry.ModComponents;
import com.panda.fulldraw.registry.ModSounds;
import com.panda.fulldraw.shot.Weapons;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * A crossbow with a magazine. One crank loads {@link Magazine#shots()} shots (the crossbow mixin draws the extra ammo
 * where vanilla draws one, and records the shot size), each use fires the first of them, and the next can't follow for
 * {@link Magazine#shotTicks()}. A shot is what one draw of ammo gave: a bolt, or Multishot's three. A magazine can't be
 * topped up: a loaded crossbow fires, as vanilla's does.
 */
public class RepeatingCrossbowItem extends FullDrawCrossbowItem {
    private final Magazine magazine;

    public RepeatingCrossbowItem(Properties properties, WeaponStats stats, Magazine magazine) {
        super(properties, stats, BoltTraits.NONE);
        this.magazine = magazine;
    }

    /** The magazine of the weapon being loaded, or null when it has none (or is opted out). */
    @Nullable
    public static Magazine magazineOf(ItemStack weapon) {
        return weapon.getItem() instanceof RepeatingCrossbowItem repeater && Weapons.inPipeline(weapon) ? repeater.magazine : null;
    }

    /** Shots still in the magazine (the sprite's pips, the tooltip). */
    public static int shotsLoaded(ItemStack stack) {
        ChargedProjectiles loaded = stack.get(DataComponents.CHARGED_PROJECTILES);
        if (loaded == null || loaded.isEmpty()) return 0;
        int perShot = shotSize(stack);
        return (loaded.size() + perShot - 1) / perShot;
    }

    private static int shotSize(ItemStack stack) {
        return stack.getOrDefault(ModComponents.SHOT_SIZE.get(), 1);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean firing = isCharged(stack);
        InteractionResult result = super.use(level, player, hand);
        if (firing && Weapons.inPipeline(stack)) player.getCooldowns().addCooldown(stack, magazine.shotTicks());
        return result;
    }

    /** Fires the magazine's first shot and keeps the rest loaded. */
    @Override
    public void performShooting(Level level, LivingEntity shooter, InteractionHand hand, ItemStack weapon, float velocity, float inaccuracy,
                                @Nullable LivingEntity target) {
        ChargedProjectiles loaded = weapon.get(DataComponents.CHARGED_PROJECTILES);
        if (!(level instanceof ServerLevel server) || loaded == null || loaded.isEmpty() || !Weapons.inPipeline(weapon)) {
            super.performShooting(level, shooter, hand, weapon, velocity, inaccuracy, target);
            return;
        }
        List<ItemStack> all = loaded.itemCopies().toList();
        int size = Math.min(all.size(), shotSize(weapon));
        List<ItemStack> shot = all.subList(0, size), rest = all.subList(size, all.size());
        weapon.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(shot));
        // vanilla chose the power for the whole magazine (1.6 if it holds a rocket); a shot of the other kind gets its own
        boolean rocketShot = shot.stream().anyMatch(s -> s.is(Items.FIREWORK_ROCKET));
        float power = rocketShot == loaded.contains(Items.FIREWORK_ROCKET) ? velocity : rocketShot ? 1.6F : 3.15F;
        super.performShooting(level, shooter, hand, weapon, power, inaccuracy, target);
        // the magazine's action on top of vanilla's shot, lower on the last one: it's empty
        level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), ModSounds.REPEATER_CLICK.get(), shooter.getSoundSource(), 0.7F,
                rest.isEmpty() ? 0.7F : 1.0F);
        if (rest.isEmpty()) return;
        // vanilla emptied it; put back what's left, or drop the real ammo if the shot broke the crossbow
        if (!weapon.isEmpty()) weapon.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(rest));
        else for (ItemStack ammo : rest) if (!ammo.has(DataComponents.INTANGIBLE_PROJECTILE)) shooter.spawnAtLocation(server, ammo);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);
        if (!Weapons.inPipeline(stack)) return;
        lines.accept(Component.translatable("tooltip.fulldraw.magazine", magazine.shots()).withStyle(ChatFormatting.GRAY));
        int loaded = shotsLoaded(stack);
        if (loaded > 0) lines.accept(Component.translatable("tooltip.fulldraw.magazine.loaded", loaded, magazine.shots()).withStyle(ChatFormatting.GRAY));
    }
}
