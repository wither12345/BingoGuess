package dev.guessbingo.compat;

import dev.guessbingo.GuessBingo;
import dev.guessbingo.game.Host;
import dev.guessbingo.game.Match;
import dev.guessbingo.game.MatchManager;
import dev.guessbingo.ui.Texts;
import me.jfenn.bingo.api.BingoEvents;
import me.jfenn.bingo.api.IBingoApi;
import me.jfenn.bingo.api.card.ICard;
import me.jfenn.bingo.api.card.ICardService;
import me.jfenn.bingo.api.data.IBingoTeam;
import me.jfenn.bingo.api.options.EndWhen;
import me.jfenn.bingo.api.state.GameState;
import me.jfenn.bingo.common.card.BingoCard;
import me.jfenn.bingo.common.card.BingoCardEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.TeamColor;

import java.util.*;

/**
 * 与 Yet Another Minecraft BINGO（2.15+）的对接。只有检测到该模组时才会加载本类。
 *
 * <p>bingo 负责大厅、分队、把玩家送进新世界，以及一局结束后换种子重建世界；
 * 本模组在 bingo 进入“游戏中”时开始猜词对局，全部回合结束后让 bingo 结束这一局。
 * 对局期间把 bingo 的结束条件改成“永不结束”，并把棋盘换成生存模式拿不到的物品，
 * 这样它不会自己播报或提前结束游戏。
 */
public final class BingoCompat implements Host {
	public static final String MOD_ID = "yet-another-minecraft-bingo";
	/** 生存模式无法获得的物品，用来填满 bingo 棋盘。 */
	private static final String[] BLANKS = {
			"barrier", "bedrock", "command_block", "chain_command_block", "repeating_command_block",
			"command_block_minecart", "structure_block", "structure_void", "jigsaw", "light",
			"debug_stick", "knowledge_book", "spawner", "trial_spawner", "vault",
			"end_portal_frame", "reinforced_deepslate", "budding_amethyst", "petrified_oak_slab", "player_head",
			"farmland", "dirt_path", "infested_stone", "test_block", "test_instance_block",
	};

	private static final Map<String, String> TEAM_NAMES = Map.of(
			"red", "红队", "blue", "蓝队", "green", "绿队", "yellow", "黄队",
			"aqua", "青队", "pink", "粉队", "orange", "橙队", "gray", "灰队");
	private static final Map<String, Integer> TEAM_COLORS = Map.of(
			"red", 0xFF5555, "blue", 0x5599FF, "green", 0x55FF55, "yellow", 0xFFFF55,
			"aqua", 0x55FFFF, "pink", 0xFF88DD, "orange", 0xFFAA00, "gray", 0xAAAAAA);

	private final MatchManager manager;
	private IBingoApi api;
	private EndWhen previousEndWhen;

	private BingoCompat(MatchManager manager) {
		this.manager = manager;
	}

	public static void install(MatchManager manager) {
		BingoCompat compat = new BingoCompat(manager);
		manager.setHost(compat);
		BingoEvents.INIT.register(api -> {
			compat.api = api;
			compat.hidePreviewCard();
		});
		BingoEvents.CLOSE.register(ignored -> compat.api = null);
		BingoEvents.GAME_STARTING.register(ignored -> compat.onStarting());
		BingoEvents.GAME_STARTED.register(event -> compat.onStarted());
		BingoEvents.GAME_ENDED.register(event -> compat.onEnded());
		BingoEvents.GAME_RESET.register(ignored -> compat.onReset());
		GuessBingo.LOGGER.info("已对接 Yet Another Minecraft BINGO：队伍、开局与重置由它提供");
	}

	// ------------------------------------------------------------------ bingo 事件

	private void onStarting() {
		if (api == null || !manager.config().autoStartWithBingo) return;
		try {
			EndWhen current = api.getOptions().getEndGameWhen();
			if (!(current instanceof EndWhen.Never)) {
				previousEndWhen = current;
				api.getOptions().setEndGameWhen(EndWhen.Never.INSTANCE);
			}
		} catch (Throwable t) {
			GuessBingo.LOGGER.warn("无法把 bingo 的结束条件改为“永不结束”", t);
		}
	}

	private void onStarted() {
		if (api == null || !manager.config().autoStartWithBingo) return;
		if (manager.match().active) return;
		String error = manager.start();
		if (error != null) {
			GuessBingo.LOGGER.warn("跟随 bingo 开局失败：{}", error);
			manager.broadcast(Texts.warn("猜词对局没有开始：" + error));
		}
	}

	private void onEnded() {
		if (manager.match().active) manager.finish("bingo 游戏已结束", true);
		restoreOptions();
	}

