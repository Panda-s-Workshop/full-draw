package com.panda.fulldraw.item;

import com.panda.fulldraw.quiver.QuiverContents;
import com.panda.fulldraw.quiver.Quivers;
import com.panda.fulldraw.registry.ModSounds;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * The Quiver: up to four stacks of arrows, anywhere in the inventory. It fills like a bundle (right-click
 * arrows onto it, or it onto arrows; right-click it onto an empty slot to take the selected stack out), bows draw
 * from its selected stack, and the cycle key picks the next kind.
 */
public class QuiverItem extends Item {
    private static final int BAR_COLOUR = 0x8C6138;

    public QuiverItem(Properties properties) {
        super(properties);
    }

    /** The quiver is on the cursor and is right-clicked onto a slot. */
    @Override
    public boolean overrideStackedOnOther(ItemStack quiver, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player)) return false;
        QuiverContents contents = Quivers.contents(quiver);
        ItemStack target = slot.getItem();
        if (target.isEmpty()) {
            if (contents.isEmpty()) return false;
            ItemStack out = contents.selectedStack();
            int before = out.getCount();
            ItemStack rest = slot.safeInsert(out);
            if (rest.getCount() == before) return false; // the slot takes nothing (an output or armour slot)
            Quivers.set(quiver, rest.isEmpty() ? contents.removeSelected() : contents.withSelected(rest));
            player.playSound(ModSounds.QUIVER_REMOVE.get(), 0.8F, 1.0F);
            return true;
        }
        if (!Quivers.accepts(target)) return false;
        int n = Math.min(contents.roomFor(target), target.getCount());
        if (n <= 0) return true; // a full quiver refuses arrows, as a full bundle does, rather than swapping places
        ItemStack taken = slot.safeTake(target.getCount(), n, player);
        Quivers.set(quiver, contents.insert(taken, taken.getCount()));
        player.playSound(ModSounds.QUIVER_INSERT.get(), 0.8F, 1.0F);
        return true;
    }

    /** Something on the cursor is right-clicked onto the quiver. */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack quiver, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess cursor) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player)) return false;
        QuiverContents contents = Quivers.contents(quiver);
        if (other.isEmpty()) {
            if (contents.isEmpty()) return false;
            cursor.set(contents.selectedStack());
            Quivers.set(quiver, contents.removeSelected());
            player.playSound(ModSounds.QUIVER_REMOVE.get(), 0.8F, 1.0F);
            return true;
        }
        if (!Quivers.accepts(other)) return false;
        int n = Math.min(contents.roomFor(other), other.getCount());
        if (n <= 0) return true;
        Quivers.set(quiver, contents.insert(other, n));
        other.shrink(n);
        player.playSound(ModSounds.QUIVER_INSERT.get(), 0.8F, 1.0F);
        return true;
    }

    @Override
    public boolean isBarVisible(ItemStack quiver) {
        return !Quivers.contents(quiver).isEmpty();
    }

    @Override
    public int getBarWidth(ItemStack quiver) {
        return Math.min(13, Math.round(13.0F * Quivers.contents(quiver).total() / (QuiverContents.SLOTS * 64)));
    }

    @Override
    public int getBarColor(ItemStack quiver) {
        return BAR_COLOUR;
    }

    @Override
    public void appendHoverText(ItemStack quiver, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
        QuiverContents contents = Quivers.contents(quiver);
        if (contents.isEmpty()) {
            lines.accept(Component.translatable("tooltip.fulldraw.quiver.empty").withStyle(ChatFormatting.GRAY));
        }
        for (int i = 0; i < contents.stacks().size(); i++) {
            ItemStackTemplate t = contents.stacks().get(i);
            boolean selected = i == contents.selected();
            lines.accept(Component.translatable(selected ? "tooltip.fulldraw.quiver.selected" : "tooltip.fulldraw.quiver.entry",
                    t.count(), t.create().getHoverName()).withStyle(selected ? ChatFormatting.WHITE : ChatFormatting.GRAY));
        }
        lines.accept(Component.translatable("tooltip.fulldraw.quiver.hint", Component.keybind("key.fulldraw.cycle_quiver"))
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
