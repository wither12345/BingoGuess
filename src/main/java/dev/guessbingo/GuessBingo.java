package dev.guessbingo;

import dev.guessbingo.cmd.GbCommands;
import dev.guessbingo.compat.BingoCompat;
import dev.guessbingo.data.Attachments;
import dev.guessbingo.game.MatchManager;
import dev.guessbingo.game.VanillaHost;
import dev.guessbingo.lex.Lexicon;
import dev.guessbingo.lex.LexiconReader;
import dev.guessbingo.net.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GuessBingo implements ModInitializer {
	public static final String MOD_ID = "guessbingo";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static MatchManager manager;

	public static MatchManager manager() {
		return manager;
	}

	@Override
	public void onInitialize() {
		Lexicon lexicon = Lexicon.loadBundled();
		PayloadTypeRegistry.clientboundPlay().register(InitQuestion.TYPE, InitQuestion.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PushGuessResult.TYPE, PushGuessResult.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CompleteData.TYPE, CompleteData.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SyncAdvancementsPayload.TYPE, SyncAdvancementsPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ScoreData.TYPE, ScoreData.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SelectData.TYPE, SelectData.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(GuessData.TYPE, GuessData.STREAM_CODEC);

		ServerPlayNetworking.registerGlobalReceiver(SelectData.TYPE, (SelectData::handle));
		ServerPlayNetworking.registerGlobalReceiver(GuessData.TYPE, (GuessData::handle));

		Attachments.SELECTING_ATTACHMENT.hashCode();

		MatchManager m = new MatchManager(lexicon, FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json"), new VanillaHost());
		manager = m;
		if (FabricLoader.getInstance().isModLoaded(BingoCompat.MOD_ID)) {
			try {
				BingoCompat.install(m);
			} catch (Throwable t) {
				LOGGER.error("对接 bingo 模组失败，退回独立模式（原版记分板队伍）", t);
			}
		} else {
			LOGGER.info("未检测到 bingo 模组，使用独立模式：队伍取自原版记分板，用 /gb start 开局");
		}

		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(Identifier.fromNamespaceAndPath(MOD_ID, "lexicon"), new LexiconReader());

		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> GbCommands.register(dispatcher, m));
		ServerLifecycleEvents.SERVER_STARTED.register(m::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> m.onServerStopping());
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			m.onPlayerJoin(handler.player);
			SyncAdvancementsPayload.send(handler.player, server);
		});
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
			if (!success) return;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				SyncAdvancementsPayload.send(player, server);
			}
		});

		LOGGER.info("猜词 Bingo 已加载，词库共 {} 条", lexicon.entries().size());
	}
}