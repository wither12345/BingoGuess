package dev.guessbingo.client;

import dev.guessbingo.GuessBingo;
import dev.guessbingo.net.StatePayload;
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
		ClientPlayNetworking.registerGlobalReceiver(StatePayload.TYPE, (payload, context) -> ClientState.update(payload.json()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientState.clear());
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(GuessBingo.MOD_ID, "panel"), Hud::extract);

		// 等所有模组都注册完界面元素之后，再接管 bingo 的棋盘：猜词对局进行时不画它，把左上角让给本面板
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
