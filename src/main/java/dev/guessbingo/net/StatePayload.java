package dev.guessbingo.net;

import dev.guessbingo.GuessBingo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 服务端 -> 客户端：面板状态（{@link HudState} 的 JSON）。 */
public record StatePayload(String json) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<StatePayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(GuessBingo.MOD_ID, "state"));

	public static final StreamCodec<RegistryFriendlyByteBuf, StatePayload> CODEC =
			StreamCodec.composite(ByteBufCodecs.stringUtf8(1 << 18), StatePayload::json, StatePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
