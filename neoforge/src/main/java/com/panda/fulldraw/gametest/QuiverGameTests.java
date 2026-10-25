package com.panda.fulldraw.gametest;

import com.panda.fulldraw.quiver.QuiverContents;
import com.panda.fulldraw.quiver.Quivers;
import com.panda.fulldraw.registry.ModItems;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.SpectralArrow;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The Quiver in a real world. */
final class QuiverGameTests {
    private QuiverGameTests() {}

    /**
     * A bow draws the Quiver's selected arrows before loose ones in the inventory: plain arrows loose and in the quiver,
     * spectral arrows selected in the quiver. The arrow fired is spectral, the quiver's spectral stack shrinks by one,
     * and the loose arrows are untouched.
     */
    static void quiverSuppliesSelectedArrow(GameTestHelper helper) {
        Player player = archer(helper);
        ItemStack quiver = new ItemStack(ModItems.QUIVER.get());
        QuiverContents contents = QuiverContents.EMPTY.insert(new ItemStack(Items.ARROW), 8).insert(new ItemStack(Items.SPECTRAL_ARROW), 5).cycle(1);
        Quivers.set(quiver, contents);
        player.getInventory().add(new ItemStack(Items.ARROW, 16));
        player.getInventory().add(quiver);

        AbstractArrow shot = release(helper, player);
        if (!(shot instanceof SpectralArrow)) helper.fail("expected the quiver's selected spectral arrow, got " + shot.getType());
        QuiverContents after = Quivers.contents(Quivers.find(player));
        if (after.stacks().size() != 2 || after.stacks().get(1).count() != 4) helper.fail("the quiver's spectral stack should be 4 now: " + after);
        if (after.stacks().get(0).count() != 8) helper.fail("the quiver's plain arrows should be untouched: " + after);
        if (player.getInventory().countItem(Items.ARROW) != 16) helper.fail("the loose arrows should be untouched: " + player.getInventory().countItem(Items.ARROW));
        helper.succeed();
    }

    /** Another mod's bow that fires its own way: it may spend its arrows as it likes, so the Quiver gives it none. */
    abstract static class OwnFiringBow extends BowItem {
        OwnFiringBow(Properties properties) {
            super(properties);
        }

        @Override
        public boolean releaseUsing(ItemStack stack, net.minecraft.world.level.Level level, net.minecraft.world.entity.LivingEntity entity, int timeLeft) {
            return false;
        }
    }

    /** Another mod's bow that only changes its range: it still fires as vanilla's does. */
    abstract static class LongerRangeBow extends BowItem {
        LongerRangeBow(Properties properties) {
            super(properties);
        }

        @Override
        public int getDefaultProjectileRange() {
            return 20;
        }
    }

    /** A crossbow under another one that fires its own way. */
    abstract static class OwnShootingCrossbow extends net.minecraft.world.item.CrossbowItem {
        OwnShootingCrossbow(Properties properties) {
            super(properties);
        }

        @Override
        public void performShooting(net.minecraft.world.level.Level level, net.minecraft.world.entity.LivingEntity shooter, InteractionHand hand, ItemStack weapon,
                                    float velocity, float inaccuracy, @org.jetbrains.annotations.Nullable net.minecraft.world.entity.LivingEntity target) {}
    }

    abstract static class UnderOwnShooting extends OwnShootingCrossbow {
        UnderOwnShooting(Properties properties) {
            super(properties);
        }
    }

    /**
     * The shot rules and the Quiver serve vanilla's bow and crossbow, Full Draw's weapons and other mods' weapons that
     * fire as vanilla's do; a weapon that overrides how it fires (or is under one that does) gets neither. A vanilla
     * bow with the Quiver as its only ammo draws from it.
     */
    static void quiverOnlyForWeaponsFiringAsVanilla(GameTestHelper helper) {
        java.util.List<String> wrong = new java.util.ArrayList<>();
        for (var item : List.of(Items.BOW, Items.CROSSBOW, ModItems.LONGBOW.get(), ModItems.REPEATING_CROSSBOW.get(), ModItems.ARBALEST.get()))
            if (!com.panda.fulldraw.shot.Weapons.firesAsVanilla(item)) wrong.add(item + " should fire as vanilla");
        if (!com.panda.fulldraw.shot.Weapons.firesAsVanilla(LongerRangeBow.class)) wrong.add("a bow that changes only its range should fire as vanilla");
        if (com.panda.fulldraw.shot.Weapons.firesAsVanilla(OwnFiringBow.class)) wrong.add("a bow with its own release should be left alone");
        if (com.panda.fulldraw.shot.Weapons.firesAsVanilla(UnderOwnShooting.class)) wrong.add("a crossbow under one with its own shooting should be left alone");
        Player player = archer(helper);
        ItemStack quiver = new ItemStack(ModItems.QUIVER.get());
        Quivers.set(quiver, QuiverContents.EMPTY.insert(new ItemStack(Items.ARROW), 4));
        player.getInventory().add(quiver);
        ItemStack ammo = Quivers.projectileFor(player, new ItemStack(Items.BOW));
        if (ammo == null || !ammo.is(Items.ARROW)) wrong.add("a vanilla bow should draw the quiver's arrows: " + ammo);
        Quivers.clearPending();
        if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
        helper.succeed();
    }

