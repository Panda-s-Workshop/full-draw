package com.panda.fulldraw.gametest;

import com.panda.fulldraw.item.RepeatingCrossbowItem;
import com.panda.fulldraw.registry.ModItems;
import com.panda.fulldraw.registry.ModSounds;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The Arbalest and the Repeating Crossbow in a real world. */
final class CrossbowGameTests {
    private CrossbowGameTests() {}

    /**
     * Loaded after 60 ticks of cranking (not 59; vanilla's crossbow needs 25), no walking or sprinting while cranking,
     * 600 uses; its bolt leaves at 3.6 with ×1.6 of the arrow's damage (3.2 from a plain arrow's 2), pierces 2 mobs and
     * has no random crit (a vanilla crossbow's bolt from a player always does). Quick Charge III takes 15 ticks off the
     * load, and Piercing I adds its 1 to the bolt's 2.
     */
    static void weaponStatsArbalest(GameTestHelper helper) {
        Player player = shooter(helper, new Vec3(5.5, 1, 5.5), 0, -30);
        ItemStack arbalest = new ItemStack(ModItems.ARBALEST.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, arbalest);
        CrossbowItem item = (CrossbowItem) arbalest.getItem();
        int duration = item.getUseDuration(arbalest, player);
        int charge = CrossbowItem.getChargeDuration(arbalest, player);
        int vanilla = CrossbowItem.getChargeDuration(new ItemStack(Items.CROSSBOW), player);
        // vanilla loads on the use tick that reaches full power
        item.onUseTick(helper.getLevel(), player, arbalest, duration - 59);
        boolean earlyLoaded = CrossbowItem.isCharged(arbalest);
        item.onUseTick(helper.getLevel(), player, arbalest, duration - 60);
        if (charge != 60 || vanilla != 25) helper.fail("load times: Arbalest " + charge + " (expected 60), vanilla crossbow " + vanilla + " (25)");
        else if (earlyLoaded) helper.fail("the Arbalest loaded after 59 ticks of its 60");
        else if (!CrossbowItem.isCharged(arbalest)) helper.fail("the Arbalest didn't load after 60 ticks");
        else {
            AbstractArrow bolt = fire(helper, player, item, arbalest);
            var use = arbalest.get(DataComponents.USE_EFFECTS);
            double speed = bolt.getDeltaMovement().length();
            if (Math.abs(speed - 3.6) > 0.05) helper.fail("Arbalest bolt speed " + speed + ", expected 3.6");
            else if (Math.abs(bolt.baseDamage - 3.2) > 1e-6) helper.fail("Arbalest bolt base damage " + bolt.baseDamage + ", expected 3.2 (2 × 1.6)");
            else if (bolt.getPierceLevel() != 2) helper.fail("Arbalest bolt pierce level " + bolt.getPierceLevel() + ", expected 2");
            else if (use == null || use.canSprint() || use.speedMultiplier() != 0.0F) helper.fail("cranking the Arbalest must hold you still and stop a sprint: " + use);
            else if (arbalest.getMaxDamage() != 600) helper.fail("the Arbalest should last 600 uses, not " + arbalest.getMaxDamage());
            else if (bolt.isCritArrow()) helper.fail("an Arbalest bolt shouldn't crit");
            else if (!loadAndFire(helper, shooter(helper, new Vec3(5.5, 1, 2.5), 0, -30), Items.CROSSBOW).isCritArrow())
                helper.fail("a vanilla crossbow's bolt from a player should still crit");
            else {
                ItemStack quick = enchanted(helper, ModItems.ARBALEST.get(), Enchantments.QUICK_CHARGE, 3);
                AbstractArrow pierced = loadAndFire(helper, shooter(helper, new Vec3(5.5, 1, 8.5), 0, -30),
                        enchanted(helper, ModItems.ARBALEST.get(), Enchantments.PIERCING, 1));
                if (CrossbowItem.getChargeDuration(quick, player) != 45)
                    helper.fail("Quick Charge III should load the Arbalest in 45 ticks, not " + CrossbowItem.getChargeDuration(quick, player));
                else if (pierced.getPierceLevel() != 3) helper.fail("Piercing I on the Arbalest should give pierce 3, not " + pierced.getPierceLevel());
                else helper.succeed();
            }
        }
    }

