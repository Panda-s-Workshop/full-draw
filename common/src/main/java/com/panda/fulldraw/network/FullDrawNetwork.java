package com.panda.fulldraw.network;

import com.panda.fulldraw.FullDrawMod;
import com.panda.fulldraw.logic.ShotMath;
import com.panda.fulldraw.platform.Services;
import com.panda.fulldraw.quiver.Quivers;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * The mod's payloads. Each loader registers them (NeoForge's payload registrar, Fabric's PayloadTypeRegistry) and
 * routes them to the handlers here; the client's handlers live in the client package.
 */
public final class FullDrawNetwork {
    /** Client → server: the cycle key was pressed. */
    public record CycleQuiver(int direction) implements CustomPacketPayload {
        public static final Type<CycleQuiver> TYPE = new Type<>(FullDrawMod.id("cycle_quiver"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CycleQuiver> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, CycleQuiver::direction, CycleQuiver::new);

        @Override
        public Type<CycleQuiver> type() {
            return TYPE;
        }
    }

    /**
     * Server → the shooter: one of their arrows hit, so the client can play the hit tick and
     * the precision sound and show the distance. The server already applied the damage.
     */
    public record ShotResult(boolean perfect, boolean precision, float distance, float damage) implements CustomPacketPayload {
        public static final Type<ShotResult> TYPE = new Type<>(FullDrawMod.id("shot_result"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ShotResult> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, ShotResult::perfect, ByteBufCodecs.BOOL, ShotResult::precision,
                ByteBufCodecs.FLOAT, ShotResult::distance, ByteBufCodecs.FLOAT, ShotResult::damage, ShotResult::new);

        @Override
        public Type<ShotResult> type() {
            return TYPE;
        }
    }

    /** Server → the shooter, once per full-draw release: the server's verdict on the timing, for the text under the ring. */
    public record ReleaseResult(int verdict) implements CustomPacketPayload {
        public static final int PERFECT = 0, EARLY = 1, LATE = 2;
        public static final Type<ReleaseResult> TYPE = new Type<>(FullDrawMod.id("release_result"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ReleaseResult> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, ReleaseResult::verdict, ReleaseResult::new);

        @Override
        public Type<ReleaseResult> type() {
            return TYPE;
        }
    }

    /** Set by the client entry: sends a payload to the server (common code never names the client's network class). */
    /**
     * The server's breath, sent to its owner when focusing starts or stops, when it runs out and when the draw
     * ends. The client predicts between these, from the same inputs, and takes the server's word when one arrives.
     */
    public record BreathState(float fill, boolean focusing, int exhaustedTicks) implements CustomPacketPayload {
        public static final Type<BreathState> TYPE = new Type<>(FullDrawMod.id("breath_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BreathState> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, BreathState::fill, ByteBufCodecs.BOOL, BreathState::focusing, ByteBufCodecs.VAR_INT, b -> b.exhaustedTicks() + 1,
                (fill, focusing, exhausted) -> new BreathState(fill, focusing, exhausted - 1));

        @Override
        public Type<BreathState> type() {
            return TYPE;
        }
    }

    /** An arrow's gravity multiplier (the Longbow's flatter arc), sent to the clients near it when it's loosed. */
    public record ArrowGravity(int entityId, float mult) implements CustomPacketPayload {
        public static final Type<ArrowGravity> TYPE = new Type<>(FullDrawMod.id("arrow_gravity"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ArrowGravity> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ArrowGravity::entityId, ByteBufCodecs.FLOAT, ArrowGravity::mult, ArrowGravity::new);

        @Override
        public Type<ArrowGravity> type() {
            return TYPE;
        }
    }

    /** A creeper's fuse a blunt arrow put out, sent to the players near it: the client counts its own fuse and would run on. */
    public record FuseOut(int entityId) implements CustomPacketPayload {
        public static final Type<FuseOut> TYPE = new Type<>(FullDrawMod.id("fuse_out"));
        public static final StreamCodec<RegistryFriendlyByteBuf, FuseOut> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, FuseOut::entityId, FuseOut::new);

        @Override
        public Type<FuseOut> type() {
            return TYPE;
        }
    }

    public static void fuseOut(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.Entity creeper) {
        FuseOut payload = new FuseOut(creeper.getId());
        for (ServerPlayer player : level.players())
            if (player.distanceToSqr(creeper) < 96 * 96) send(player, payload);
    }

    /** The server's shot numbers, sent to each player on joining and again when the server's config reloads. */
    public record ServerTuning(ShotMath.Tuning tuning) implements CustomPacketPayload {
        public static final Type<ServerTuning> TYPE = new Type<>(FullDrawMod.id("server_tuning"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ServerTuning> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            ShotMath.Tuning t = p.tuning();
            buf.writeVarInt(t.perfectWindow());
            buf.writeVarInt(t.perfectDelay());
            for (double d : new double[] {t.perfectMult(), t.precisionMobs(), t.precisionPlayers(), t.skillCap(), t.rangeStart(), t.rangePerBlock(),
                    t.rangeCap(), t.pointBlankDistance(), t.pointBlankMult(), t.trembleStartSeconds(), t.trembleFullSeconds(), t.trembleMaxSpread()})
                buf.writeDouble(d);
        }, buf -> new ServerTuning(new ShotMath.Tuning(buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble())));

        @Override
        public Type<ServerTuning> type() {
            return TYPE;
        }
    }

    /** When a player joins: the server's numbers for their ring and HUD. */
    public static void onJoin(ServerPlayer player) {
        send(player, new ServerTuning(com.panda.fulldraw.FullDrawConfig.tuning()));
    }

    /** When the server's config changes while it runs: everyone online gets the new numbers. */
    public static void onTuningChanged(net.minecraft.server.MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) onJoin(player);
    }

    public static Consumer<CustomPacketPayload> toServer = payload -> {};

    private FullDrawNetwork() {}

    public static void handleCycleQuiver(CycleQuiver payload, ServerPlayer player) {
        Quivers.cycle(player, payload.direction());
    }

    /** Sends to one player if their client has Full Draw (a vanilla client or a GameTest mock player doesn't). */
    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        Services.PLATFORM.sendToPlayer(player, payload);
    }
}
