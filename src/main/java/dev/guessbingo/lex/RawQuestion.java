package dev.guessbingo.lex;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

public record RawQuestion(Identifier id, Type type) {
    public static final StreamCodec<ByteBuf, RawQuestion> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, RawQuestion::id,
            Type.STREAM_CODEC, RawQuestion::type,
            RawQuestion::new
    );
}