    /**
     * An Arbalest bolt strays at most 0.4 of a vanilla crossbow's: vanilla adds up to 0.0172 × the inaccuracy to each
     * axis of the aim (1.0 for a crossbow), so every Arbalest bolt stays within 0.0069. Twelve vanilla bolts go further.
     */
    static void arbalestSpread(GameTestHelper helper) {
        double heavy = 0, plain = 0;
        for (int i = 0; i < 12; i++) {
            heavy = Math.max(heavy, stray(loadAndFire(helper, shooter(helper, new Vec3(2.5, 1, 2.5), -90, -10), ModItems.ARBALEST.get())));
            plain = Math.max(plain, stray(loadAndFire(helper, shooter(helper, new Vec3(2.5, 1, 8.5), -90, -10), Items.CROSSBOW)));
            helper.getLevel().getEntitiesOfClass(AbstractArrow.class, new AABB(helper.absolutePos(net.minecraft.core.BlockPos.ZERO)).inflate(16))
                    .forEach(net.minecraft.world.entity.Entity::discard);
        }
        double limit = 0.0172275 * 0.4 + 1e-4;
        if (heavy > limit) helper.fail(String.format(java.util.Locale.ROOT, "an Arbalest bolt strayed %.4f from the aim, more than %.4f", heavy, limit));
        else if (plain <= limit) helper.fail(String.format(java.util.Locale.ROOT, "twelve vanilla bolts stayed within %.4f; the check can't tell them apart", plain));
        else helper.succeed();
    }

    /** The largest per-axis gap between a bolt's flight and where its shooter looked (pitch -10, yaw -90). */
    private static double stray(AbstractArrow bolt) {
        Vec3 aim = net.minecraft.world.entity.Entity.calculateViewVector(-10, -90);
        Vec3 d = bolt.getDeltaMovement().normalize().subtract(aim);
        return Math.max(Math.abs(d.x), Math.max(Math.abs(d.y), Math.abs(d.z)));
    }

    /**
     * A bolt from the Arbalest goes through three husks in a row and stops in the fourth, which it doesn't hurt. The
     * first takes ceil(3.6 × 3.2) = 12 through half of a husk's 2 armour, 12 × (1 − 0.2 / 25) = 11.904, every time: no crit.
     */
    static void arbalestPiercesThree(GameTestHelper helper) {
        List<Husk> husks = List.of(husk(helper, 3.5, 6.5), husk(helper, 5.5, 6.5), husk(helper, 7.5, 6.5), husk(helper, 9.5, 6.5));
        Player player = shooter(helper, new Vec3(1.5, 1, 6.5), -90, 0); // facing +X
        loadAndFire(helper, player, ModItems.ARBALEST.get());
        helper.runAfterDelay(20, () -> {
            List<Float> lost = husks.stream().map(h -> h.getMaxHealth() - h.getHealth()).toList();
            if (lost.get(0) <= 0 || lost.get(1) <= 0 || lost.get(2) <= 0) helper.fail("the bolt should hurt the first three husks in the row: lost " + lost);
            else if (lost.get(3) > 0) helper.fail("the bolt should stop in the fourth husk without hurting it: lost " + lost);
            else if (Math.abs(lost.get(0) - 11.904) > 0.001) helper.fail("the first husk should lose 11.904, not " + lost.get(0));
            else helper.succeed();
        });
    }

