package dev.guessbingo.net;

import dev.guessbingo.GuessBingo;
import dev.guessbingo.lex.GuessResult;
import dev.guessbingo.lex.Question;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record PushGuessResult(int index, GuessResult guessResult, Question reveal) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PushGuessResult> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(GuessBingo.MOD_ID, "push_guess"));


    public static final StreamCodec<RegistryFriendlyByteBuf, PushGuessResult> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, PushGuessResult::index,
            GuessResult.STREAM_CODEC, PushGuessResult::guessResult,
            Question.STREAM_CODEC, PushGuessResult::reveal,
            PushGuessResult::new
    );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
