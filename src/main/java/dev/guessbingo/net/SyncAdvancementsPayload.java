package dev.guessbingo.net;

import dev.guessbingo.GuessBingo;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;

/** 服务端 -> 客户端：完整进度列表（包含图表与标题，与玩家是否已获得无关）。 */
public record SyncAdvancementsPayload(Map<Identifier, DisplayInfo> advancements) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SyncAdvancementsPayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(GuessBingo.MOD_ID, "sync_advancements"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SyncAdvancementsPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.map(
					HashMap::new,
					Identifier.STREAM_CODEC,
					DisplayInfo.STREAM_CODEC
			),
			SyncAdvancementsPayload::advancements,
			SyncAdvancementsPayload::new
	);

	@Override
	public @NonNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	/** 把服务端持有的完整进度注册表全量下发给指定玩家。 */
	public static void send(ServerPlayer player, MinecraftServer server) {
		Map<Identifier, DisplayInfo> infos = new HashMap<>();
		server.getAdvancements().getAllAdvancements().forEach(holder -> {
			holder.value().display().ifPresent(info -> infos.put(holder.id(), info));
		});
		ServerPlayNetworking.send(player, new SyncAdvancementsPayload(infos));
	}
}