package com.panda.fulldraw.neoforge;

import com.panda.fulldraw.FullDrawConfig;
import com.panda.fulldraw.logic.MarksmanRules;
import com.panda.fulldraw.logic.ShotMath;
import net.neoforged.neoforge.common.ModConfigSpec;

/** The server's settings as a NeoForge config (synced from server to clients). */
public final class NeoForgeConfig implements FullDrawConfig.Source {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    private static final ShotMath.Tuning D = ShotMath.DEFAULTS;

    private static final ModConfigSpec.BooleanValue VANILLA_CRIT = B
            .comment("Players' full-draw bow shots crit as in vanilla. false: only a Perfect Release crits.")
            .define("vanillaCritOnFullDraw", false);

    static {
        B.comment("The Perfect Release").push("perfect");
    }

    private static final ModConfigSpec.IntValue WINDOW = B.comment("Ticks the Perfect window stays open (a weapon's own extra window adds to this).")
            .defineInRange("perfectWindowTicks", D.perfectWindow(), 1, 20);
    private static final ModConfigSpec.IntValue DELAY = B.comment("Ticks after full draw the Perfect window opens.")
            .defineInRange("perfectDelayTicks", D.perfectDelay(), 0, 20);
    private static final ModConfigSpec.DoubleValue PERFECT = B.comment("Damage multiplier of a Perfect Release.")
            .defineInRange("perfectMultiplier", D.perfectMult(), 1.0, 5.0);

    static {
        B.pop().comment("Precision hits, range and the cap").push("damage");
    }

    private static final ModConfigSpec.DoubleValue PRECISION_MOBS = B.comment("Head or weak-spot multiplier against mobs.")
            .defineInRange("precisionMultiplierMobs", D.precisionMobs(), 1.0, 5.0);
    private static final ModConfigSpec.DoubleValue PRECISION_PLAYERS = B.comment("Head multiplier against players.")
            .defineInRange("precisionMultiplierPlayers", D.precisionPlayers(), 1.0, 5.0);
    private static final ModConfigSpec.DoubleValue CAP = B.comment("The most Perfect × precision × range may multiply a shot by.")
            .defineInRange("skillCap", D.skillCap(), 1.0, 10.0);
    private static final ModConfigSpec.DoubleValue RANGE_START = B.comment("Blocks of flight before the range bonus starts.")
            .defineInRange("rangeBonusStart", D.rangeStart(), 0.0, 256.0);
    private static final ModConfigSpec.DoubleValue RANGE_PER_BLOCK = B.comment("Range bonus per block beyond the start.")
            .defineInRange("rangeBonusPerBlock", D.rangePerBlock(), 0.0, 1.0);
    private static final ModConfigSpec.DoubleValue RANGE_CAP = B.comment("The largest range multiplier.")
            .defineInRange("rangeBonusCap", D.rangeCap(), 1.0, 5.0);
    private static final ModConfigSpec.DoubleValue POINT_BLANK = B.comment("Below this many blocks, a shot is point-blank.")
            .defineInRange("pointBlankDistance", D.pointBlankDistance(), 0.0, 16.0);
    private static final ModConfigSpec.DoubleValue POINT_BLANK_MULT = B.comment("Damage multiplier of a point-blank shot.")
            .defineInRange("pointBlankMultiplier", D.pointBlankMult(), 0.0, 1.0);

    static {
        B.pop().comment("The tremble of a held draw").push("tremble");
    }

    private static final ModConfigSpec.DoubleValue TREMBLE_START = B.comment("Seconds past full draw the tremble starts.")
            .defineInRange("trembleStartSeconds", D.trembleStartSeconds(), 0.0, 60.0);
    private static final ModConfigSpec.DoubleValue TREMBLE_FULL = B.comment("Seconds past full draw the tremble is at its worst.")
            .defineInRange("trembleFullSeconds", D.trembleFullSeconds(), 0.1, 120.0);
    private static final ModConfigSpec.DoubleValue TREMBLE_MAX = B.comment("Spread at the worst of the tremble (vanilla's bow is 1.0).")
            .defineInRange("trembleMaxSpread", D.trembleMaxSpread(), 0.0, 20.0);

    static {
        B.pop().comment("Arrows").push("arrows");
    }

    private static final ModConfigSpec.BooleanValue BLAST_BREAKS = B.comment("Blast Arrows break blocks, as TNT does, where the tnt_explodes game rule allows (a mob's under mobGriefing). false: they only hurt and knock back.")
            .define("blastArrowBreaksBlocks", false);
    private static final ModConfigSpec.IntValue ROPE_LENGTH = B.comment("The most rope one Rope Arrow hangs, counting its knot.")
            .defineInRange("ropeArrowLength", com.panda.fulldraw.logic.ArrowTraits.ROPE_LENGTH, 1, 64);
    private static final ModConfigSpec.DoubleValue RECOVERY = B.comment("The chance a plain arrow stuck in a mob drops back when it dies. Other arrows keep their ratio to it (a Broadhead's is 5/8 of it; a Blunt's always comes back at the default). 0: none come back.")
            .defineInRange("arrowRecoveryChance", com.panda.fulldraw.logic.ArrowTraits.PLAIN_RECOVERY, 0.0, 1.0);
    private static final ModConfigSpec.IntValue PICKUP_LIFETIME = B.comment("Seconds an arrow a player can pick up stays in a block (vanilla: 60).")
            .defineInRange("arrowPickupLifetimeSeconds", com.panda.fulldraw.logic.ArrowTraits.PICKUP_LIFETIME_SECONDS, 1, com.panda.fulldraw.logic.ArrowTraits.MAX_PICKUP_LIFETIME_SECONDS);