    /**
     * An Arbalest bolt counts half the armour. Its 12 into a husk in full iron (2 + 15 = 17 points) leaves
     * 12 × (1 − 2.5 / 25) = 10.8 with half the points; a vanilla crossbow's 7 to 11 leaves 3.2 to 5.9 through all 17.
     */
    static void arbalestHalvesArmour(GameTestHelper helper) {
        Husk heavy = armoured(husk(helper, 5.5, 6.5)), plain = armoured(husk(helper, 5.5, 2.5));
        // worn armour counts from the wearer's next tick
        helper.runAfterDelay(2, () -> {
            if (heavy.getArmorValue() != 17 || plain.getArmorValue() != 17) {
                helper.fail("the husks should wear 17 armour: " + heavy.getArmorValue() + ", " + plain.getArmorValue());
                return;
            }
            loadAndFire(helper, shooter(helper, new Vec3(1.5, 1, 6.5), -90, 0), ModItems.ARBALEST.get());
            loadAndFire(helper, shooter(helper, new Vec3(1.5, 1, 2.5), -90, 0), Items.CROSSBOW);
        });
        helper.runAfterDelay(12, () -> {
            float h = heavy.getMaxHealth() - heavy.getHealth(), p = plain.getMaxHealth() - plain.getHealth();
            if (Math.abs(h - 10.8) > 0.05) helper.fail("an Arbalest bolt into 17 armour dealt " + h + ", expected 10.8 (12 through half the armour)");
            else if (p <= 0 || p > 6.0) helper.fail("a vanilla bolt into 17 armour dealt " + p + ", expected 3.2 to 5.9");
            else helper.succeed();
        });
    }

    /**
     * An Arbalest bolt shoves what it hits as Punch II would (2 × 0.6 on top of the hit's own 0.4); a vanilla crossbow's
     * bolt gives only the hit's own.
     */
    static void arbalestKnocksBack(GameTestHelper helper) {
        Husk heavy = husk(helper, 4.5, 6.5), plain = husk(helper, 4.5, 2.5);
        loadAndFire(helper, shooter(helper, new Vec3(1.5, 1, 6.5), -90, 0), ModItems.ARBALEST.get());
        loadAndFire(helper, shooter(helper, new Vec3(1.5, 1, 2.5), -90, 0), Items.CROSSBOW);
        double[] fastest = new double[2];
        for (int t = 1; t <= 12; t++) {
            helper.runAfterDelay(t, () -> {
                fastest[0] = Math.max(fastest[0], heavy.getDeltaMovement().horizontalDistance());
                fastest[1] = Math.max(fastest[1], plain.getDeltaMovement().horizontalDistance());
            });
        }
        helper.runAfterDelay(14, () -> {
            if (heavy.getHealth() >= heavy.getMaxHealth() || plain.getHealth() >= plain.getMaxHealth()) helper.fail("both bolts should hit their husk");
            else if (fastest[0] < fastest[1] + 0.8) helper.fail(String.format(java.util.Locale.ROOT,
                    "the Arbalest bolt should shove much harder: %.2f vs a vanilla bolt's %.2f blocks a tick", fastest[0], fastest[1]));
            else helper.succeed();
        });
    }

