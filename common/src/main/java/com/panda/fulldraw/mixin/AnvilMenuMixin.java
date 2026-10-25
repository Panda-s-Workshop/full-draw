package com.panda.fulldraw.mixin;

import com.panda.fulldraw.logic.EnchantmentRules;
import com.panda.fulldraw.registry.ModEnchantments;
import java.util.Objects;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Deadeye III and Steady II come only from the Marksman's Eye: an anvil puts such a book on a bow, but doesn't merge two
 * Deadeye II (or two Steady I) into the next tier.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin extends ItemCombinerMenu {
    @Shadow @Final private DataSlot cost;

    private AnvilMenuMixin(MenuType<?> type, int id, Inventory inventory, ContainerLevelAccess access, ItemCombinerMenuSlotDefinition slots) {
        super(type, id, inventory, access, slots);
    }

    @Inject(method = "createResult", at = @At("TAIL"))
    private void fulldraw$keepEyeTiers(CallbackInfo ci) {
        ItemStack result = resultSlots.getItem(0);
        if (result.isEmpty()) return;
        ItemStack capped = result;
        capped = fulldraw$cap(capped, ModEnchantments.DEADEYE, EnchantmentRules.DEADEYE_TABLE_MAX);
        capped = fulldraw$cap(capped, ModEnchantments.STEADY, EnchantmentRules.STEADY_TABLE_MAX);
        if (capped == result) return;
        // capped back to what the left item already was (two Deadeye II): nothing to pay for
        ItemStack left = inputSlots.getItem(0);
        boolean unchanged = EnchantmentHelper.getEnchantmentsForCrafting(capped).equals(EnchantmentHelper.getEnchantmentsForCrafting(left))
                && capped.getDamageValue() == left.getDamageValue()
                && Objects.equals(capped.get(DataComponents.CUSTOM_NAME), left.get(DataComponents.CUSTOM_NAME));
        resultSlots.setItem(0, unchanged ? ItemStack.EMPTY : capped);
        if (unchanged) cost.set(0);
    }

    private ItemStack fulldraw$cap(ItemStack result, ResourceKey<Enchantment> enchantment, int tableMax) {
        int level = ModEnchantments.level(result, enchantment);
        int allowed = EnchantmentRules.anvilLevel(level, ModEnchantments.level(inputSlots.getItem(0), enchantment),
                ModEnchantments.level(inputSlots.getItem(1), enchantment), tableMax);
        if (allowed == level) return result;
        ItemStack copy = result.copy();
        ModEnchantments.setLevel(copy, enchantment, allowed);
        return copy;
    }
}
