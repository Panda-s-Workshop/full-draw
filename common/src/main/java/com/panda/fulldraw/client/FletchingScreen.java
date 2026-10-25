package com.panda.fulldraw.client;

import com.panda.fulldraw.FullDrawMod;
import com.panda.fulldraw.fletching.FletchingMenu;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ItemCombinerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The Fletching Table's screen: the four part slots, each empty one showing a faded part it takes (in turn, when it takes
 * several), an arrow to the result, and vanilla's crossed-out arrow when what lies there makes nothing, with what the
 * nearest arrow still needs written under the result. Hovering an empty part slot names the items it takes;
 * hovering the crossed-out arrow names what the nearest arrows need.
 */
public class FletchingScreen extends ItemCombinerScreen<FletchingMenu> {
    private static final Identifier TEXTURE = FullDrawMod.id("textures/gui/container/fletching.png");
    private static final Identifier ERROR = Identifier.withDefaultNamespace("container/smithing/error");
    /** The arrow between the parts and the result, where the background draws it. */
    public static final int ARROW_X = 89, ARROW_Y = 38;
    /** The parts' faint pictures in the texture, in slot order, 16 apart from here at v = 0. */
    private static final int GHOST_U = 176;
    private static final String[] SLOT_NAMES = {"head", "shaft", "fletching", "extra"};
    /** How long each of an empty slot's parts shows before the next, as vanilla's cycling slot pictures. */
    private static final long CYCLE_MS = 1500;
    /** The slot's grey drawn over a faded part, so it reads as a picture, not an item. */
    private static final int FADE = 0xC08B8B8B;
    /** Where the nearest arrow's need is written: the free row under the result, right of the Extra slot, in the label colour. */
    private static final int HINT_X = 66, HINT_Y = 61, LABEL = 0xFF404040;
    /** At most this many items listed under a slot's name; the rest are counted. */
    private static final int LISTED = 6;
    /** Dyed families a slot takes whole, named once rather than colour by colour. */
    private static final List<Map.Entry<TagKey<Item>, String>> FAMILIES = List.of(Map.entry(ItemTags.WOOL, "gui.fulldraw.fletching.any_wool"));

    /** Each part slot's pictures, by slot, worked out when the screen opens. */
    private final List<List<ItemStack>> ghosts = new ArrayList<>();

