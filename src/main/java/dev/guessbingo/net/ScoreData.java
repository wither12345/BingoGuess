package dev.guessbingo.net;

import dev.guessbingo.GuessBingo;
import dev.guessbingo.game.Host;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;

public record ScoreData(Map<Host.TeamInfo, Float> scores) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ScoreData> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(GuessBingo.MOD_ID, "score_data"));

    public static final StreamCodec<ByteBuf, ScoreData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(
                    HashMap::new,
                    Host.TeamInfo.STREAM_CODEC,
                    ByteBufCodecs.FLOAT
            ),
            ScoreData::scores,
            ScoreData::new
    );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