    /** A quiver holding Broadheads and Bodkins, Bodkins selected, gives the bow a Bodkin; cycled, a Broadhead. */
    static void quiverSuppliesNewArrows(GameTestHelper helper) {
        Player player = archer(helper);
        ItemStack quiver = new ItemStack(ModItems.QUIVER.get());
        Quivers.set(quiver, QuiverContents.EMPTY.insert(new ItemStack(ModItems.BROADHEAD_ARROW.get()), 4).insert(new ItemStack(ModItems.BODKIN_ARROW.get()), 4).cycle(1));
        player.getInventory().add(quiver);
        AbstractArrow first = release(helper, player);
        if (!(first instanceof com.panda.fulldraw.entity.BodkinArrow)) helper.fail("expected the quiver's selected Bodkin, got " + first.getType());
        Quivers.set(Quivers.find(player), Quivers.contents(Quivers.find(player)).cycle(1));
        AbstractArrow second = release(helper, player);
        if (!(second instanceof com.panda.fulldraw.entity.BroadheadArrow)) helper.fail("cycled, expected a Broadhead, got " + second.getType());
        QuiverContents after = Quivers.contents(Quivers.find(player));
        if (after.stacks().get(0).count() != 3 || after.stacks().get(1).count() != 3) helper.fail("one of each should be spent: " + after);
        helper.succeed();
    }

    /**
     * Shooting a quiver's last arrow of a kind drops that kind and selects the next; cycling one kind or none does
     * nothing; cycling wraps.
     */
    static void quiverCycleSkipsEmpty(GameTestHelper helper) {
        Player player = archer(helper);
        ItemStack quiver = new ItemStack(ModItems.QUIVER.get());
        Quivers.set(quiver, QuiverContents.EMPTY.insert(new ItemStack(Items.ARROW), 3).insert(new ItemStack(Items.SPECTRAL_ARROW), 1).cycle(1));
        player.getInventory().add(quiver);

        AbstractArrow last = release(helper, player);
        if (!(last instanceof SpectralArrow)) helper.fail("the first shot should take the one spectral arrow");
        QuiverContents after = Quivers.contents(Quivers.find(player));
        if (after.stacks().size() != 1 || after.selected() != 0 || !after.selectedStack().is(Items.ARROW))
            helper.fail("the empty spectral slot should leave and plain arrows be selected: " + after);
        AbstractArrow next = release(helper, player);
        if (next instanceof SpectralArrow) helper.fail("the second shot should be a plain arrow from the quiver");
        if (Quivers.contents(Quivers.find(player)).total() != 2) helper.fail("the quiver should hold 2 arrows now");

        QuiverContents one = QuiverContents.EMPTY.insert(new ItemStack(Items.ARROW), 5);
        if (one.cycle(1) != one || QuiverContents.EMPTY.cycle(1) != QuiverContents.EMPTY) helper.fail("cycling one kind or none must change nothing");
        QuiverContents three = one.insert(new ItemStack(Items.SPECTRAL_ARROW), 2).insert(new ItemStack(Items.TIPPED_ARROW), 2);
        if (three.cycle(1).cycle(1).cycle(1).selected() != 0 || three.cycle(-1).selected() != 2) helper.fail("cycling should wrap both ways");
        if (three.roomFor(new ItemStack(Items.ARROW)) != 59) helper.fail("room on the plain stack: " + three.roomFor(new ItemStack(Items.ARROW)));
        helper.succeed();
    }

