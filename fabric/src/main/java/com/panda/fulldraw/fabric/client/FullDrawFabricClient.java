package com.panda.fulldraw.fabric.client;

import com.panda.fulldraw.client.DevExit;
import com.panda.fulldraw.client.FullDrawKeys;
import com.panda.fulldraw.client.ShotFeedback;
import com.panda.fulldraw.client.ShotHud;
import com.panda.fulldraw.network.FullDrawNetwork;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;

/** The Fabric client entry point: hands the common client registrations to Fabric API's registries and events. */
public final class FullDrawFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> DevExit.onClientStopping());
        com.panda.fulldraw.FullDrawClientConfig.use(FabricClientConfig.load());
        FullDrawNetwork.toServer = payload -> {
            if (ClientPlayNetworking.canSend(payload.type())) ClientPlayNetworking.send(payload);
        };
        ClientPlayNetworking.registerGlobalReceiver(FullDrawNetwork.ShotResult.TYPE, (payload, ctx) -> ShotFeedback.onShotResult(payload));
        ClientPlayNetworking.registerGlobalReceiver(FullDrawNetwork.ReleaseResult.TYPE, (payload, ctx) -> ShotHud.onReleaseResult(payload));
        ClientPlayNetworking.registerGlobalReceiver(FullDrawNetwork.BreathState.TYPE, (payload, ctx) -> com.panda.fulldraw.client.FocusClient.onBreathState(payload));
        ClientPlayNetworking.registerGlobalReceiver(FullDrawNetwork.ServerTuning.TYPE, (payload, ctx) -> com.panda.fulldraw.FullDrawConfig.useRemote(payload.tuning()));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) ->
                com.panda.fulldraw.FullDrawConfig.useRemote(null));
        for (KeyMapping key : FullDrawKeys.create(KeyMapping.Category.register(FullDrawKeys.CATEGORY_ID))) KeyMappingHelper.registerKeyMapping(key);
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            FullDrawKeys.onClientTick();
            com.panda.fulldraw.client.FocusClient.tick();
            com.panda.fulldraw.client.DrawJuice.tick();
        });
        HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, ShotHud.RING_LAYER, ShotHud::drawRing);
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, ShotHud.QUIVER_LAYER, ShotHud::drawQuiver);
    }
}