    static {
        B.pop().comment("Mobs").push("mobs");
    }

    private static final ModConfigSpec.BooleanValue SHIELDBEARER_SPAWNS = B.comment("Shieldbearers join raids from the third wave and guard pillager outposts. false: only spawn eggs and commands make them.")
            .define("shieldbearerSpawns", true);
    private static final ModConfigSpec.BooleanValue HARRIER_SPAWNS = B.comment("Gale Harriers spawn on their own over the windswept hills and peaks by day. false: only spawn eggs and commands make them.")
            .define("galeHarrierSpawns", true);
    private static final ModConfigSpec.BooleanValue LURKER_SPAWNS = B.comment("Bog Lurkers spawn on their own in swamp water. false: only spawn eggs and commands make them.")
            .define("bogLurkerSpawns", true);

    static {
        B.pop().comment("The Marksman (a new one takes these; one already met keeps its own health and blow)").push("marksman");
    }

    private static final MarksmanRules.Tuning M = MarksmanRules.DEFAULTS;
    private static final ModConfigSpec.DoubleValue MARKSMAN_HEALTH = B.comment("The Marksman's health.")
            .defineInRange("marksmanHealth", (double) M.health(), 1.0, 1024.0);
    private static final ModConfigSpec.IntValue MARKSMAN_DRAW = B.comment("Ticks the Marksman draws in a crenel's gap before it looses.")
            .defineInRange("marksmanDrawTicks", M.drawTicks(), 5, 200);
    private static final ModConfigSpec.IntValue MARKSMAN_DRAW_TWO = B.comment("Ticks it draws below half its health.")
            .defineInRange("marksmanDrawTicksPhaseTwo", M.drawTicksPhaseTwo(), 5, 200);
    private static final ModConfigSpec.DoubleValue MARKSMAN_ARROW = B.comment("Its arrow's base damage (a skeleton's is 2.0; each of its shots is a Perfect one).")
            .defineInRange("marksmanArrowBaseDamage", M.arrowBaseDamage(), 0.0, 20.0);
    private static final ModConfigSpec.DoubleValue MARKSMAN_BASH = B.comment("The blow of its bow's end, for a foe on the battlements with it.")
            .defineInRange("marksmanBashDamage", M.bashDamage(), 0.0, 64.0);

    static {
        B.pop();
    }

    public static final ModConfigSpec SPEC = B.build();

    @Override
    public boolean vanillaCritOnFullDraw() {
        return SPEC.isLoaded() ? VANILLA_CRIT.get() : FullDrawConfig.DEFAULTS.vanillaCritOnFullDraw();
    }

    @Override
    public boolean blastArrowBreaksBlocks() {
        return SPEC.isLoaded() ? BLAST_BREAKS.get() : FullDrawConfig.DEFAULTS.blastArrowBreaksBlocks();
    }

    @Override
    public int ropeArrowLength() {
        return SPEC.isLoaded() ? ROPE_LENGTH.get() : FullDrawConfig.DEFAULTS.ropeArrowLength();
    }

    @Override
    public double arrowRecoveryChance() {
        return SPEC.isLoaded() ? RECOVERY.get() : FullDrawConfig.DEFAULTS.arrowRecoveryChance();
    }

    @Override
    public int arrowPickupLifetimeSeconds() {
        return SPEC.isLoaded() ? PICKUP_LIFETIME.get() : FullDrawConfig.DEFAULTS.arrowPickupLifetimeSeconds();
    }

    @Override
    public boolean galeHarrierSpawns() {
        return SPEC.isLoaded() ? HARRIER_SPAWNS.get() : FullDrawConfig.DEFAULTS.galeHarrierSpawns();
    }

    @Override
    public boolean bogLurkerSpawns() {
        return SPEC.isLoaded() ? LURKER_SPAWNS.get() : FullDrawConfig.DEFAULTS.bogLurkerSpawns();
    }

    @Override
    public MarksmanRules.Tuning marksman() {
        return SPEC.isLoaded() ? new MarksmanRules.Tuning(MARKSMAN_HEALTH.get().floatValue(), MARKSMAN_DRAW.get(), MARKSMAN_DRAW_TWO.get(), MARKSMAN_ARROW.get(),
                MARKSMAN_BASH.get()) : MarksmanRules.DEFAULTS;
    }

    @Override
    public boolean shieldbearerSpawns() {
        return SPEC.isLoaded() ? SHIELDBEARER_SPAWNS.get() : FullDrawConfig.DEFAULTS.shieldbearerSpawns();
    }

    @Override
    public ShotMath.Tuning tuning() {
        if (!SPEC.isLoaded()) return D;
        double start = TREMBLE_START.get();
        return new ShotMath.Tuning(WINDOW.get(), DELAY.get(), PERFECT.get(), PRECISION_MOBS.get(), PRECISION_PLAYERS.get(), CAP.get(),
                RANGE_START.get(), RANGE_PER_BLOCK.get(), RANGE_CAP.get(), POINT_BLANK.get(), POINT_BLANK_MULT.get(), start,
                FullDrawConfig.trembleFull(start, TREMBLE_FULL.get()), TREMBLE_MAX.get());
    }
}