    /**
     * A bolt from the Arbalest goes through a raised shield (a piercing bolt always has) and knocks it down for 3 s;
     * a vanilla crossbow's bolt is stopped by the shield, which stays up. From behind, the Arbalest's bolt hurts but
     * leaves the shield up: only a shield that faces the bolt is knocked down.
     */
    @SuppressWarnings("removal")
    static void arbalestBreaksShieldBlock(GameTestHelper helper) {
        ServerPlayer heavyTarget = blocker(helper, new Vec3(6.5, 1, 6.5), 90), plainTarget = blocker(helper, new Vec3(6.5, 1, 2.5), 90),
                backTarget = blocker(helper, new Vec3(6.5, 1, 10.5), -90);
        if (!heavyTarget.isBlocking() || !plainTarget.isBlocking() || !backTarget.isBlocking()) {
            helper.fail("the targets should have their shields raised before the shots");
            return;
        }
        loadAndFire(helper, shooter(helper, new Vec3(1.5, 1, 6.5), -90, 0), ModItems.ARBALEST.get());
        loadAndFire(helper, shooter(helper, new Vec3(1.5, 1, 2.5), -90, 0), Items.CROSSBOW);
        loadAndFire(helper, shooter(helper, new Vec3(1.5, 1, 10.5), -90, 0), ModItems.ARBALEST.get());
        helper.runAfterDelay(10, () -> {
            ItemStack heavyShield = heavyTarget.getMainHandItem(), plainShield = plainTarget.getMainHandItem(), backShield = backTarget.getMainHandItem();
            if (heavyTarget.getHealth() >= heavyTarget.getMaxHealth()) helper.fail("the Arbalest bolt should go through the raised shield");
            else if (!heavyTarget.getCooldowns().isOnCooldown(heavyShield) || heavyTarget.isUsingItem())
                helper.fail("the Arbalest bolt should knock the shield down: cooldown " + heavyTarget.getCooldowns().isOnCooldown(heavyShield) + ", still raised " + heavyTarget.isUsingItem());
            else if (plainTarget.getHealth() < plainTarget.getMaxHealth()) helper.fail("the shield should stop a vanilla bolt: health " + plainTarget.getHealth());
            else if (plainTarget.getCooldowns().isOnCooldown(plainShield)) helper.fail("a vanilla bolt mustn't knock the shield down");
            else if (backTarget.getHealth() >= backTarget.getMaxHealth()) helper.fail("the Arbalest bolt should hurt a shield-bearer from behind");
            else if (backTarget.getCooldowns().isOnCooldown(backShield)) helper.fail("a bolt from behind mustn't knock the shield down");
            else {
                // down for 3 s, 60 ticks of the player's cooldowns (ticked here: nothing ticks a mock player's)
                for (int i = 0; i < 59; i++) heavyTarget.getCooldowns().tick();
                boolean downAt59 = heavyTarget.getCooldowns().isOnCooldown(heavyShield);
                heavyTarget.getCooldowns().tick();
                if (!downAt59 || heavyTarget.getCooldowns().isOnCooldown(heavyShield))
                    helper.fail("the shield should be down for exactly 60 ticks: down at 59 " + downAt59 + ", at 60 " + heavyTarget.getCooldowns().isOnCooldown(heavyShield));
                else helper.succeed();
            }
        });
    }

    /**
     * Loaded after 80 ticks of cranking (not 79); each bolt leaves at 3.0 with ×0.45 of the arrow's damage (0.9 from a
     * plain arrow's 2), strays at most 0.0172 × 2.0 per axis and further than a vanilla bolt can; 520 uses; walking
     * while cranking as with vanilla's crossbow.
     */
    static void weaponStatsRepeatingCrossbow(GameTestHelper helper) {
        Player player = shooter(helper, new Vec3(2.5, 1, 2.5), -90, -10);
        player.getInventory().setItem(9, new ItemStack(Items.ARROW, 64));
        ItemStack repeater = new ItemStack(ModItems.REPEATING_CROSSBOW.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, repeater);
        CrossbowItem item = (CrossbowItem) repeater.getItem();
        int duration = item.getUseDuration(repeater, player);
        item.onUseTick(helper.getLevel(), player, repeater, duration - 79);
        boolean earlyLoaded = CrossbowItem.isCharged(repeater);
        item.onUseTick(helper.getLevel(), player, repeater, duration - 80);
        var use = repeater.get(DataComponents.USE_EFFECTS);
        if (CrossbowItem.getChargeDuration(repeater, player) != 80) helper.fail("the Repeating Crossbow should load in 80 ticks, not " + CrossbowItem.getChargeDuration(repeater, player));
        else if (earlyLoaded || !CrossbowItem.isCharged(repeater)) helper.fail("the Repeating Crossbow should load at 80 ticks of cranking, not 79: early " + earlyLoaded);
        else if (repeater.getMaxDamage() != 520) helper.fail("the Repeating Crossbow should last 520 uses, not " + repeater.getMaxDamage());
        else if (use != null && !use.equals(new ItemStack(Items.CROSSBOW).get(DataComponents.USE_EFFECTS)))
            helper.fail("cranking the Repeating Crossbow should slow you as vanilla's crossbow does: " + use);
        else {
            // vanilla adds the spread to the aim without rescaling, so one bolt's speed is off by up to a tenth: average them
            double worst = 0, speeds = 0;
            AbstractArrow first = null;
            for (int i = 0; i < 35; i++) {
                if (i > 0 && i % 5 == 0) load(helper, player, repeater);
                AbstractArrow bolt = fire(helper, player, item, repeater);
                if (first == null) first = bolt;
                worst = Math.max(worst, stray(bolt));
                speeds += bolt.getDeltaMovement().length();
            }
            double speed = speeds / 35, limit = 0.0172275 * 2.0 + 1e-4;
            if (Math.abs(speed - 3.0) > 0.03) helper.fail("Repeating Crossbow bolts' mean speed " + speed + ", expected 3.0");
            else if (Math.abs(first.baseDamage - 0.9) > 1e-6) helper.fail("Repeating Crossbow bolt base damage " + first.baseDamage + ", expected 0.9 (2 × 0.45)");
            else if (first.isCritArrow()) helper.fail("a Repeating Crossbow bolt shouldn't crit");
            else if (worst > limit) helper.fail(String.format(java.util.Locale.ROOT, "a Repeating Crossbow bolt strayed %.4f, more than %.4f", worst, limit));
            else if (worst <= 0.0172275) helper.fail(String.format(java.util.Locale.ROOT, "35 Repeating Crossbow bolts stayed within a vanilla bolt's %.4f", worst));
            else helper.succeed();
        }
    }

