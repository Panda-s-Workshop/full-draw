package com.panda.fulldraw.client;

import java.util.function.Supplier;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/** What client registration hands its content to. Each loader's client entry adapts these to its own events. */
public final class ClientSinks {
    private ClientSinks() {}

    public interface Renderers {
        <E extends Entity> void register(EntityType<? extends E> type, EntityRendererProvider<E> provider);
    }

    public interface Layers {
        void register(ModelLayerLocation layer, Supplier<LayerDefinition> definition);
    }

    public interface Particles {
        <T extends ParticleOptions> void sprites(ParticleType<T> type, SpriteProvider<T> provider);
    }

    /** A particle provider made from the type's sprite set (vanilla's own interface for this is package-private). */
    public interface SpriteProvider<T extends ParticleOptions> {
        ParticleProvider<T> create(SpriteSet sprites);
    }
}
