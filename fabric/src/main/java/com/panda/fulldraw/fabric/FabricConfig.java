package com.panda.fulldraw.fabric;

import com.panda.fulldraw.FullDrawConfig;
import com.panda.fulldraw.logic.ShotMath;

/**
 * The server's settings on Fabric: config/fulldraw.properties, read at startup and
 * rewritten with every key and comment. Clients on a server use the server's numbers.
 */
final class FabricConfig implements FullDrawConfig.Source {
    private final boolean vanillaCritOnFullDraw, blastArrowBreaksBlocks;
    private final ShotMath.Tuning tuning;
    private final int ropeArrowLength;

    private FabricConfig(boolean vanillaCritOnFullDraw, ShotMath.Tuning tuning, boolean blastArrowBreaksBlocks, int ropeArrowLength) {
        this.vanillaCritOnFullDraw = vanillaCritOnFullDraw;
        this.tuning = tuning;
        this.blastArrowBreaksBlocks = blastArrowBreaksBlocks;
        this.ropeArrowLength = ropeArrowLength;
    }

    static FabricConfig load() {
        PropertiesConfig c = new PropertiesConfig("fulldraw.properties");
        ShotMath.Tuning d = ShotMath.DEFAULTS;
        boolean crit = c.bool("vanillaCritOnFullDraw", false, "Players' full-draw bow shots crit as in vanilla; false means only a Perfect Release crits");
        int window = c.integer("perfectWindowTicks", d.perfectWindow(), 1, 20, "Ticks the Perfect window stays open (a weapon's own extra window adds to this)");
        int delay = c.integer("perfectDelayTicks", d.perfectDelay(), 0, 20, "Ticks after full draw the Perfect window opens");
        double perfect = c.decimal("perfectMultiplier", d.perfectMult(), 1.0, 5.0, "Damage multiplier of a Perfect Release");
        double mobs = c.decimal("precisionMultiplierMobs", d.precisionMobs(), 1.0, 5.0, "Head or weak-spot multiplier against mobs");
        double players = c.decimal("precisionMultiplierPlayers", d.precisionPlayers(), 1.0, 5.0, "Head multiplier against players");
        double cap = c.decimal("skillCap", d.skillCap(), 1.0, 10.0, "The most Perfect x precision x range may multiply a shot by");
        double rangeStart = c.decimal("rangeBonusStart", d.rangeStart(), 0.0, 256.0, "Blocks of flight before the range bonus starts");
        double perBlock = c.decimal("rangeBonusPerBlock", d.rangePerBlock(), 0.0, 1.0, "Range bonus per block beyond the start");
        double rangeCap = c.decimal("rangeBonusCap", d.rangeCap(), 1.0, 5.0, "The largest range multiplier");
        double pointBlank = c.decimal("pointBlankDistance", d.pointBlankDistance(), 0.0, 16.0, "Below this many blocks, a shot is point-blank");
        double pointBlankMult = c.decimal("pointBlankMultiplier", d.pointBlankMult(), 0.0, 1.0, "Damage multiplier of a point-blank shot");
        double start = c.decimal("trembleStartSeconds", d.trembleStartSeconds(), 0.0, 60.0, "Seconds past full draw the tremble starts");
        double full = c.decimal("trembleFullSeconds", d.trembleFullSeconds(), 0.1, 120.0, "Seconds past full draw the tremble is at its worst");
        double max = c.decimal("trembleMaxSpread", d.trembleMaxSpread(), 0.0, 20.0, "Spread at the worst of the tremble (vanilla's bow is 1.0)");
        boolean blast = c.bool("blastArrowBreaksBlocks", false, "Blast Arrows break blocks, as TNT does, where the tnt_explodes game rule allows (a mob's under mobGriefing); false means they only hurt and knock back");
        int rope = c.integer("ropeArrowLength", com.panda.fulldraw.logic.ArrowTraits.ROPE_LENGTH, 1, 64, "The most rope one Rope Arrow hangs, counting its knot");
        c.save("Full Draw: server settings. Clients on this server use these numbers.");
        return new FabricConfig(crit, new ShotMath.Tuning(window, delay, perfect, mobs, players, cap, rangeStart, perBlock, rangeCap, pointBlank,
                pointBlankMult, start, FullDrawConfig.trembleFull(start, full), max), blast, rope);
    }

    @Override
    public boolean blastArrowBreaksBlocks() {
        return blastArrowBreaksBlocks;
    }

    @Override
    public int ropeArrowLength() {
        return ropeArrowLength;
    }

    @Override
    public boolean vanillaCritOnFullDraw() {
        return vanillaCritOnFullDraw;
    }

    @Override
    public ShotMath.Tuning tuning() {
        return tuning;
    }
}
