package com.panda.fulldraw.mixin;

import com.panda.fulldraw.shot.BowBash;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Bow Bash: vanilla calls {@code hurtEnemy} on the server once a melee hit has landed. And a line under items whose use
 * Full Draw changes but doesn't own: vanilla's Fletching Table (it opens a menu now), the Rope (it extends a rope), and
 * vanilla's arrows (how often they come back from a kill).
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Inject(method = "addDetailsToTooltip", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/world/item/Item;appendHoverText(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/Item$TooltipContext;Lnet/minecraft/world/item/component/TooltipDisplay;Ljava/util/function/Consumer;Lnet/minecraft/world/item/TooltipFlag;)V"))
    private void fulldraw$useLine(net.minecraft.world.item.Item.TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display,
                                  @org.jetbrains.annotations.Nullable net.minecraft.world.entity.player.Player player, net.minecraft.world.item.TooltipFlag flag,
                                  java.util.function.Consumer<net.minecraft.network.chat.Component> lines, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        ItemStack self = (ItemStack) (Object) this;
        String key = self.is(net.minecraft.world.item.Items.FLETCHING_TABLE) ? "item.fulldraw.fletching_table.tooltip"
                : self.is(com.panda.fulldraw.registry.ModItems.ROPE.get()) ? "item.fulldraw.rope.tooltip"
                : self.is(com.panda.fulldraw.registry.ModItems.MARKSMANS_EYE.get()) ? "item.fulldraw.marksmans_eye.tooltip" : null;
        if (key != null) lines.accept(net.minecraft.network.chat.Component.translatable(key).withStyle(net.minecraft.ChatFormatting.GRAY));
        // any other arrow (vanilla's, another mod's) comes back from a kill at the plain arrow's chance, as Full Draw's say
        // of theirs; on a server without Full Draw nothing comes back, so nothing is said
        if (self.getItem() instanceof net.minecraft.world.item.ArrowItem && !(self.getItem() instanceof com.panda.fulldraw.item.FullDrawArrowItem)
                && com.panda.fulldraw.FullDrawConfig.knowsServerRecovery())
            lines.accept(com.panda.fulldraw.item.FullDrawArrowItem.recoveryLine(com.panda.fulldraw.logic.ArrowTraits.PLAIN_RECOVERY));
    }

    @Inject(method = "hurtEnemy", at = @At("HEAD"))
    private void fulldraw$bowBash(LivingEntity target, LivingEntity attacker, CallbackInfoReturnable<Boolean> cir) {
        BowBash.onHit((ItemStack) (Object) this, target, attacker);
    }
}
