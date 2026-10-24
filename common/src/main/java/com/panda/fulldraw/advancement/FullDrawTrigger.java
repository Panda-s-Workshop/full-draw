package com.panda.fulldraw.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/** One criterion trigger for every Full Draw milestone: {@code {"event": "long_shot", "value": {"min": 50}}}. */
public class FullDrawTrigger extends SimpleCriterionTrigger<FullDrawTrigger.Instance> {
    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    public void trigger(ServerPlayer player, String event, int value) {
        trigger(player, instance -> instance.matches(event, value));
    }

    public void trigger(ServerPlayer player, String event) {
        trigger(player, event, 1);
    }

    public record Instance(Optional<Holder<LootItemCondition>> player, String event, MinMaxBounds.Ints value)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                LootItemCondition.CODEC.optionalFieldOf("player").forGetter(Instance::player),
                Codec.STRING.fieldOf("event").forGetter(Instance::event),
                MinMaxBounds.Ints.CODEC.optionalFieldOf("value", MinMaxBounds.Ints.ANY).forGetter(Instance::value)
        ).apply(i, Instance::new));

        public boolean matches(String event, int value) {
            return this.event.equals(event) && this.value.matches(value);
        }
    }
}