    public FletchingScreen(FletchingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, TEXTURE);
        for (int slot = 0; slot <= FletchingMenu.EXTRA; slot++) ghosts.add(slot < FletchingMenu.HEAD ? List.of() : slotParts(slot));
    }

    /**
     * Each empty part slot shows a faded part it takes, the next every {@link #CYCLE_MS} when it takes several (a dyed
     * family once); with no part in its tag, the panel's own picture. A filled slot shows only its item. Then the
     * nearest arrow's need, when the arrow is crossed out.
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        long turn = net.minecraft.util.Util.getMillis() / CYCLE_MS;
        for (int slot = FletchingMenu.HEAD; slot <= FletchingMenu.EXTRA; slot++) {
            var s = menu.getSlot(slot);
            if (s.hasItem()) continue;
            int x = leftPos + s.x, y = topPos + s.y;
            List<ItemStack> parts = ghosts.get(slot);
            if (parts.isEmpty()) {
                g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, GHOST_U + 16 * slot, 0, 16, 16, 256, 256);
                continue;
            }
            g.fakeItem(parts.get((int) (turn % parts.size())), x, y);
            g.fill(x, y, x + 16, y + 16, FADE);
        }
        if (menu.hasRecipeError() && !menu.hints().isEmpty()) inlineHint(g, menu.hints().getFirst());
    }

    /**
     * The nearest arrow's need, under the result: the arrow, then the part it needs with the count, or the part it's
     * made without ("[Broadhead Arrow] needs [2 Iron Nugget]", "[Arrow] without [String]").
     */
    private void inlineHint(GuiGraphicsExtractor g, FletchingMenu.Hint hint) {
        int x = leftPos + HINT_X, y = topPos + HINT_Y;
        g.fakeItem(new ItemStack(hint.result()), x, y);
        x += 19;
        boolean without = hint.count() == 0;
        ItemStack part = without ? menu.getSlot(hint.slot()).getItem().copyWithCount(1) : new ItemStack(hint.item(), hint.count());
        if (part.isEmpty()) return;
        Component words = Component.translatable(without ? "gui.fulldraw.fletching.inline_without" : "gui.fulldraw.fletching.inline_needs");
        g.text(font, words, x, y + 4, LABEL, false);
        x += font.width(words) + 3;
        g.fakeItem(part, x, y);
        if (!without) g.itemDecorations(font, part, x, y);
    }

    /** The parts an empty slot shows in turn: its tag's items, a dyed family as its first. */
    public static List<ItemStack> slotParts(int slot) {
        List<ItemStack> parts = new ArrayList<>();
        Set<TagKey<Item>> families = new HashSet<>();
        for (Holder<Item> item : BuiltInRegistries.ITEM.getTagOrEmpty(FletchingMenu.SLOT_TAGS.get(slot))) {
            var family = FAMILIES.stream().filter(f -> item.is(f.getKey())).findFirst();
            if (family.isEmpty() || families.add(family.get().getKey())) parts.add(new ItemStack(item));
        }
        return parts;
    }

    @Override
    protected void extractErrorIcon(GuiGraphicsExtractor g, int left, int top) {
        if (menu.hasRecipeError()) g.blitSprite(RenderPipelines.GUI_TEXTURED, ERROR, left + ARROW_X - 2, top + ARROW_Y - 3, 28, 21);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        List<Component> said;
        if (menu.hasRecipeError() && isHovering(ARROW_X - 2, ARROW_Y - 3, 28, 21, mouseX, mouseY)) said = hintLines(menu.hints());
        else if (hoveredSlot != null && !hoveredSlot.hasItem() && menu.getCarried().isEmpty() && hoveredSlot.index <= FletchingMenu.EXTRA) said = slotLines(hoveredSlot.index);
        else return;
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (Component line : said) lines.addAll(font.split(line, 200));
        g.setTooltipForNextFrame(font, lines, mouseX, mouseY);
    }

    /**
     * The crossed-out arrow's tooltip: that nothing is made, then what the nearest recipes still need ("Rope Arrow:
     * needs 4 String in the Extra slot"), as the server worked it out.
     */
    public static List<Component> hintLines(List<FletchingMenu.Hint> hints) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.fulldraw.fletching.no_recipe"));
        for (FletchingMenu.Hint hint : hints) {
            Component arrow = new ItemStack(hint.result()).getHoverName(), slot = Component.translatable("gui.fulldraw.fletching." + SLOT_NAMES[hint.slot()]);
            lines.add((hint.count() == 0 ? Component.translatable("gui.fulldraw.fletching.leave_empty", arrow, slot)
                    : Component.translatable("gui.fulldraw.fletching.needs", arrow, hint.count(), new ItemStack(hint.item()).getHoverName(), slot)).withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    /** An empty part slot's tooltip: its name, then the items its tag lets in (a whole dyed family as one line). */
    public static List<Component> slotLines(int slot) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.fulldraw.fletching." + SLOT_NAMES[slot]));
        List<Component> takes = new ArrayList<>();
        Set<String> families = new HashSet<>();
        for (Holder<Item> item : BuiltInRegistries.ITEM.getTagOrEmpty(FletchingMenu.SLOT_TAGS.get(slot))) {
            var family = FAMILIES.stream().filter(f -> item.is(f.getKey())).findFirst();
            if (family.isEmpty()) takes.add(new ItemStack(item).getHoverName());
            else if (families.add(family.get().getValue())) takes.add(Component.translatable(family.get().getValue()));
        }
        if (takes.isEmpty()) lines.add(Component.translatable("gui.fulldraw.fletching.takes_nothing").withStyle(ChatFormatting.GRAY));
        for (int i = 0; i < Math.min(LISTED, takes.size()); i++) lines.add(takes.get(i).copy().withStyle(ChatFormatting.GRAY));
        if (takes.size() > LISTED) lines.add(Component.translatable("gui.fulldraw.fletching.and_more", takes.size() - LISTED).withStyle(ChatFormatting.GRAY));
        return lines;
    }
}
