package dev.guessbingo;

import dev.guessbingo.cmd.GbCommands;
import dev.guessbingo.compat.BingoCompat;
import dev.guessbingo.game.MatchManager;
import dev.guessbingo.game.VanillaHost;
import dev.guessbingo.lex.Lexicon;
import dev.guessbingo.net.StatePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.effect.ServerMobEffectEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
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
		PayloadTypeRegistry.clientboundPlay().register(StatePayload.TYPE, StatePayload.CODEC);

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

		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> GbCommands.register(dispatcher, m));
		ServerLifecycleEvents.SERVER_STARTED.register(m::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> m.onServerStopping());
		ServerTickEvents.END_SERVER_TICK.register(server -> m.tick());
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> m.onPlayerJoin(handler.player));

		// 击杀类挑战：记在造成击杀的玩家头上（包括被其宠物等间接击杀的情况）
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			ServerPlayer killer = null;
			if (source.getEntity() instanceof ServerPlayer direct) killer = direct;
			else if (entity.getKillCredit() instanceof ServerPlayer credited) killer = credited;
			if (killer != null) m.onKill(killer, entity.getType());
		});
		ServerMobEffectEvents.AFTER_ADD.register((instance, entity, context) -> {
			if (entity instanceof ServerPlayer player) m.onEffect(player, instance.getEffect().value());
		});

		LOGGER.info("猜词 Bingo 已加载，词库共 {} 条", lexicon.entries().size());
	}

	/** 由 mixin 调用：瞬时效果被施加到某个生物身上。 */
	public static void onInstantEffect(LivingEntity target, MobEffect effect) {
		MatchManager m = manager;
		if (m != null && target instanceof ServerPlayer player) m.onEffect(player, effect);
	}
}
