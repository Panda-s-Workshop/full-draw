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
        net.minecraft.client.gui.screens.MenuScreens.register(com.panda.fulldraw.registry.ModMenus.FLETCHING.get(), com.panda.fulldraw.client.FletchingScreen::new);
        var renderers = new com.panda.fulldraw.client.ClientSinks.Renderers() {
            @Override
            public <E extends net.minecraft.world.entity.Entity> void register(net.minecraft.world.entity.EntityType<? extends E> type,
                                                                               net.minecraft.client.renderer.entity.EntityRendererProvider<E> provider) {
                net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(type, provider);
            }
        };
        com.panda.fulldraw.client.FullDrawArrowRenderer.register(renderers);
        com.panda.fulldraw.client.ShieldbearerRenderer.registerRenderers(renderers);
        com.panda.fulldraw.client.ShieldbearerRenderer.registerLayers((layer, definition) ->
                net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry.registerModelLayer(layer, definition::get));
        com.panda.fulldraw.client.GaleHarrierRenderer.registerRenderers(renderers);
        com.panda.fulldraw.client.GaleHarrierRenderer.registerLayers((layer, definition) ->
                net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry.registerModelLayer(layer, definition::get));
        com.panda.fulldraw.client.BogLurkerRenderer.registerRenderers(renderers);
        com.panda.fulldraw.client.BogLurkerRenderer.registerLayers((layer, definition) ->
                net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry.registerModelLayer(layer, definition::get));
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> DevExit.onClientStopping());
        com.panda.fulldraw.FullDrawClientConfig.use(FabricClientConfig.load());
        // Fabric API has no hook for item model properties; vanilla's registry is open until the models load
        net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties.ID_MAPPER
                .put(com.panda.fulldraw.FullDrawMod.id("magazine"), com.panda.fulldraw.client.MagazineShots.MAP_CODEC);
        FullDrawNetwork.toServer = payload -> {
            if (ClientPlayNetworking.canSend(payload.type())) ClientPlayNetworking.send(payload);
        };
        ClientPlayNetworking.registerGlobalReceiver(FullDrawNetwork.ShotResult.TYPE, (payload, ctx) -> ShotFeedback.onShotResult(payload));
        ClientPlayNetworking.registerGlobalReceiver(FullDrawNetwork.ReleaseResult.TYPE, (payload, ctx) -> ShotHud.onReleaseResult(payload));
        ClientPlayNetworking.registerGlobalReceiver(FullDrawNetwork.BreathState.TYPE, (payload, ctx) -> com.panda.fulldraw.client.FocusClient.onBreathState(payload));
        ClientPlayNetworking.registerGlobalReceiver(FullDrawNetwork.ServerTuning.TYPE, (payload, ctx) -> com.panda.fulldraw.FullDrawConfig.useRemote(payload.tuning(), payload.ropeLength(), payload.recoveryChance()));
        ClientPlayNetworking.registerGlobalReceiver(FullDrawNetwork.ArrowGravity.TYPE, (payload, ctx) -> ShotFeedback.onArrowGravity(payload));
        ClientPlayNetworking.registerGlobalReceiver(FullDrawNetwork.FuseOut.TYPE, (payload, ctx) -> ShotFeedback.onFuseOut(payload));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) ->
                com.panda.fulldraw.FullDrawConfig.useRemote(null, 0, Double.NaN));
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
