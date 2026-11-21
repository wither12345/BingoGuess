package dev.guessbingo.net;

import dev.guessbingo.GuessBingo;
import dev.guessbingo.data.Attachments;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record SelectData(int id) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SelectData> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(GuessBingo.MOD_ID, "select"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SelectData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,
            SelectData::id,
            SelectData::new
    );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(ServerPlayNetworking.Context context) {
        context.player().setAttached(Attachments.SELECTING_ATTACHMENT, id);
    }
}
