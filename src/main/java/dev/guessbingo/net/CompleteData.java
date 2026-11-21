package dev.guessbingo.net;

import dev.guessbingo.GuessBingo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record CompleteData(int index) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CompleteData> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(GuessBingo.MOD_ID, "complete"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CompleteData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,
            CompleteData::index,
            CompleteData::new
    );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
