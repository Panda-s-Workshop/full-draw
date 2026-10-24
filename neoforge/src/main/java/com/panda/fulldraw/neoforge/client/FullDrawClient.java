package com.panda.fulldraw.neoforge.client;

import com.panda.fulldraw.FullDrawMod;
import com.panda.fulldraw.client.DevExit;
import com.panda.fulldraw.client.FullDrawKeys;
import com.panda.fulldraw.client.ShotFeedback;
import com.panda.fulldraw.client.ShotHud;
import com.panda.fulldraw.network.FullDrawNetwork;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

/** The NeoForge client entry point: hands the common client registrations NeoForge's events. */
@Mod(value = FullDrawMod.MODID, dist = Dist.CLIENT)
public final class FullDrawClient {
    public FullDrawClient(IEventBus modBus, ModContainer container) {
        NeoForge.EVENT_BUS.addListener((GameShuttingDownEvent e) -> DevExit.onClientStopping());
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, NeoForgeClientConfig.SPEC);
        com.panda.fulldraw.FullDrawClientConfig.use(new NeoForgeClientConfig());
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) ->
                com.panda.fulldraw.FullDrawConfig.useRemote(null));
        FullDrawNetwork.toServer = payload -> {
            var connection = net.minecraft.client.Minecraft.getInstance().getConnection();
            if (connection != null && connection.hasChannel(payload.type())) ClientPacketDistributor.sendToServer(payload);
        };
        modBus.addListener((RegisterClientPayloadHandlersEvent e) -> {
            e.register(FullDrawNetwork.ShotResult.TYPE, (payload, ctx) -> ShotFeedback.onShotResult(payload));
            e.register(FullDrawNetwork.ReleaseResult.TYPE, (payload, ctx) -> ShotHud.onReleaseResult(payload));
            e.register(FullDrawNetwork.BreathState.TYPE, (payload, ctx) -> com.panda.fulldraw.client.FocusClient.onBreathState(payload));
            e.register(FullDrawNetwork.ServerTuning.TYPE, (payload, ctx) -> com.panda.fulldraw.FullDrawConfig.useRemote(payload.tuning()));
        });
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            KeyMapping.Category category = new KeyMapping.Category(FullDrawKeys.CATEGORY_ID);
            e.registerCategory(category);
            for (KeyMapping key : FullDrawKeys.create(category)) e.register(key);
        });
        modBus.addListener((RegisterGuiLayersEvent e) -> {
            e.registerAbove(VanillaGuiLayers.CROSSHAIR, ShotHud.RING_LAYER, ShotHud::drawRing);
            e.registerAbove(VanillaGuiLayers.HOTBAR, ShotHud.QUIVER_LAYER, ShotHud::drawQuiver);
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            FullDrawKeys.onClientTick();
            com.panda.fulldraw.client.FocusClient.tick();
            com.panda.fulldraw.client.DrawJuice.tick();
        });
    }
}
