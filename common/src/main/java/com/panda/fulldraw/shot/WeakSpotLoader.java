package com.panda.fulldraw.shot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.panda.fulldraw.FullDrawMod;
import com.panda.fulldraw.logic.WeakSpot;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;

/**
 * Loads {@code data/<ns>/fulldraw/weak_spot/<entity path>.json}: the file's id is the entity's id, so any mod
 * can describe its own mobs. Files for entities that aren't installed are skipped.
 */
public final class WeakSpotLoader extends SimpleJsonResourceReloadListener<WeakSpotLoader.Json> {
    public static final Identifier ID = FullDrawMod.id("weak_spots");

    /** {@code {"none": true}} or {@code {"boxes": [{"min": [x,y,z], "max": [x,y,z]}], "multiplier": 1.5}}. */
    public record Json(boolean none, List<WeakSpot.Box> boxes, double multiplier) {
        private static final Codec<WeakSpot.Box> BOX = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.listOf(3, 3).fieldOf("min").forGetter(b -> List.of(b.minX(), b.minY(), b.minZ())),
                Codec.DOUBLE.listOf(3, 3).fieldOf("max").forGetter(b -> List.of(b.maxX(), b.maxY(), b.maxZ()))
        ).apply(i, (min, max) -> new WeakSpot.Box(min.get(0), min.get(1), min.get(2), max.get(0), max.get(1), max.get(2))));

        public static final Codec<Json> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("none", false).forGetter(Json::none),
                BOX.listOf().optionalFieldOf("boxes", List.of()).forGetter(Json::boxes),
                Codec.DOUBLE.optionalFieldOf("multiplier", 0.0).forGetter(Json::multiplier)
        ).apply(i, Json::new));

        WeakSpot toWeakSpot() {
            return none ? WeakSpot.NONE : new WeakSpot(List.copyOf(boxes), multiplier);
        }
    }

    public WeakSpotLoader() {
        super(Json.CODEC, FileToIdConverter.json("fulldraw/weak_spot"));
    }

    @Override
    protected void apply(Map<Identifier, Json> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<EntityType<?>, WeakSpot> loaded = new HashMap<>();
        files.forEach((id, json) -> BuiltInRegistries.ENTITY_TYPE.getOptional(id).ifPresent(type -> loaded.put(type, json.toWeakSpot())));
        WeakSpots.set(loaded);
        FullDrawMod.LOGGER.info("Full Draw: {} weak-spot overrides ({} files)", loaded.size(), files.size());
    }
}