	private void onReset() {
		// bingo 回到大厅（大厅模式下世界已经重建）：上一局的状态不再有意义
		if (manager.server() != null && !manager.match().active && !manager.match().questions.isEmpty()) manager.discard();
		hidePreviewCard();
	}

	/** 大厅里的“预览棋盘”与猜词无关，容易让玩家以为那是本局的目标，所以关掉这项显示。 */
	private void hidePreviewCard() {
		if (api == null || !manager.config().autoStartWithBingo || !manager.config().neutralizeBingoCard) return;
		try {
			if (api.getOptions().getShowPreviewCard()) api.getOptions().setShowPreviewCard(false);
		} catch (Throwable t) {
			GuessBingo.LOGGER.warn("无法关闭 bingo 的局前棋盘预览", t);
		}
	}

	private void restoreOptions() {
		if (api == null || previousEndWhen == null) return;
		try {
			api.getOptions().setEndGameWhen(previousEndWhen);
		} catch (Throwable t) {
			GuessBingo.LOGGER.warn("无法恢复 bingo 的结束条件", t);
		}
		previousEndWhen = null;
	}

	// ------------------------------------------------------------------ Host

	@Override
	public String label() {
		return "Yet Another Minecraft BINGO";
	}

	@Override
	public List<TeamInfo> teams(MinecraftServer server) {
		List<TeamInfo> out = new ArrayList<>();
		if (api == null) return out;
		for (IBingoTeam team : api.getTeams()) {
			List<UUID> members = team.getPlayers();
			if (members.isEmpty()) continue;
			String id = team.getId();
			String key = id.startsWith("bingo_") ? id.substring("bingo_".length()) : id;
			String name = TEAM_NAMES.get(key);
			Integer color = TEAM_COLORS.get(key);
			if (name == null || color == null) {
				PlayerTeam scoreboardTeam = server.getScoreboard().getPlayerTeam(id);
				if (name == null) name = scoreboardTeam != null ? scoreboardTeam.getDisplayName().getString() : id;
				if (color == null) color = scoreboardTeam != null ? scoreboardTeam.getColor().map(TeamColor::rgb).orElse(Texts.WHITE) : Texts.WHITE;
			}
			out.add(new TeamInfo(id, name, color, List.copyOf(members)));
		}
		return out;
	}

	@Override
	public String manualStartBlocker(MinecraftServer server) {
		if (api == null) return "bingo 模组尚未初始化";
		if (!GameState.PLAYING.equals(api.getGame().getState())) {
			return "请先用 bingo 的开始按钮或 /bingo start 开局；进入游戏后猜词会自动开始";
		}
		return null;
	}

	@Override
	public List<Integer> onMatchStarted(MinecraftServer server, Match match) {
		// 大厅模式下每局都会换种子，把它记进日志便于核对
		GuessBingo.LOGGER.info("猜词对局开始：世界种子 {}，大厅模式 {}", server.overworld().getSeed(), api != null && api.getConfig().isLobbyMode());
		List<Integer> difficulties = new ArrayList<>(5);
		for (int i = 0; i < 5; i++)
			difficulties.add(0);
		if (api == null || !manager.config().neutralizeBingoCard) return List.of(1,1,1,1,1);
		try {
			ICardService cards = api.getCards();
			Set<UUID> seen = new HashSet<>();
			List<ICard> targets = new ArrayList<>();
			for (IBingoTeam team : api.getTeams()) {
				ICard card = cards.getTeamCard(team.getId());
				if (card != null && seen.add(card.getId())) targets.add(card);
			}
			ICard active = cards.getActiveCard();
			if (seen.add(active.getId())) targets.add(active);
			for (ICard card : targets) {
				for (int i = 0; i < BLANKS.length; i++) {
					if(card instanceof BingoCard bingoCard) {
						BingoCardEntry entry = bingoCard.getEntries().get(i);
						if (entry.getTier() != null) {
							int index = 4 - entry.getTier().ordinal();
							difficulties.set(index, difficulties.get(index) + 1);
						}
					}
					cards.replaceEntry(card, i % 5, i / 5, "minecraft:" + BLANKS[i]);
				}
			}
			GuessBingo.LOGGER.info("已把 {} 张 bingo 棋盘换成无法完成的格子", targets.size());
		} catch (Throwable t) {
			GuessBingo.LOGGER.warn("无法替换 bingo 棋盘（不影响猜词对局，但 bingo 可能会播报它自己的任务）", t);
		}
		return difficulties;
	}

	@Override
	public void onMatchEnded(MinecraftServer server, Match match, boolean endedByHost) {
		if (endedByHost || api == null) return;
		if (GameState.PLAYING.equals(api.getGame().getState())) {
			// 走 bingo 自己的结束流程，这样结算界面、统计和之后的重置都照常进行
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "bingo end");
		}
		restoreOptions();
	}
}
