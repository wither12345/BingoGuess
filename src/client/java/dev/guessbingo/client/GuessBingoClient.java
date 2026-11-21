package dev.guessbingo.client;

import dev.guessbingo.GuessBingo;
import dev.guessbingo.data.Attachments;
import dev.guessbingo.net.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

public final class GuessBingoClient implements ClientModInitializer {
	private static final String BINGO = "yet-another-minecraft-bingo";

	@Override
	public void onInitializeClient() {

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientAdvancementsStore.clear();
		});
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(GuessBingo.MOD_ID, "panel"), Hud::extract);

		ClientPlayNetworking.registerGlobalReceiver(InitQuestion.TYPE, (payload, context) -> ClientState.init(payload.questions()));
		ClientPlayNetworking.registerGlobalReceiver(PushGuessResult.TYPE, (payload, context) -> ClientState.updateQuestionState(payload));
		ClientPlayNetworking.registerGlobalReceiver(SyncAdvancementsPayload.TYPE, (payload, context) -> ClientAdvancementsStore.update(payload));
		ClientPlayNetworking.registerGlobalReceiver(ScoreData.TYPE, (payload, context) -> ClientState.updateScoreData(payload));
		ClientPlayNetworking.registerGlobalReceiver(CompleteData.TYPE, (payload, context) -> ClientState.completeQuestion(payload.index()));


		// 等所有模组都注册完界面元素之后，再接管 bingo 的棋盘：猜词对局进行时不画它，把左上角让给本面板
		Attachments.SELECTING_ATTACHMENT.hashCode();

		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			if (!FabricLoader.getInstance().isModLoaded(BINGO) || !ClientConfig.get().hideBingoCard) return;
			for (String path : new String[] {"card_above", "card_underneath"}) {
				try {
					HudElementRegistry.replaceElement(Identifier.fromNamespaceAndPath(BINGO, path), original -> (graphics, tracker) -> {
						if (!ClientState.active()) original.extractRenderState(graphics, tracker);
					});
				} catch (RuntimeException e) {
					GuessBingo.LOGGER.warn("没能接管 bingo 的界面元素 {}：对局中它的棋盘可能与猜词面板重叠", path, e);
				}
			}
		});
	}
}