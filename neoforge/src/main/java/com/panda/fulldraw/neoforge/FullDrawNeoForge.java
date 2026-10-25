package com.panda.fulldraw.neoforge;

import com.panda.fulldraw.FullDrawConfig;
import com.panda.fulldraw.FullDrawMod;
import com.panda.fulldraw.network.FullDrawNetwork;
import com.panda.fulldraw.platform.registry.DeferredRegister;
import com.panda.fulldraw.shot.WeakSpotLoader;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.registries.RegisterEvent;

/** The NeoForge entry point: registers the common content and wires the common hooks to NeoForge's events. */
@Mod(FullDrawMod.MODID)
public final class FullDrawNeoForge {
    public FullDrawNeoForge(IEventBus modBus, ModContainer container) {
        FullDrawMod.init();
        modBus.addListener((RegisterEvent e) -> DeferredRegister.all().forEach(register -> registerInto(e, register)));
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent e) -> e.enqueueWork(FullDrawMod::setup));
        modBus.addListener((net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent e) -> com.panda.fulldraw.registry.ModEntities.attributes(e::put));
        modBus.addListener((net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent e) ->
                com.panda.fulldraw.registry.ModEntities.spawnRules(new com.panda.fulldraw.registry.ModEntities.SpawnRules() {
                    @Override
                    public <T extends net.minecraft.world.entity.Mob> void set(net.minecraft.world.entity.EntityType<T> type,
                                                                               net.minecraft.world.entity.SpawnPlacementType placement,
                                                                               net.minecraft.world.level.levelgen.Heightmap.Types heightmap,
                                                                               net.minecraft.world.entity.SpawnPlacements.SpawnPredicate<T> predicate) {
                        e.register(type, placement, heightmap, predicate, net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.REPLACE);
                    }
                }));
        // the Shieldbearer's egg also sits beside the pillager's in vanilla's Spawn Eggs tab, the Gale Harrier's beside the phantom's
        modBus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == net.minecraft.world.item.CreativeModeTabs.SPAWN_EGGS) {
                e.insertAfter(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PILLAGER_SPAWN_EGG),
                        new net.minecraft.world.item.ItemStack(com.panda.fulldraw.registry.ModItems.SHIELDBEARER_SPAWN_EGG.get()),
                        net.minecraft.world.item.CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
                e.insertAfter(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PHANTOM_SPAWN_EGG),
                        new net.minecraft.world.item.ItemStack(com.panda.fulldraw.registry.ModItems.GALE_HARRIER_SPAWN_EGG.get()),
                        net.minecraft.world.item.CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            }
        });
        container.registerConfig(ModConfig.Type.SYNCED, NeoForgeConfig.SPEC);
        FullDrawConfig.use(new NeoForgeConfig());
        modBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Reloading e) -> {
            var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (e.getConfig().getSpec() == NeoForgeConfig.SPEC && server != null) server.execute(() -> FullDrawNetwork.onTuningChanged(server));
        });
        modBus.addListener((RegisterPayloadHandlersEvent e) -> {
            PayloadRegistrar r = e.registrar("3").executesOn(HandlerThread.MAIN).optional();
            r.playToServer(FullDrawNetwork.CycleQuiver.TYPE, FullDrawNetwork.CycleQuiver.STREAM_CODEC, (payload, ctx) -> {
                if (ctx.player() instanceof ServerPlayer player) FullDrawNetwork.handleCycleQuiver(payload, player);
            });
            r.playToClient(FullDrawNetwork.ShotResult.TYPE, FullDrawNetwork.ShotResult.STREAM_CODEC);
            r.playToClient(FullDrawNetwork.ReleaseResult.TYPE, FullDrawNetwork.ReleaseResult.STREAM_CODEC);
            r.playToClient(FullDrawNetwork.BreathState.TYPE, FullDrawNetwork.BreathState.STREAM_CODEC);
            r.playToClient(FullDrawNetwork.ServerTuning.TYPE, FullDrawNetwork.ServerTuning.STREAM_CODEC);
            r.playToClient(FullDrawNetwork.ArrowGravity.TYPE, FullDrawNetwork.ArrowGravity.STREAM_CODEC);
            r.playToClient(FullDrawNetwork.FuseOut.TYPE, FullDrawNetwork.FuseOut.STREAM_CODEC);
        });

        IEventBus game = NeoForge.EVENT_BUS;
        game.addListener((AddServerReloadListenersEvent e) -> e.addListener(WeakSpotLoader.ID, new WeakSpotLoader()));
        game.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) FullDrawNetwork.onJoin(player);
        });
        game.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) ->
                com.panda.fulldraw.shot.Shots.forget(e.getEntity()));
        game.addListener((net.neoforged.neoforge.event.RegisterCommandsEvent e) -> com.panda.fulldraw.command.StatsCommand.register(e.getDispatcher()));
        game.addListener((net.neoforged.neoforge.event.tick.PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer player) com.panda.fulldraw.shot.Focus.tick(player);
        });

        com.panda.fulldraw.gametest.FullDrawGameTests.register(modBus);
    }

    private static <R> void registerInto(RegisterEvent event, DeferredRegister<R> register) {
        event.register(register.getRegistryKey(), helper -> register.registerAll(helper::register));
    }
}
