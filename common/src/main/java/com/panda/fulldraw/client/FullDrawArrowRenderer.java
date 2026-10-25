package com.panda.fulldraw.client;

import com.panda.fulldraw.FullDrawMod;
import com.panda.fulldraw.registry.ModEntities;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

/** Vanilla's arrow model with the arrow's own texture ({@code textures/entity/projectiles/<id>.png}). */
public class FullDrawArrowRenderer<T extends AbstractArrow> extends ArrowRenderer<T, ArrowRenderState> {
    private final Identifier texture;

    public FullDrawArrowRenderer(EntityRendererProvider.Context context, Identifier texture) {
        super(context);
        this.texture = texture;
    }

    @Override
    protected Identifier getTextureLocation(ArrowRenderState state) {
        return texture;
    }

    @Override
    public ArrowRenderState createRenderState() {
        return new ArrowRenderState();
    }

    public static void register(ClientSinks.Renderers sink) {
        sink.register(ModEntities.BROADHEAD_ARROW.get(), context -> new FullDrawArrowRenderer<>(context, texture("broadhead_arrow")));
        sink.register(ModEntities.BODKIN_ARROW.get(), context -> new FullDrawArrowRenderer<>(context, texture("bodkin_arrow")));
        sink.register(ModEntities.BLUNT_ARROW.get(), context -> new FullDrawArrowRenderer<>(context, texture("blunt_arrow")));
    }

    private static Identifier texture(String id) {
        return FullDrawMod.id("textures/entity/projectiles/" + id + ".png");
    }
}