    /**
     * One crank loads five shots and takes five arrows; each use fires one bolt and puts the crossbow on a 12-tick
     * cooldown; the fifth empties it. With three arrows it loads three shots. With Multishot each shot is three bolts,
     * for one arrow, and stays three after the enchantment is taken off.
     */
    static void repeaterMagazineFive(GameTestHelper helper) {
        Player player = shooter(helper, new Vec3(2.5, 1, 2.5), -90, -10);
        ItemStack repeater = load(helper, player, new ItemStack(ModItems.REPEATING_CROSSBOW.get()));
        CrossbowItem item = (CrossbowItem) repeater.getItem();
        int loaded = repeater.get(DataComponents.CHARGED_PROJECTILES).size(), arrowsLeft = player.getInventory().getItem(9).getCount();
        if (loaded != 5 || arrowsLeft != 3) {
            helper.fail("one crank should load 5 bolts from 8 arrows: loaded " + loaded + ", " + arrowsLeft + " arrows left");
            return;
        }
        fire(helper, player, item, repeater);
        if (repeater.get(DataComponents.CHARGED_PROJECTILES).size() != 4 || !player.getCooldowns().isOnCooldown(repeater)) {
            helper.fail("a shot should leave 4 loaded and a 12-tick cooldown: " + repeater.get(DataComponents.CHARGED_PROJECTILES).size()
                    + " loaded, cooldown " + player.getCooldowns().isOnCooldown(repeater));
            return;
        }
        // nothing ticks a mock player's cooldowns
        for (int i = 0; i < 11; i++) player.getCooldowns().tick();
        boolean downAt11 = player.getCooldowns().isOnCooldown(repeater);
        player.getCooldowns().tick();
        if (!downAt11 || player.getCooldowns().isOnCooldown(repeater)) {
            helper.fail("the cooldown between shots should be exactly 12 ticks: at 11 " + downAt11 + ", at 12 " + player.getCooldowns().isOnCooldown(repeater));
            return;
        }
        for (int i = 0; i < 4; i++) fire(helper, player, item, repeater);
        if (CrossbowItem.isCharged(repeater)) {
            helper.fail("five shots should empty the magazine");
            return;
        }
        // three arrows load three shots
        Player few = shooter(helper, new Vec3(2.5, 1, 6.5), -90, -10);
        few.getInventory().setItem(9, new ItemStack(Items.ARROW, 3));
        ItemStack partial = load(helper, few, new ItemStack(ModItems.REPEATING_CROSSBOW.get()));
        // Multishot: three bolts a shot, still one arrow a shot
        Player multi = shooter(helper, new Vec3(2.5, 1, 9.5), -90, -10);
        ItemStack fan = load(helper, multi, enchanted(helper, ModItems.REPEATING_CROSSBOW.get(), Enchantments.MULTISHOT, 1));
        // a grindstone after loading: the shots stay as they were loaded
        fan.set(DataComponents.ENCHANTMENTS, net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        AABB box = new AABB(multi.blockPosition()).inflate(4);
        int before = helper.getLevel().getEntitiesOfClass(AbstractArrow.class, box).size();
        item.use(helper.getLevel(), multi, InteractionHand.MAIN_HAND);
        int fired = helper.getLevel().getEntitiesOfClass(AbstractArrow.class, box).size() - before;
        if (partial.get(DataComponents.CHARGED_PROJECTILES).size() != 3) helper.fail("three arrows should load three shots, not " + partial.get(DataComponents.CHARGED_PROJECTILES).size());
        else if (fan.get(DataComponents.CHARGED_PROJECTILES).size() != 12 || fired != 3 || multi.getInventory().getItem(9).getCount() != 3)
            helper.fail("Multishot should load 5 shots of 3 for 5 arrows and fire 3 a shot: " + fan.get(DataComponents.CHARGED_PROJECTILES).size()
                    + " left after a shot, " + fired + " fired, " + multi.getInventory().getItem(9).getCount() + " arrows left");
        else if (RepeatingCrossbowItem.shotsLoaded(fan) != 4) helper.fail("the sprite should count 4 shots left, not " + RepeatingCrossbowItem.shotsLoaded(fan));
        else helper.succeed();
    }

    /**
     * A full magazine lands on one target: five bolts 12 ticks apart all hurt a husk 8 blocks away, 3 each through its 2
     * armour (2.94), 14.7 in all. Fired 8 ticks apart, the husk's damage cooldown turns bolts 2 and 4 away, which is why
     * the Repeating Crossbow waits 12.
     */
    static void repeaterBurstLands(GameTestHelper helper) {
        Husk spaced = husk(helper, 10.5, 2.5), crowded = husk(helper, 10.5, 8.5);
        burst(helper, shooter(helper, new Vec3(2.5, 1, 2.5), -90, 0), com.panda.fulldraw.logic.Magazine.REPEATING_CROSSBOW.shotTicks());
        burst(helper, shooter(helper, new Vec3(2.5, 1, 8.5), -90, 0), 8);
        helper.runAfterDelay(80, () -> {
            float a = spaced.getMaxHealth() - spaced.getHealth(), b = crowded.getMaxHealth() - crowded.getHealth();
            if (Math.abs(a - 14.7) > 0.05) helper.fail("five bolts 12 ticks apart should all land: lost " + a + ", expected 14.7");
            else if (b > 9.0) helper.fail("bolts 8 ticks apart were expected to lose two to the damage cooldown, but the husk lost " + b);
            else helper.succeed();
        });
    }

    /** Loads a Repeating Crossbow and fires its five shots {@code gap} ticks apart (calling use directly skips the cooldown). */
    private static void burst(GameTestHelper helper, Player player, int gap) {
        ItemStack repeater = load(helper, player, new ItemStack(ModItems.REPEATING_CROSSBOW.get()));
        for (int i = 0; i < 5; i++)
            helper.runAfterDelay(1 + i * gap, () -> ((CrossbowItem) repeater.getItem()).use(helper.getLevel(), player, InteractionHand.MAIN_HAND));
    }

    /**
     * A Blast Arrow loads alone: a crank on Blast Arrows loads one shot, and a crank on two arrows with Blast Arrows
     * behind them loads the two arrows and stops.
     */
    static void repeaterLoadsBlastAlone(GameTestHelper helper) {
        Player player = shooter(helper, new Vec3(2.5, 1, 2.5), -90, 0);
        player.getInventory().setItem(9, new ItemStack(ModItems.BLAST_ARROW.get(), 8));
        int alone = RepeatingCrossbowItem.shotsLoaded(load(helper, player, new ItemStack(ModItems.REPEATING_CROSSBOW.get())));
        int blastLeft = player.getInventory().getItem(9).getCount();
        player.getInventory().setItem(9, new ItemStack(Items.ARROW, 2));
        player.getInventory().setItem(10, new ItemStack(ModItems.BLAST_ARROW.get(), 8));
        int behind = RepeatingCrossbowItem.shotsLoaded(load(helper, player, new ItemStack(ModItems.REPEATING_CROSSBOW.get())));
        if (alone != 1 || blastLeft != 7) helper.fail("a crank on Blast Arrows should load one: " + alone + " shots, " + blastLeft + " left of 8");
        else if (behind != 2 || player.getInventory().getItem(10).getCount() != 8) helper.fail("the drawing should stop before a Blast Arrow: " + behind + " shots");
        else helper.succeed();
    }

    /** A shot that breaks the Repeating Crossbow drops the four arrows still loaded instead of losing them. */
    static void repeaterBreakDropsAmmo(GameTestHelper helper) {
        Player player = shooter(helper, new Vec3(2.5, 1, 2.5), -90, 0);
        ItemStack worn = new ItemStack(ModItems.REPEATING_CROSSBOW.get());
        worn.setDamageValue(worn.getMaxDamage() - 1);
        load(helper, player, worn);
        ((CrossbowItem) worn.getItem()).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        int dropped = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(player.blockPosition()).inflate(3),
                e -> e.getItem().is(Items.ARROW)).stream().mapToInt(e -> e.getItem().getCount()).sum();
        if (!player.getMainHandItem().isEmpty()) helper.fail("the last use should have broken the crossbow");
        else if (dropped != 4) helper.fail("the four loaded arrows should drop when the crossbow breaks, not " + dropped);
        else helper.succeed();
    }

