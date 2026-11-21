package dev.guessbingo.net;

import dev.guessbingo.GuessBingo;
import dev.guessbingo.lex.RawQuestion;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record GuessData(int id, RawQuestion question) implements CustomPacketPayload {
    public static final Type<GuessData> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(GuessBingo.MOD_ID, "guess"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GuessData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, GuessData::id,
            RawQuestion.STREAM_CODEC, GuessData::question,
            GuessData::new
    );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(ServerPlayNetworking.Context context) {
        GuessBingo.manager().guess(context.player(), this.id, this.question);
    }
}
