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
        FUNCTIONS.register("steady_aim_halves_spread", () -> ShotGameTests::steadyAimHalvesSpread);
        FUNCTIONS.register("bow_bash_shoves", () -> ShotGameTests::bowBashShoves);
        FUNCTIONS.register("bow_bash_moves_players", () -> ShotGameTests::bowBashMovesPlayers);
        FUNCTIONS.register("server_tuning_codec_round_trips", () -> ShotGameTests::serverTuningCodecRoundTrips);
        FUNCTIONS.register("fulldraw_stats_reports", () -> ShotGameTests::statsReports);
        FUNCTIONS.register("fulldraw_stats_needs_no_permission", () -> ShotGameTests::statsNeedsNoPermission);
        FUNCTIONS.register("quiver_supplies_selected_arrow", () -> QuiverGameTests::quiverSuppliesSelectedArrow);
        FUNCTIONS.register("quiver_cycle_skips_empty", () -> QuiverGameTests::quiverCycleSkipsEmpty);
        FUNCTIONS.register("quiver_bundle_clicks", () -> QuiverGameTests::quiverBundleClicks);
        FUNCTIONS.register("blocked_hit_reports_nothing", () -> AdvancementGameTests::blockedHitReportsNothing);
        FUNCTIONS.register("breeze_deflection_drops_record", () -> AdvancementGameTests::breezeDeflectionDropsRecord);
        FUNCTIONS.register("slice_advancements", () -> AdvancementGameTests::sliceAdvancements);
        FUNCTIONS.register("recipes_loaded", () -> FullDrawGameTests::recipesLoaded);
    }

    private FullDrawGameTests() {}

    public static void register(IEventBus modBus) {
        FUNCTIONS.register(modBus);
        modBus.addListener(FullDrawGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(FullDrawMod.id("default"));
        for (var entry : FUNCTIONS.getEntries()) {
            ResourceKey<Consumer<GameTestHelper>> key = ResourceKey.create(Registries.TEST_FUNCTION, entry.getId());
            event.registerTest(key.identifier(), new FunctionGameTestInstance(key, new TestData<>(env, ARENA, 100, 0, true)));
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
        for (String id : List.of("shortbow", "quiver")) {
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
        for (String key : List.of("itemGroup.fulldraw")) {
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
