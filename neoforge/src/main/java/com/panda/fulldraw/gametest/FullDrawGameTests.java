package com.panda.fulldraw.gametest;

import com.panda.fulldraw.FullDrawMod;
import com.panda.fulldraw.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registration, data and behaviour checks that run on a GameTest server. */
public final class FullDrawGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, FullDrawMod.MODID);
    private static final Identifier ARENA = FullDrawMod.id("test_arena");

    static {
        FUNCTIONS.register("mod_loads", () -> FullDrawGameTests::modLoads);
        FUNCTIONS.register("lang_coverage", () -> FullDrawGameTests::langCoverage);
        // the shot pipeline
        FUNCTIONS.register("perfect_release_crits", () -> ShotGameTests::perfectReleaseCrits);
        FUNCTIONS.register("headshot_and_long_shot", () -> ShotGameTests::headshotAndLongShot);
        FUNCTIONS.register("weak_spot_data_loads", () -> ShotGameTests::weakSpotDataLoads);
        FUNCTIONS.register("weapon_stats_shortbow", () -> ShotGameTests::weaponStatsShortbow);
        FUNCTIONS.register("weapon_stats_longbow", () -> ShotGameTests::weaponStatsLongbow);
        FUNCTIONS.register("weapon_stats_recurve_bow", () -> ShotGameTests::weaponStatsRecurveBow);
        FUNCTIONS.register("weapon_stats_arbalest", () -> CrossbowGameTests::weaponStatsArbalest);
        FUNCTIONS.register("arbalest_spread", () -> CrossbowGameTests::arbalestSpread);
        FUNCTIONS.register("arbalest_pierces_three", () -> CrossbowGameTests::arbalestPiercesThree);
        FUNCTIONS.register("arbalest_halves_armour", () -> CrossbowGameTests::arbalestHalvesArmour);
        FUNCTIONS.register("arbalest_knocks_back", () -> CrossbowGameTests::arbalestKnocksBack);
        FUNCTIONS.register("arbalest_breaks_shield_block", () -> CrossbowGameTests::arbalestBreaksShieldBlock);
        FUNCTIONS.register("weapon_stats_repeating_crossbow", () -> CrossbowGameTests::weaponStatsRepeatingCrossbow);
        FUNCTIONS.register("repeater_magazine_five", () -> CrossbowGameTests::repeaterMagazineFive);
        FUNCTIONS.register("repeater_burst_lands", () -> CrossbowGameTests::repeaterBurstLands);
        FUNCTIONS.register("repeater_break_drops_ammo", () -> CrossbowGameTests::repeaterBreakDropsAmmo);
        FUNCTIONS.register("repeater_loads_blast_alone", () -> CrossbowGameTests::repeaterLoadsBlastAlone);
        FUNCTIONS.register("crank_sounds", () -> CrossbowGameTests::crankSounds);
        FUNCTIONS.register("steady_aim_halves_spread", () -> ShotGameTests::steadyAimHalvesSpread);
        FUNCTIONS.register("bow_bash_shoves", () -> ShotGameTests::bowBashShoves);
        FUNCTIONS.register("bow_bash_moves_players", () -> ShotGameTests::bowBashMovesPlayers);
        FUNCTIONS.register("server_tuning_codec_round_trips", () -> ShotGameTests::serverTuningCodecRoundTrips);
        FUNCTIONS.register("fulldraw_stats_reports", () -> ShotGameTests::statsReports);
        FUNCTIONS.register("fulldraw_stats_needs_no_permission", () -> ShotGameTests::statsNeedsNoPermission);
        FUNCTIONS.register("quiver_supplies_selected_arrow", () -> QuiverGameTests::quiverSuppliesSelectedArrow);
        FUNCTIONS.register("quiver_cycle_skips_empty", () -> QuiverGameTests::quiverCycleSkipsEmpty);
        FUNCTIONS.register("quiver_supplies_new_arrows", () -> QuiverGameTests::quiverSuppliesNewArrows);
        FUNCTIONS.register("quiver_bundle_clicks", () -> QuiverGameTests::quiverBundleClicks);
        FUNCTIONS.register("quiver_only_for_weapons_firing_as_vanilla", () -> QuiverGameTests::quiverOnlyForWeaponsFiringAsVanilla);
        FUNCTIONS.register("blocked_hit_reports_nothing", () -> AdvancementGameTests::blockedHitReportsNothing);
        FUNCTIONS.register("breeze_deflection_drops_record", () -> AdvancementGameTests::breezeDeflectionDropsRecord);
        FUNCTIONS.register("slice_advancements", () -> AdvancementGameTests::sliceAdvancements);
        FUNCTIONS.register("long_shot_advancement", () -> AdvancementGameTests::longShotAdvancement);
        FUNCTIONS.register("long_shot_through_a_gateway", () -> AdvancementGameTests::longShotThroughAGateway);
        FUNCTIONS.register("duel_at_dusk_advancement", () -> AdvancementGameTests::duelAtDuskAdvancement);
        FUNCTIONS.register("duel_at_dusk_blast", () -> AdvancementGameTests::duelAtDuskBlast);
        FUNCTIONS.register("recipes_loaded", () -> FullDrawGameTests::recipesLoaded);
        FUNCTIONS.register("arrows_fire_from_bow_and_dispenser", () -> ArrowGameTests::arrowsFireFromBowAndDispenser);
        FUNCTIONS.register("broadhead_bleeds", () -> ArrowGameTests::broadheadBleeds);
        FUNCTIONS.register("bodkin_vs_armour", () -> ArrowGameTests::bodkinVsArmour);
        FUNCTIONS.register("blunt_cancels_creeper", () -> ArrowGameTests::bluntCancelsCreeper);
        FUNCTIONS.register("arrow_heads_in_the_pipeline", () -> ArrowGameTests::arrowHeadsInThePipeline);
        FUNCTIONS.register("blunt_interrupts_archer", () -> ArrowGameTests::bluntInterruptsArcher);
        FUNCTIONS.register("blunt_breaks_off_tells", () -> ArrowGameTests::bluntBreaksOffTells);
        FUNCTIONS.register("rope_arrow_places_rope", () -> ArrowGameTests::ropeArrowPlacesRope);
        FUNCTIONS.register("rope_comes_down_together", () -> ArrowGameTests::ropeComesDownTogether);
        FUNCTIONS.register("rope_arrow_refusals", () -> ArrowGameTests::ropeArrowRefusals);
        FUNCTIONS.register("flare_lights_and_glows", () -> ArrowGameTests::flareLightsAndGlows);
        FUNCTIONS.register("blast_arrow_explodes", () -> ArrowGameTests::blastArrowExplodes);
        FUNCTIONS.register("blast_hit_adds_copies_plain", () -> ArrowGameTests::blastHitAddsAndCopiesArePlain);
        FUNCTIONS.register("flare_burns_above_cover", () -> ArrowGameTests::flareBurnsAboveCover);
        FUNCTIONS.register("flare_adventure_and_mob_flares", () -> ArrowGameTests::flareAdventureAndMobFlares);
        FUNCTIONS.register("fletching_recipes_load", () -> FletchingGameTests::fletchingRecipesLoad);
        FUNCTIONS.register("fletching_menu_crafts_eight", () -> FletchingGameTests::fletchingMenuCraftsEight);
        FUNCTIONS.register("fletching_menu_edges", () -> FletchingGameTests::fletchingMenuEdges);
        FUNCTIONS.register("fletcher_trades", () -> FletchingGameTests::fletcherTrades);
        FUNCTIONS.register("arrows_recovered_on_kill", () -> ArrowGameTests::arrowsRecoveredOnKill);
        FUNCTIONS.register("arrow_pickup_lifetime", () -> ArrowGameTests::arrowPickupLifetime);
        // the Shieldbearer
        FUNCTIONS.register("shieldbearer_blocks_front", () -> ShieldbearerGameTests::shieldbearerBlocksFront);
        FUNCTIONS.register("shieldbearer_hit_from_side", () -> ShieldbearerGameTests::shieldbearerHitFromSide);
        FUNCTIONS.register("shieldbearer_blunt_stagger", () -> ShieldbearerGameTests::shieldbearerBluntStagger);
        FUNCTIONS.register("shieldbearer_bolt_cracks", () -> ShieldbearerGameTests::shieldbearerBoltCracks);
        FUNCTIONS.register("shieldbearer_bashes", () -> ShieldbearerGameTests::shieldbearerBashes);
        FUNCTIONS.register("shieldbearer_dazed_while_down", () -> ShieldbearerGameTests::shieldbearerDazedWhileDown);
        FUNCTIONS.register("shieldbearer_turns_slowly_in_a_fight", () -> ShieldbearerGameTests::shieldbearerTurnsSlowlyInAFight);
        FUNCTIONS.register("shieldbearer_shield_saves", () -> ShieldbearerGameTests::shieldbearerShieldSaves);
        FUNCTIONS.register("shieldbearers_join_raid_waves", () -> ShieldbearerGameTests::shieldbearersJoinRaidWaves);
        FUNCTIONS.register("shieldbearer_outpost_spawns", () -> ShieldbearerGameTests::shieldbearerOutpostSpawns);
        // the Gale Harrier
        FUNCTIONS.register("harrier_grounded_by_dive_headshot", () -> HarrierGameTests::harrierGroundedByDiveHeadshot);
        FUNCTIONS.register("harrier_steep_dive_headshot", () -> HarrierGameTests::harrierSteepDiveHeadshot);
        FUNCTIONS.register("harrier_perfect_grounds_only_in_dive", () -> HarrierGameTests::harrierPerfectGroundsOnlyInDive);
        FUNCTIONS.register("harrier_dive_strikes_and_climbs", () -> HarrierGameTests::harrierDiveStrikesAndClimbs);
        FUNCTIONS.register("harrier_grounded_takes_double_melee", () -> HarrierGameTests::harrierGroundedTakesDoubleMelee);
        FUNCTIONS.register("harrier_grounded_counts_from_landing", () -> HarrierGameTests::harrierGroundedCountsFromLanding);
        FUNCTIONS.register("harrier_saves", () -> HarrierGameTests::harrierSaves);
        FUNCTIONS.register("harrier_spawn_rules", () -> HarrierGameTests::harrierSpawnRules);
        FUNCTIONS.register("harrier_drops", () -> HarrierGameTests::harrierDrops);
        FUNCTIONS.register("harrier_drops_with_looting", () -> HarrierGameTests::harrierDropsWithLooting);
        FUNCTIONS.register("harrier_parts_craft", () -> HarrierGameTests::harrierPartsCraft);
        FUNCTIONS.register("bog_lurker_eye_weak_spot", () -> LurkerGameTests::bogLurkerEyeWeakSpot);
        FUNCTIONS.register("bog_lurker_body_through_water", () -> LurkerGameTests::bogLurkerBodyThroughWater);
        FUNCTIONS.register("bog_lurker_spits_in_range", () -> LurkerGameTests::bogLurkerSpitsInRange);
        FUNCTIONS.register("bog_lurker_holds_fire_too_close", () -> LurkerGameTests::bogLurkerHoldsFireTooClose);
        FUNCTIONS.register("bog_lurker_flees_close", () -> LurkerGameTests::bogLurkerFleesClose);
        FUNCTIONS.register("bog_lurker_dives_when_struck_from_afar", () -> LurkerGameTests::bogLurkerDivesWhenStruckFromAfar);
        FUNCTIONS.register("watchtower_places", () -> WatchtowerGameTests::watchtowerPlaces);
        // the Marksman
        FUNCTIONS.register("marksman_cover_only_precision", () -> MarksmanGameTests::marksmanCoverOnlyPrecision);
        FUNCTIONS.register("marksman_draws_perfect_shots", () -> MarksmanGameTests::marksmanDrawsPerfectShots);
        FUNCTIONS.register("marksman_loses_the_shot_to_a_blunt", () -> MarksmanGameTests::marksmanLosesTheShotToABlunt);
        FUNCTIONS.register("marksman_phase_two", () -> MarksmanGameTests::marksmanPhaseTwo);
        FUNCTIONS.register("marksman_boss_bar_leaves", () -> MarksmanGameTests::marksmanBossBarLeaves);
        FUNCTIONS.register("marksman_on_the_watchtower", () -> MarksmanGameTests::marksmanOnTheWatchtower);
        FUNCTIONS.register("marksman_shoots_from_the_gap", () -> MarksmanGameTests::marksmanShootsFromTheGap);
        FUNCTIONS.register("marksman_shoots_back_at_an_unseen_attacker", () -> MarksmanGameTests::marksmanShootsBackAtAnUnseenAttacker);
        FUNCTIONS.register("marksman_back_to_post", () -> MarksmanGameTests::marksmanBackToPost);
        FUNCTIONS.register("marksman_bashes_a_foe_beside_it", () -> MarksmanGameTests::marksmanBashesAFoeBesideIt);
        FUNCTIONS.register("marksman_drops", () -> MarksmanGameTests::marksmanDrops);
        FUNCTIONS.register("marksman_kept_on_peaceful", () -> MarksmanGameTests::marksmanKeptOnPeaceful);
        FUNCTIONS.register("marksman_follows_the_config", () -> MarksmanGameTests::marksmanFollowsTheConfig);
        // the enchantments
        FUNCTIONS.register("enchantments_registered", () -> EnchantmentGameTests::enchantmentsRegistered);
        FUNCTIONS.register("quickdraw_shortens_draw", () -> EnchantmentGameTests::quickdrawShortensDraw);
        FUNCTIONS.register("steady_widens_window", () -> EnchantmentGameTests::steadyWidensWindow);
        FUNCTIONS.register("deadeye_raises_precision", () -> EnchantmentGameTests::deadeyeRaisesPrecision);
        FUNCTIONS.register("ricochet_bounces", () -> EnchantmentGameTests::ricochetBounces);
        FUNCTIONS.register("eye_books_craft", () -> EnchantmentGameTests::eyeBooksCraft);
        FUNCTIONS.register("anvil_keeps_eye_tiers", () -> EnchantmentGameTests::anvilKeepsEyeTiers);
        FUNCTIONS.register("bog_lurker_saves", () -> LurkerGameTests::bogLurkerSaves);
        FUNCTIONS.register("bog_lurker_spawn_rules", () -> LurkerGameTests::bogLurkerSpawnRules);
        FUNCTIONS.register("bog_lurker_drops", () -> LurkerGameTests::bogLurkerDrops);
    }

    /**
     * Tests that swap the server config for longer than one tick, or count a mob across the whole test grid (another
     * test's mobs would count too).
     */
    private static final java.util.Set<String> ALONE = java.util.Set.of("blast_arrow_explodes", "arrows_recovered_on_kill", "arrow_pickup_lifetime",
            "harrier_spawn_rules", "bog_lurker_spawn_rules", "marksman_kept_on_peaceful", "marksman_follows_the_config");
    /** Tests that need the open sky over their arena (every other test gets a barrier roof). */
    private static final java.util.Set<String> SKY = java.util.Set.of("harrier_spawn_rules", "bog_lurker_spawn_rules", "harrier_steep_dive_headshot",
            "harrier_dive_strikes_and_climbs");
    /** Tests that watch a mob through more than one round of what it does, or wait on the light: 10 s instead of 5. */
    private static final java.util.Set<String> LONG = java.util.Set.of("bog_lurker_spits_in_range", "bog_lurker_flees_close", "bog_lurker_dives_when_struck_from_afar",
            "bog_lurker_spawn_rules", "harrier_spawn_rules", "marksman_draws_perfect_shots", "marksman_loses_the_shot_to_a_blunt", "marksman_phase_two", "marksman_on_the_watchtower",
            "marksman_shoots_from_the_gap", "marksman_shoots_back_at_an_unseen_attacker", "marksman_back_to_post", "marksman_follows_the_config");

    private FullDrawGameTests() {}

    public static void register(IEventBus modBus) {
        FUNCTIONS.register(modBus);
        modBus.addListener(FullDrawGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(FullDrawMod.id("default"));
        for (var entry : FUNCTIONS.getEntries()) {
            ResourceKey<Consumer<GameTestHelper>> key = ResourceKey.create(Registries.TEST_FUNCTION, entry.getId());
            String name = entry.getId().getPath();
            // a test that holds a changed server config over several ticks runs in a batch of its own: batches run one
            // after another, so no other test sees its config, and no other test puts the config back under it; one
            // that counts a mob about it runs alone so that no other test's mobs are about
            Holder<TestEnvironmentDefinition<?>> own = ALONE.contains(name) ? event.registerEnvironment(FullDrawMod.id("alone_" + name)) : env;
            event.registerTest(key.identifier(), new FunctionGameTestInstance(key, new TestData<>(own, net.minecraft.world.level.Level.OVERWORLD, ARENA,
                    LONG.contains(name) ? 200 : 100, 0, true, net.minecraft.world.level.block.Rotation.NONE, false, 1, 1, SKY.contains(name), 0)));
        }
    }

    // ---- tests -------------------------------------------------------------------------------------------------

    /** The mod's registries are reachable on a server: its creative tab is registered. */
    private static void modLoads(GameTestHelper helper) {
        if (BuiltInRegistries.CREATIVE_MODE_TAB.getOptional(FullDrawMod.id("main")).isEmpty()) helper.fail("creative tab fulldraw:main missing");
        helper.succeed();
    }

    /** Every recipe the generator writes is loaded. */
    private static void recipesLoaded(GameTestHelper helper) {
        var recipes = helper.getLevel().getServer().getRecipeManager();
        List<String> missing = new ArrayList<>();
        for (String id : List.of("shortbow", "longbow", "recurve_bow", "arbalest", "repeating_crossbow", "quiver")) {
            if (recipes.byKey(ResourceKey.create(Registries.RECIPE, FullDrawMod.id(id))).isEmpty()) missing.add(id);
            // and the recipe-book unlock, or the recipe never shows in the book
            if (helper.getLevel().getServer().getAdvancements().get(FullDrawMod.id("recipes/combat/" + id)) == null) missing.add("unlock " + id);
        }
        if (!missing.isEmpty()) helper.fail("recipes not loaded: " + missing);
        helper.succeed();
    }

    /** Every registered name and every advancement text has an English string. */
    private static void langCoverage(GameTestHelper helper) {
        var lang = net.minecraft.locale.Language.getInstance();
        List<String> missing = new ArrayList<>();
        ModItems.ITEMS.getEntries().forEach(h -> {
            String key = h.get().getDescriptionId();
            if (!lang.has(key)) missing.add(key);
        });
        com.panda.fulldraw.registry.ModEntities.ENTITIES.getEntries().forEach(h -> {
            String key = h.get().getDescriptionId();
            if (!lang.has(key)) missing.add(key);
        });
        com.panda.fulldraw.registry.ModEffects.EFFECTS.getEntries().forEach(h -> {
            String key = h.get().getDescriptionId();
            if (!lang.has(key)) missing.add(key);
        });
        for (String key : List.of("itemGroup.fulldraw", "death.attack.fulldraw.bleeding", "death.attack.fulldraw.bleeding.player")) {
            if (!lang.has(key)) missing.add(key);
        }
        var enchantments = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        enchantments.listElements().forEach(ref -> {
            if (!ref.key().identifier().getNamespace().equals(FullDrawMod.MODID)) return;
            String key = ref.key().identifier().toLanguageKey("enchantment");
            if (!lang.has(key)) missing.add(key);
        });
        MinecraftServer server = helper.getLevel().getServer();
        server.getAdvancements().getAllAdvancements().forEach(holder -> {
            if (!holder.id().getNamespace().equals(FullDrawMod.MODID)) return;
            holder.value().display().ifPresent(d -> {
                for (Component c : List.of(d.title(), d.description())) {
                    if (c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && !lang.has(t.getKey())) missing.add(t.getKey());
                }
            });
        });
        if (!missing.isEmpty()) helper.fail("missing lang keys: " + missing);
        helper.succeed();
    }
}
