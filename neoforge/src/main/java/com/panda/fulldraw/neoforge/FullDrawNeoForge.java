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
        container.registerConfig(ModConfig.Type.SYNCED, NeoForgeConfig.SPEC);
        FullDrawConfig.use(new NeoForgeConfig());
        modBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Reloading e) -> {
            var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (e.getConfig().getSpec() == NeoForgeConfig.SPEC && server != null) server.execute(() -> FullDrawNetwork.onTuningChanged(server));
        });
        modBus.addListener((RegisterPayloadHandlersEvent e) -> {
            PayloadRegistrar r = e.registrar("1").executesOn(HandlerThread.MAIN).optional();
            r.playToServer(FullDrawNetwork.CycleQuiver.TYPE, FullDrawNetwork.CycleQuiver.STREAM_CODEC, (payload, ctx) -> {
                if (ctx.player() instanceof ServerPlayer player) FullDrawNetwork.handleCycleQuiver(payload, player);
            });
            r.playToClient(FullDrawNetwork.ShotResult.TYPE, FullDrawNetwork.ShotResult.STREAM_CODEC);
            r.playToClient(FullDrawNetwork.ReleaseResult.TYPE, FullDrawNetwork.ReleaseResult.STREAM_CODEC);
            r.playToClient(FullDrawNetwork.BreathState.TYPE, FullDrawNetwork.BreathState.STREAM_CODEC);
            r.playToClient(FullDrawNetwork.ServerTuning.TYPE, FullDrawNetwork.ServerTuning.STREAM_CODEC);
            r.playToClient(FullDrawNetwork.ArrowGravity.TYPE, FullDrawNetwork.ArrowGravity.STREAM_CODEC);
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