    /** Cranks the crossbow in the shooter's hand to full and returns it loaded. */
    private static ItemStack load(GameTestHelper helper, Player player, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        CrossbowItem item = (CrossbowItem) stack.getItem();
        item.onUseTick(helper.getLevel(), player, stack, item.getUseDuration(stack, player) - CrossbowItem.getChargeDuration(stack, player));
        if (!CrossbowItem.isCharged(stack)) throw helper.assertionException("the crossbow didn't load");
        return stack;
    }

    private static Player shooter(GameTestHelper helper, Vec3 local, float yaw, float pitch) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(local);
        player.snapTo(at.x, at.y, at.z, yaw, pitch);
        // a crossbow aims where the head looks, and an unticked mock player's head hasn't turned with snapTo
        player.setYHeadRot(yaw);
        player.yHeadRotO = yaw;
        // in the inventory, not the hotbar slot the crossbow goes into
        player.getInventory().setItem(9, new ItemStack(Items.ARROW, 8));
        return player;
    }

    private static AbstractArrow loadAndFire(GameTestHelper helper, Player player, net.minecraft.world.item.Item crossbow) {
        return loadAndFire(helper, player, new ItemStack(crossbow));
    }

    /** Loads the crossbow in the shooter's hand as a full crank would and fires it. */
    private static AbstractArrow loadAndFire(GameTestHelper helper, Player player, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        CrossbowItem item = (CrossbowItem) stack.getItem();
        item.onUseTick(helper.getLevel(), player, stack, item.getUseDuration(stack, player) - CrossbowItem.getChargeDuration(stack, player));
        if (!CrossbowItem.isCharged(stack)) throw helper.assertionException("the crossbow didn't load");
        return fire(helper, player, item, stack);
    }

    private static AbstractArrow fire(GameTestHelper helper, Player player, CrossbowItem item, ItemStack stack) {
        AABB box = new AABB(player.blockPosition()).inflate(4);
        Set<AbstractArrow> before = new HashSet<>(helper.getLevel().getEntitiesOfClass(AbstractArrow.class, box));
        item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        List<AbstractArrow> after = helper.getLevel().getEntitiesOfClass(AbstractArrow.class, box, a -> !before.contains(a));
        if (after.size() != 1) throw helper.assertionException("expected one new bolt, found " + after.size());
        return after.getFirst();
    }

    /**
     * The sounds vanilla picks for a crank: the Arbalest's own; Quick Charge's on a Quick Charge Arbalest, as on any
     * crossbow; vanilla's for the Repeating Crossbow and a vanilla crossbow.
     */
    static void crankSounds(GameTestHelper helper) {
        try {
            var pick = CrossbowItem.class.getDeclaredMethod("getChargingSounds", ItemStack.class);
            pick.setAccessible(true);
            var field = CrossbowItem.class.getDeclaredField("DEFAULT_SOUNDS");
            field.setAccessible(true);
            Object vanilla = field.get(null);
            var arbalest = (CrossbowItem.ChargingSounds) pick.invoke(null, new ItemStack(ModItems.ARBALEST.get()));
            Object quick = pick.invoke(null, enchanted(helper, ModItems.ARBALEST.get(), Enchantments.QUICK_CHARGE, 1));
            Object vanillaQuick = pick.invoke(null, enchanted(helper, Items.CROSSBOW, Enchantments.QUICK_CHARGE, 1));
            Object repeater = pick.invoke(null, new ItemStack(ModItems.REPEATING_CROSSBOW.get()));
            Object plain = pick.invoke(null, new ItemStack(Items.CROSSBOW));
            boolean own = arbalest.start().map(h -> h.value() == ModSounds.ARBALEST_CRANK_START.get()).orElse(false)
                    && arbalest.mid().map(h -> h.value() == ModSounds.ARBALEST_CRANK_MIDDLE.get()).orElse(false)
                    && arbalest.end().map(h -> h.value() == ModSounds.ARBALEST_CRANK_END.get()).orElse(false);
            if (!own) helper.fail("the Arbalest should crank with its own three sounds: " + arbalest);
            else if (!quick.equals(vanillaQuick) || quick == vanilla) helper.fail("a Quick Charge Arbalest should play Quick Charge's crank: " + quick);
            else if (repeater != vanilla || plain != vanilla) helper.fail("the Repeating Crossbow and a vanilla crossbow keep vanilla's crank: " + repeater + ", " + plain);
            else helper.succeed();
        } catch (ReflectiveOperationException e) {
            helper.fail("couldn't ask vanilla for the crank sounds: " + e);
        }
    }

    private static ItemStack enchanted(GameTestHelper helper, net.minecraft.world.item.Item item, ResourceKey<Enchantment> key, int level) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key), level);
        return stack;
    }

    private static Husk husk(GameTestHelper helper, double x, double z) {
        Husk husk = helper.spawnWithNoFreeWill(EntityTypes.HUSK, new Vec3(x, 1, z));
        husk.setBaby(false);
        for (EquipmentSlot slot : EquipmentSlot.values()) husk.setItemSlot(slot, ItemStack.EMPTY);
        return husk;
    }

    private static Husk armoured(Husk husk) {
        husk.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        husk.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        husk.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        husk.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        return husk;
    }

    /** A survival player facing {@code yaw} (90: -X, toward the shooters) with a shield raised long enough to block. */
    @SuppressWarnings("removal")
    private static ServerPlayer blocker(GameTestHelper helper, Vec3 local, float yaw) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        // a player is invulnerable until its client says the world has loaded; a mock player's never does
        player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        Vec3 at = helper.absoluteVec(local);
        player.snapTo(at.x, at.y, at.z, yaw, 0);
        player.setYHeadRot(yaw);
        player.yHeadRotO = yaw;
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHIELD));
        player.startUsingItem(InteractionHand.MAIN_HAND);
        for (int i = 0; i < 8; i++) player.doTick();
        return player;
    }
}
