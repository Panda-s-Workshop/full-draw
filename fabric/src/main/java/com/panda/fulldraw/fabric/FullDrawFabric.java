package com.panda.fulldraw.fabric;

import com.panda.fulldraw.FullDrawConfig;
import com.panda.fulldraw.FullDrawMod;
import com.panda.fulldraw.network.FullDrawNetwork;
import com.panda.fulldraw.platform.registry.DeferredRegister;
import com.panda.fulldraw.shot.WeakSpotLoader;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;

/** The Fabric entry point: registers the common content and wires the common hooks to Fabric API's events. */
public final class FullDrawFabric implements ModInitializer {
    /**
     * Registries whose entries others are made from, registered first in this order: items take blocks, components and
     * entity types (spawn eggs). The rest follow in the order the mod created them.
     */
    private static final List<ResourceKey<?>> FIRST = List.of(Registries.BLOCK, Registries.DATA_COMPONENT_TYPE, Registries.SOUND_EVENT,
            Registries.PARTICLE_TYPE, Registries.ENTITY_TYPE, Registries.BLOCK_ENTITY_TYPE, Registries.ITEM);

    @Override
    public void onInitialize() {
        FullDrawConfig.use(FabricConfig.load());
        FullDrawMod.init();
        DeferredRegister.all().stream()
                .sorted(Comparator.comparingInt(r -> {
                    int i = FIRST.indexOf(r.getRegistryKey());
                    return i < 0 ? FIRST.size() : i;
                }))
                .forEach(FullDrawFabric::registerAll);

        PayloadTypeRegistry.serverboundPlay().register(FullDrawNetwork.CycleQuiver.TYPE, FullDrawNetwork.CycleQuiver.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FullDrawNetwork.ShotResult.TYPE, FullDrawNetwork.ShotResult.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FullDrawNetwork.ReleaseResult.TYPE, FullDrawNetwork.ReleaseResult.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FullDrawNetwork.BreathState.TYPE, FullDrawNetwork.BreathState.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FullDrawNetwork.ServerTuning.TYPE, FullDrawNetwork.ServerTuning.STREAM_CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                FullDrawNetwork.onJoin(handler.getPlayer()));
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                com.panda.fulldraw.shot.Shots.forget(handler.getPlayer()));
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) ->
                com.panda.fulldraw.command.StatsCommand.register(dispatcher));
        ServerPlayNetworking.registerGlobalReceiver(FullDrawNetwork.CycleQuiver.TYPE, (payload, ctx) -> FullDrawNetwork.handleCycleQuiver(payload, ctx.player()));

        ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(WeakSpotLoader.ID, new WeakSpotLoader());
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server ->
                server.getPlayerList().getPlayers().forEach(com.panda.fulldraw.shot.Focus::tick));
    }

    @SuppressWarnings("unchecked")
    private static <R> void registerAll(DeferredRegister<R> register) {
        Registry<R> registry = (Registry<R>) BuiltInRegistries.REGISTRY.getValue(register.getRegistryKey().identifier());
        if (registry == null) throw new IllegalStateException("No registry " + register.getRegistryKey().identifier());
        register.registerAll((key, value) -> Registry.register(registry, key, value));
    }
}