    /**
     * The bundle-style clicks through the real inventory menu: arrows onto the quiver, the quiver onto
     * arrows, the quiver onto an empty slot (takes the selected stack out), onto the crafting output (nothing moves),
     * and a fifth kind into a full quiver (refused).
     */
    static void quiverBundleClicks(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        var menu = player.inventoryMenu;
        int quiverSlot = InventoryMenu.INV_SLOT_START, arrowSlot = quiverSlot + 1, emptySlot = quiverSlot + 2;
        menu.getSlot(quiverSlot).set(new ItemStack(ModItems.QUIVER.get()));

        menu.setCarried(new ItemStack(Items.ARROW, 16));
        menu.clicked(quiverSlot, 1, ContainerInput.PICKUP, player);
        QuiverContents c = Quivers.contents(menu.getSlot(quiverSlot).getItem());
        if (!menu.getCarried().isEmpty() || c.total() != 16) helper.fail("arrows clicked onto the quiver: carried " + menu.getCarried() + ", quiver " + c);

        ItemStack quiver = menu.getSlot(quiverSlot).getItem();
        menu.getSlot(quiverSlot).set(ItemStack.EMPTY);
        menu.setCarried(quiver);
        menu.getSlot(arrowSlot).set(new ItemStack(Items.SPECTRAL_ARROW, 8));
        menu.clicked(arrowSlot, 1, ContainerInput.PICKUP, player);
        c = Quivers.contents(menu.getCarried());
        if (!menu.getSlot(arrowSlot).getItem().isEmpty() || c.stacks().size() != 2 || c.total() != 24)
            helper.fail("the quiver clicked onto spectral arrows: slot " + menu.getSlot(arrowSlot).getItem() + ", quiver " + c);

        menu.clicked(emptySlot, 1, ContainerInput.PICKUP, player);
        ItemStack out = menu.getSlot(emptySlot).getItem();
        c = Quivers.contents(menu.getCarried());
        if (!out.is(Items.ARROW) || out.getCount() != 16 || c.total() != 8) helper.fail("the quiver onto an empty slot: out " + out + ", quiver " + c);

        // an empty cursor right-clicked onto the quiver takes its selected stack
        ItemStack held = menu.getCarried();
        menu.setCarried(ItemStack.EMPTY);
        menu.getSlot(quiverSlot).set(held);
        menu.clicked(quiverSlot, 1, ContainerInput.PICKUP, player);
        if (!menu.getCarried().is(Items.SPECTRAL_ARROW) || menu.getCarried().getCount() != 8 || !Quivers.contents(menu.getSlot(quiverSlot).getItem()).isEmpty())
            helper.fail("an empty cursor onto the quiver: carried " + menu.getCarried() + ", quiver " + Quivers.contents(menu.getSlot(quiverSlot).getItem()));
        menu.setCarried(menu.getSlot(quiverSlot).getItem());
        menu.getSlot(quiverSlot).set(ItemStack.EMPTY);
        Quivers.set(menu.getCarried(), QuiverContents.EMPTY.insert(new ItemStack(Items.ARROW), 5));

        QuiverContents before = Quivers.contents(menu.getCarried());
        menu.clicked(InventoryMenu.RESULT_SLOT, 1, ContainerInput.PICKUP, player);
        if (!(menu.getCarried().getItem() instanceof com.panda.fulldraw.item.QuiverItem) || !Quivers.contents(menu.getCarried()).equals(before))
            helper.fail("the quiver onto the crafting output must move nothing: carried " + menu.getCarried());

        Quivers.set(menu.getCarried(), QuiverContents.EMPTY.insert(new ItemStack(Items.ARROW), 1).insert(new ItemStack(Items.SPECTRAL_ARROW), 1)
                .insert(net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.TIPPED_ARROW, net.minecraft.world.item.alchemy.Potions.SWIFTNESS), 1)
                .insert(net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.TIPPED_ARROW, net.minecraft.world.item.alchemy.Potions.HEALING), 1));
        menu.getSlot(arrowSlot).set(net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.TIPPED_ARROW, net.minecraft.world.item.alchemy.Potions.POISON));
        menu.clicked(arrowSlot, 1, ContainerInput.PICKUP, player);
        if (Quivers.contents(menu.getCarried()).stacks().size() != 4 || menu.getSlot(arrowSlot).getItem().isEmpty())
            helper.fail("a fifth kind must not go into a full quiver");
        helper.succeed();
    }

    private static Player archer(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(5.5, 1, 5.5));
        player.snapTo(at.x, at.y, at.z, 0, -60);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
        return player;
    }

    private static AbstractArrow release(GameTestHelper helper, Player player) {
        ItemStack bow = player.getMainHandItem();
        BowItem item = (BowItem) bow.getItem();
        AABB box = new AABB(player.blockPosition()).inflate(4);
        Set<AbstractArrow> before = new HashSet<>(helper.getLevel().getEntitiesOfClass(AbstractArrow.class, box));
        item.releaseUsing(bow, helper.getLevel(), player, item.getUseDuration(bow, player) - 23);
        List<AbstractArrow> after = helper.getLevel().getEntitiesOfClass(AbstractArrow.class, box, a -> !before.contains(a));
        if (after.size() != 1) throw helper.assertionException("expected one new arrow, found " + after.size());
        return after.getFirst();
    }
}
