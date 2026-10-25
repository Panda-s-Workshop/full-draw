package com.panda.fulldraw.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The item of a Full Draw arrow: it is ammo wherever a vanilla arrow is (the {@code minecraft:arrows} tag), shot as its
 * own entity from a bow, a crossbow or a dispenser. Its tooltip says what the head does, one lang line each.
 */
public class FullDrawArrowItem extends ArrowItem {
    /** The arrow a shooter fires. */
    public interface Fired {
        AbstractArrow create(Level level, LivingEntity shooter, ItemStack pickup, @Nullable ItemStack weapon);
    }

    /** The arrow a dispenser fires. */
    public interface Dispensed {
        AbstractArrow create(Level level, double x, double y, double z, ItemStack pickup);
    }

    private final Fired fired;
    private final Dispensed dispensed;
    private final com.panda.fulldraw.logic.ArrowTraits head;
    private final int tooltipLines;
    private final java.util.function.Supplier<Object[]> tooltipArgs;

    public FullDrawArrowItem(Properties properties, Fired fired, Dispensed dispensed, com.panda.fulldraw.logic.ArrowTraits head, int tooltipLines) {
        this(properties, fired, dispensed, head, tooltipLines, () -> new Object[0]);
    }

    /** With numbers for the tooltip's {@code %s}: what the server has set, read when the tooltip is drawn. */
    public FullDrawArrowItem(Properties properties, Fired fired, Dispensed dispensed, com.panda.fulldraw.logic.ArrowTraits head, int tooltipLines,
                             java.util.function.Supplier<Object[]> tooltipArgs) {
        super(properties);
        this.fired = fired;
        this.dispensed = dispensed;
        this.head = head;
        this.tooltipLines = tooltipLines;
        this.tooltipArgs = tooltipArgs;
    }

    @Override
    public AbstractArrow createArrow(Level level, ItemStack ammo, LivingEntity shooter, @Nullable ItemStack weapon) {
        return fired.create(level, shooter, ammo.copyWithCount(1), weapon);
    }

    @Override
    public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction direction) {
        AbstractArrow arrow = dispensed.create(level, pos.x(), pos.y(), pos.z(), stack.copyWithCount(1));
        arrow.pickup = AbstractArrow.Pickup.ALLOWED;
        return arrow;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);
        for (int i = 0; i < tooltipLines; i++)
            lines.accept(Component.translatable(getDescriptionId() + ".tooltip." + i, tooltipArgs.get()).withStyle(ChatFormatting.GRAY));
        lines.accept(recoveryLine(head.recovery()));
    }

    /**
     * How often an arrow with this head recovery comes back from a kill, at the server's plain-arrow chance: spent on
     * impact for a head that never stays in a creature, always, never, or the share.
     */
    public static Component recoveryLine(double recovery) {
        double chance = com.panda.fulldraw.logic.ArrowTraits.recoveryChance(recovery, com.panda.fulldraw.FullDrawConfig.clientArrowRecoveryChance());
        Component line = recovery <= 0 ? Component.translatable("tooltip.fulldraw.recovery.spent")
                : chance >= 1.0 ? Component.translatable("tooltip.fulldraw.recovery.always")
                : chance <= 0.0 ? Component.translatable("tooltip.fulldraw.recovery.never")
                : Component.translatable("tooltip.fulldraw.recovery.chance", Math.clamp(Math.round(chance * 100), 1, 99));
        return line.copy().withStyle(ChatFormatting.GRAY);
    }
}
