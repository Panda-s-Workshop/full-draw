package com.panda.fulldraw.item;

import com.panda.fulldraw.logic.WeaponStats;
import com.panda.fulldraw.shot.Shots;
import com.panda.fulldraw.shot.Weapons;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
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
        if (!startsWhileSprinting && player.isSprinting()) {
            // said on the action bar, or the bow just seems not to work
            if (!level.isClientSide()) player.sendOverlayMessage(Component.translatable("message.fulldraw.no_draw_sprinting", player.getItemInHand(hand).getHoverName()));
            return InteractionResult.FAIL;
        }
        return super.use(level, player, hand);
    }

    /** How it differs from vanilla's bow: its draw, its hit, its spread and arc, its Perfect window, and the run. */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);
        if (!Weapons.inPipeline(stack)) return;
        WeaponStats vanilla = WeaponStats.VANILLA_BOW, stats = Weapons.stats(stack);
        lines.accept(gray("tooltip.fulldraw.bow.draw", number(stats.drawTicks() / 20.0)));
        if (stats.damageMult() != vanilla.damageMult()) lines.accept(gray("tooltip.fulldraw.bow.damage", number(stats.damageMult())));
        if (stats.spread() < vanilla.spread()) lines.accept(gray("tooltip.fulldraw.bow.tight"));
        else if (stats.spread() > vanilla.spread()) lines.accept(gray("tooltip.fulldraw.bow.wide"));
        if (stats.gravityMult() < vanilla.gravityMult()) lines.accept(gray("tooltip.fulldraw.bow.flat"));
        if (stats.extraWindow() > 0) lines.accept(gray("tooltip.fulldraw.bow.window", stats.extraWindow()));
        if (!startsWhileSprinting) lines.accept(gray("tooltip.fulldraw.bow.no_sprint"));
    }

    /** Up to two decimals, with no trailing zeros: 0.6, 1.15, 1. */
    private static String number(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value).replaceAll("\\.?0+$", "");
    }

    private static Component gray(String key, Object... args) {
        return Component.translatable(key, args).withStyle(ChatFormatting.GRAY);
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
            return super.releaseUsing(stack, level, entity, duration - Weapons.stats(stack).toVanillaTicks(held));
        } finally {
            Shots.endRelease();
        }
    }
}
