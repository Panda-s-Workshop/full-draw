package com.panda.fulldraw.command;

import com.mojang.brigadier.CommandDispatcher;
import com.panda.fulldraw.FullDrawConfig;
import com.panda.fulldraw.item.FullDrawCrossbowItem;
import com.panda.fulldraw.item.RepeatingCrossbowItem;
import com.panda.fulldraw.logic.BoltTraits;
import com.panda.fulldraw.logic.Magazine;
import com.panda.fulldraw.logic.ShotMath;
import com.panda.fulldraw.logic.WeaponStats;
import com.panda.fulldraw.shot.Shots;
import com.panda.fulldraw.shot.Weapons;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;

/**
 * {@code /fulldraw stats}: the held weapon's numbers and the breakdown of your last bow shot that landed. Any
 * player may run it; it only reads.
 */
public final class StatsCommand {
    private StatsCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("fulldraw").then(Commands.literal("stats").executes(c -> {
            ServerPlayer player = c.getSource().getPlayerOrException();
            // a system message, not command feedback: it's the answer the player asked for, shown even where a server
            // turns send_command_feedback off
            for (Component line : lines(player)) c.getSource().sendSystemMessage(line);
            return 1;
        })));
    }

    /** What the command prints, also read by the GameTest. */
    public static List<Component> lines(Player player) {
        List<Component> out = new ArrayList<>();
        ShotMath.Tuning t = FullDrawConfig.tuning();
        ItemStack weapon = player.getMainHandItem().getItem() instanceof ProjectileWeaponItem ? player.getMainHandItem() : player.getOffhandItem();
        if (!(weapon.getItem() instanceof ProjectileWeaponItem)) {
            out.add(Component.translatable("command.fulldraw.stats.no_weapon"));
        } else if (!Weapons.inPipeline(weapon)) {
            out.add(Component.translatable("command.fulldraw.stats.opted_out", weapon.getHoverName()));
        } else if (weapon.getItem() instanceof CrossbowItem) {
            // a crossbow has no Perfect window: its load, its bolts' speed and spread, and what they do on a hit
            WeaponStats s = Weapons.stats(weapon);
            out.add(Component.translatable("command.fulldraw.stats.crossbow", weapon.getHoverName(), seconds(CrossbowItem.getChargeDuration(weapon, player)),
                    f2(s.damageMult()), f0(3.15 * s.velocityMult() * 20), f1(s.spread())));
            Magazine magazine = RepeatingCrossbowItem.magazineOf(weapon);
            if (magazine != null) out.add(Component.translatable("command.fulldraw.stats.magazine", magazine.shots(), seconds(magazine.shotTicks())));
            BoltTraits bolts = FullDrawCrossbowItem.boltsOf(weapon);
            if (bolts != null && !bolts.equals(BoltTraits.NONE))
                out.add(Component.translatable("command.fulldraw.stats.bolts", bolts.pierce(), Math.round((1.0 - bolts.armourKept()) * 100), f0(bolts.knockback()),
                        weapon.is(Weapons.SHIELD_PIERCING) ? Component.translatable("command.fulldraw.stats.shield", f0(BoltTraits.SHIELD_CRACK_SECONDS)) : Component.empty()));
        } else if (!(weapon.getItem() instanceof BowItem)) {
            // another mod's weapon that is neither: the pipeline only knows a bow's release
            out.add(Component.translatable("command.fulldraw.stats.no_window", weapon.getHoverName()));
        } else {
            WeaponStats s = Weapons.stats(weapon);
            int open = s.drawTicks() + t.perfectDelay();
            int close = open + t.perfectWindow() + s.extraWindow(); // the first tick after the window
            out.add(Component.translatable("command.fulldraw.stats.weapon", weapon.getHoverName(), seconds(s.drawTicks()), seconds(open),
                    seconds(close), f2(s.damageMult()), f0(3.0 * s.velocityMult() * 20), f1(s.spread())));
        }
        Shots.LastShot last = Shots.lastShot(player);
        if (last == null) {
            out.add(Component.translatable("command.fulldraw.stats.no_shot"));
            return out;
        }
        ShotMath.Breakdown b = last.damage();
        var item = BuiltInRegistries.ITEM.getOptional(last.weapon());
        Component weaponName = item.isPresent() ? item.get().getName(item.get().getDefaultInstance()) : Component.literal(last.weapon().toString());
        out.add(Component.translatable("command.fulldraw.stats.last_shot", weaponName, f1(b.distance()), f2(b.total())));
        out.add(Component.translatable("command.fulldraw.stats.factors", b.base(), f2(b.weaponMult()), f2(b.headPenalty()), f2(b.perfectMult()),
                f2(b.precisionMult()), f2(b.rangeMult()), f2(b.headBonus()), f2(b.skillMult()),
                b.capped() ? Component.translatable("command.fulldraw.stats.capped", f2(b.skillRaw()), f2(b.skillCap())) : Component.empty(),
                b.pointBlankMult() < 1.0 ? Component.translatable("command.fulldraw.stats.point_blank", f2(b.pointBlankMult())) : Component.empty()));
        return out;
    }

    private static String seconds(int ticks) {
        return f2(ticks / 20.0);
    }

    private static String f0(double v) {
        return String.format(Locale.ROOT, "%.0f", v);
    }

    private static String f1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static String f2(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
