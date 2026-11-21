package dev.guessbingo.net;

import com.mojang.datafixers.util.Either;
import dev.guessbingo.GuessBingo;
import dev.guessbingo.lex.wrapped.WrappedQuestion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.List;

public record InitQuestion(List<Either<Integer, WrappedQuestion>> questions) implements CustomPacketPayload{
    public static final CustomPacketPayload.Type<InitQuestion> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(GuessBingo.MOD_ID, "questions"));

    public static final StreamCodec<RegistryFriendlyByteBuf, InitQuestion> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.either(
                    ByteBufCodecs.INT,
                    WrappedQuestion.STREAM_CODEC
            ).apply(ByteBufCodecs.list()),
            InitQuestion::questions,
            InitQuestion::new
    );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
