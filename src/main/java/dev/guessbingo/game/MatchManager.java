package dev.guessbingo.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.guessbingo.GuessBingo;
import dev.guessbingo.game.Host.TeamInfo;
import dev.guessbingo.lex.*;
import dev.guessbingo.net.HudState;
import dev.guessbingo.net.StatePayload;
import dev.guessbingo.ui.Texts;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.storage.LevelResource;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.*;

/**
 * 一局游戏的全部流程：选题、猜词、揭晓、挑战判定、计分、跳过投票、存档。
 * 所有方法都在服务器主线程上调用。
 */
public final class MatchManager {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final DecimalFormat SCORE_FORMAT = new DecimalFormat("##.#");

	public enum Kind { ACCEPTED, REJECTED, CANDIDATES }

	/** 一次猜词提交的处理结果，交给指令层去反馈。 */
	public record GuessOutcome(Kind kind, String message, List<Entry> candidates) {
		static GuessOutcome rejected(String message) {
			return new GuessOutcome(Kind.REJECTED, message, List.of());
		}
	}

	private static final class Vote {
		final Set<UUID> yes = new HashSet<>();
		final Set<UUID> no = new HashSet<>();
		int deadlineTick;
	}

	private final Lexicon lexicon;
	private final Path configFile;
	private final Random random = new Random();
	/** 词条名 -> 其挑战在当前服务器上能否解析 */
	private final Map<String, Boolean> validity = new HashMap<>();

	private Config config;
	private Host host;
	private MinecraftServer server;
	private Match match = new Match();
	private Detector detector;
	private Vote vote;
	private int ticks;

	public MatchManager(Lexicon lexicon, Path configFile, Host host) {
		this.lexicon = lexicon;
		this.configFile = configFile;
		this.config = Config.load(configFile);
		this.host = host;
	}

	// ------------------------------------------------------------------ 基础访问

	public Lexicon lexicon() {
		return lexicon;
	}

	public Config config() {
		return config;
	}

	public Match match() {
		return match;
	}

	public Host host() {
		return host;
	}

	public void setHost(Host host) {
		this.host = host;
	}

	public MinecraftServer server() {
		return server;
	}

	public void reloadConfig() {
		config = Config.load(configFile);
		validity.clear();
	}

	public void saveConfig() {
		config.save(configFile);
	}

	// ------------------------------------------------------------------ 生命周期

	public void onServerStarted(MinecraftServer server) {
		this.server = server;
		this.ticks = 0;
		this.validity.clear();
		int answerable = 0, valid = 0;
		List<String> broken = new ArrayList<>();
		for (Entry entry : lexicon.entries()) {
			if (!entry.answerable()) continue;
			answerable++;
			if (resolvable(entry)) valid++;
			else broken.add(entry.n);
		}
		GuessBingo.LOGGER.info("词库：{} 条可猜，{} 条可出题（其中 {} 条在本服务器上有效）", lexicon.entries().size(), answerable, valid);
		if (!broken.isEmpty()) GuessBingo.LOGGER.warn("以下词条的挑战目标在本服务器上不存在，已自动排除：{}", String.join("、", broken));
		load();
		if (match.active) {
			Entry answer = lexicon.get(match.answer);
			if (answer == null || match.current() == null) {
				GuessBingo.LOGGER.warn("存档中的对局状态无法恢复，已丢弃");
				match = new Match();
			} else {
				if (match.challenging()) detector = Detector.create(server, answer.ch);
				GuessBingo.LOGGER.info("已恢复进行中的对局：第 {}/{} 回合，阶段 {}", match.index + 1, match.questions.size(), match.phase);
			}
		}
	}

	public void onServerStopping() {
		if (server != null) save();
		server = null;
		detector = null;
		vote = null;
	}

	public void onPlayerJoin(ServerPlayer player) {
		syncTo(player);
		if (!match.active) return;
		Match.Question q = match.current();
		if (q == null) return;
		player.sendSystemMessage(Texts.info("对局进行中：第 " + (match.index + 1) + "/" + match.questions.size() + " 回合。"
				+ (match.guessing() ? "按 G 或输入 /guess <名称> 猜词。" : "谜底是「" + match.answer + "」，去完成它。")));
	}

	// ------------------------------------------------------------------ 开局与选题

	private boolean resolvable(Entry entry) {
		return validity.computeIfAbsent(entry.n, _ -> server != null && Detector.create(server, entry.ch) != null);
	}

	public boolean isValidAnswer(Entry entry) {
		return entry != null && entry.answerable() && config.enabled(entry) && resolvable(entry);
	}

	/** 同一挑战（例如各种木船、不同氧化程度的同一铜块）在一局里只出一次。 */
	private static String challengeKey(Entry entry) {
		Challenge ch = entry.ch;
		return ch.t + "|" + ch.ids + "|" + ch.items + "|" + ch.potions;
	}

	private List<Match.Question> pickQuestions(List<String> shortfalls, List<Integer> difficulties) {
		List<Match.Question> out = new ArrayList<>();
		Set<String> usedChallenges = new HashSet<>();
		Set<String> recent = new HashSet<>(config.recentAnswers);
		for (int d = 1; d <= 5; d++) {
			int wanted = difficulties.get(d - 1);
			if (wanted <= 0) continue;
			// 最近几局出过的谜底先不用；实在凑不够时再把它们放回来
			List<Entry> fresh = new ArrayList<>(), stale = new ArrayList<>();
			for (Entry entry : lexicon.entries()) {
				if (!isValidAnswer(entry) || config.difficultyOf(entry) != d) continue;
				if (config.typeWeights.getOrDefault(entry.ty, 1.0) <= 0) continue;
				(recent.contains(entry.n) ? stale : fresh).add(entry);
			}
			int got = draw(fresh, wanted, d, usedChallenges, out);
			if (got < wanted) got += draw(stale, wanted - got, d, usedChallenges, out);
			if (got < wanted) shortfalls.add("难度 " + d + " 只凑到 " + got + "/" + wanted + " 题");
		}
		return out;
	}

	/** 从候选中抽取至多 wanted 题，返回实际抽到的数量。 */
	private int draw(List<Entry> candidates, int wanted, int difficulty, Set<String> usedChallenges, List<Match.Question> out) {
		Map<String, List<Entry>> pools = new LinkedHashMap<>();
		for (Entry entry : candidates) pools.computeIfAbsent(entry.ty, k -> new ArrayList<>()).add(entry);
		int got = 0;
		while (got < wanted && !pools.isEmpty()) {
			String type = pickType(pools);
			List<Entry> pool = pools.get(type);
			Entry entry = pool.remove(random.nextInt(pool.size()));
			if (pool.isEmpty()) pools.remove(type);
			if (!usedChallenges.add(challengeKey(entry))) continue;
			out.add(new Match.Question(entry.n, difficulty));
			got++;
		}
		return got;
	}

	/**
	 * 先选类型，再在类型内等概率选词。类型的权重 = 配置的权重 × 该类型候选数的平方根：
	 * 方块、物品这样的大类出现得更多，但不会凭数量垄断；结构、状态效果这样的小类偶尔出现，又不至于总是那几个词。
	 */
	private String pickType(Map<String, List<Entry>> pools) {
		double total = 0;
		for (Map.Entry<String, List<Entry>> pool : pools.entrySet()) total += typeWeight(pool.getKey(), pool.getValue().size());
		double roll = random.nextDouble() * total;
		String last = null;
		for (Map.Entry<String, List<Entry>> pool : pools.entrySet()) {
			last = pool.getKey();
			roll -= typeWeight(pool.getKey(), pool.getValue().size());
			if (roll < 0) return last;
		}
		return last;
	}

	private double typeWeight(String type, int candidates) {
		return config.typeWeights.getOrDefault(type, 1.0) * Math.sqrt(candidates);
	}

	/** 开始一局，按配置随机选题。成功返回 null，否则返回原因。 */
	public String start() {
		return start(null);
	}

	/**
	 * 开始一局。
	 *
	 * @param fixed 指定题目（按给定顺序进行）；为 null 时按配置随机选题。指定题目主要供自动化测试使用
	 */
	public String start(List<Match.Question> fixed) {
		if (server == null) return "服务器尚未就绪";
		if (match.active) return "已经有一局正在进行，可用 /gb stop 结束它";
		List<TeamInfo> teams = host.teams(server);
		List<Integer> difficulties = host.onMatchStarted(server, match);

		if (teams.isEmpty()) return "还没有任何队伍，请先让玩家加入队伍";
		List<String> shortfalls = new ArrayList<>();
		List<Match.Question> questions;
		if (fixed != null) {
			questions = new ArrayList<>(fixed);
			for (Match.Question q : questions) {
				if (!isValidAnswer(lexicon.get(q.name))) return "「" + q.name + "」不能作为谜底";
			}
		} else {
			questions = pickQuestions(shortfalls, difficulties);
			for (Match.Question q : questions) config.rememberAnswer(q.name);
			saveConfig();
		}
		if (questions.isEmpty()) return "词库中没有符合当前设置的谜底";

		match = new Match();
		match.active = true;
		match.questions = questions;
		for (TeamInfo team : teams) registerTeam(team);
		detector = null;
		vote = null;

		broadcast(Texts.text("━━━━━━ 猜词 Bingo 开始 ━━━━━━", Texts.GOLD));
		List<String> plan = new ArrayList<>();
		for (int d = 1; d <= 5; d++) {
			final int difficulty = d;
			long n = questions.stream().filter(q -> q.difficulty == difficulty).count();
			if (n > 0) plan.add("难度" + d + "×" + n);
		}
		broadcast(Texts.info("本局共 " + questions.size() + " 回合，从易到难：" + String.join(" ", plan)));
		MutableComponent roster = Texts.info("参赛队伍：");
		for (int i = 0; i < teams.size(); i++) {
			TeamInfo team = teams.get(i);
			roster.append(Texts.text((i > 0 ? " " : "") + team.name() + "（" + team.members().size() + " 人）", team.color()));
		}
		broadcast(roster);
		broadcast(Texts.info("规则：先猜出谜底，再第一个完成它。猜词进度全员共享；谜底揭晓前，即使已经持有对应物品也不算完成。"));
		for (String shortfall : shortfalls) broadcast(Texts.warn("提示：" + shortfall + "（词库中该难度的可用谜底不足）"));
		beginRound();
		return null;
	}

	private void registerTeam(TeamInfo team) {
		match.teamNames.put(team.id(), team.name());
		match.teamColors.put(team.id(), team.color());
	}

	private void beginRound() {
		Match.Question q = match.current();
		match.phase = Match.GUESS;
		match.answer = q.name;
		match.history = new ArrayList<>();
		detector = null;
		vote = null;
		broadcast(Texts.info("第 " + (match.index + 1) + "/" + match.questions.size() + " 回合 · 难度 " + Texts.stars(q.difficulty)
				+ "（" + q.difficulty + " 分）· 按 G 猜词"));
		forEachPlayer(p -> sound(p, SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0f));
		save();
		sync();
	}

	// ------------------------------------------------------------------ 猜词

	public GuessOutcome guess(ServerPlayer player, String text) {
		if (!match.active) return GuessOutcome.rejected("现在没有进行中的对局");
		TeamInfo team = host.teamOf(server, player.getUUID());
		if (team == null) return GuessOutcome.rejected("你不在任何队伍中，正在观战，不能提交猜测");
		if (!match.guessing()) return GuessOutcome.rejected("本回合的谜底已经揭晓：「" + match.answer + "」，快去完成它");
		String query = Lexicon.normalize(text);
		if (query.isEmpty()) return GuessOutcome.rejected("请输入名称或关键词");
		Entry guess = lexicon.find(query);
		if (guess == null) {
			List<Entry> candidates = lexicon.search(query);
			if (candidates.isEmpty()) return GuessOutcome.rejected("词库里没有包含「" + query + "」的名称");
			return new GuessOutcome(Kind.CANDIDATES, query, candidates);
		}
		for (GuessRow previous : match.history) {
			if (previous.name.equals(guess.n)) return GuessOutcome.rejected("「" + guess.n + "」已经猜过了");
		}
		Entry answer = lexicon.get(match.answer);
		if (answer == null) return GuessOutcome.rejected("当前谜底异常，请联系管理员跳过本回合");

		// 等同变种按猜中处理，谜底改为玩家猜的那个（前提是它本身也能作为谜底）
		if (!guess.n.equals(answer.n) && Compare.equivalent(guess, answer) && isValidAnswer(guess)) {
			match.answer = guess.n;
			answer = guess;
		}

		registerTeam(team);
		GuessRow row = Compare.compare(lexicon, guess, answer);
		row.by = player.getScoreboardName();
		row.team = team.id();

        Match.RoundResult result = findOrCreateResult(match.current());
		match.pushHistory(row, result);

		broadcast(Texts.guessLine(row, team.name(), team.color()));

		if (row.ok) {
			reveal(player, answer);
		} else {
			sound(player, SoundEvents.NOTE_BLOCK_GUITAR.value(), 0.8f);
			save();
			sync();
		}
		return new GuessOutcome(Kind.ACCEPTED, guess.n, List.of());
	}

	/** 谜底被猜中：向全员公布，并开始判定挑战。 */
	private void reveal(ServerPlayer guesser, Entry answer) {
		Match.Question q = match.current();
		match.phase = Match.CHALLENGE;
		vote = null;
		detector = Detector.create(server, answer.ch);
		String task = answer.ch.describe();

		broadcast(Texts.good(guesser.getScoreboardName() + " 猜中了！谜底是「" + answer.n + "」（" + answer.ty + "）"));
		broadcast(Texts.info("现在去完成它：" + task + " —— 第一个完成的队伍得 " + q.difficulty + " 分"));
		forEachPlayer(p -> {
			title(p, Texts.text("谜底：" + answer.n, Texts.GOLD), Texts.text(task + " · " + q.difficulty + " 分", Texts.WHITE));
			sound(p, SoundEvents.PLAYER_LEVELUP, 1.0f);
		});

		if (detector == null) {
			broadcast(Texts.warn("这个谜底的挑战在当前服务器上无法判定，本回合自动跳过"));
			skipRound("无法判定");
			return;
		}
		save();
		sync();
		// 揭晓时已经满足条件的队伍各得一次分，然后立刻进入下一回合；击杀类必须在揭晓后发生
		if (!answer.ch.requiresFreshAction()) {
			LinkedHashMap<String, ServerPlayer> winners = scan();
			if (!winners.isEmpty()) completeRound(winners, true);
		}
	}

	// ------------------------------------------------------------------ 挑战判定

	private Map<UUID, TeamInfo> teamIndex() {
		Map<UUID, TeamInfo> index = new HashMap<>();
		for (TeamInfo team : host.teams(server)) {
			for (UUID member : team.members()) index.put(member, team);
		}
		return index;
	}

	/** 每支队伍里第一个满足条件的在线玩家。 */
	private LinkedHashMap<String, ServerPlayer> scan() {
		LinkedHashMap<String, ServerPlayer> winners = new LinkedHashMap<>();
		if (detector == null || !detector.polled()) return winners;
		Map<UUID, TeamInfo> index = teamIndex();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator()) continue;
			TeamInfo team = index.get(player.getUUID());
			if (team == null || winners.containsKey(team.id())) continue;
			if (detector.satisfied(player)) {
				registerTeam(team);
				winners.put(team.id(), player);
			}
		}
		return winners;
	}

	public void onKill(ServerPlayer killer, EntityType<?> type) {
		if (!match.challenging() || detector == null || !detector.matchesKill(type)) return;
		awardSingle(killer);
	}

	/** 状态效果被施加时调用（包括瞬时效果）。 */
	public void onEffect(ServerPlayer player, MobEffect effect) {
		if (!match.challenging() || detector == null || !detector.matchesEffect(effect)) return;
		awardSingle(player);
	}

	private void awardSingle(ServerPlayer player) {
		if (player.isSpectator()) return;
		TeamInfo team = host.teamOf(server, player.getUUID());
		if (team == null) return;
		registerTeam(team);
		LinkedHashMap<String, ServerPlayer> winners = new LinkedHashMap<>();
		winners.put(team.id(), player);
		completeRound(winners, false);
	}

	private void completeRound(LinkedHashMap<String, ServerPlayer> winners, boolean alreadyMet) {
		Match.Question q = match.current();
		Match.RoundResult result = findOrCreateResult(q);
		MutableComponent line = Texts.prefix();
		line.append(Texts.text(alreadyMet ? "揭晓时已满足：" : "挑战完成：", Texts.GREEN));
		boolean first = true;
		for (Map.Entry<String, ServerPlayer> winner : winners.entrySet()) {
			String teamId = winner.getKey();
			String playerName = winner.getValue().getScoreboardName();

			result.putWinner(teamId, q.difficulty);
			result.players.add(playerName);
			if (!first) line.append(Texts.text("、", Texts.GRAY));
			first = false;
			line.append(Texts.text("[" + teamName(teamId) + "] " + playerName, teamColor(teamId)));
            float multiply = result.getScoreMultiply(teamId);
            float finalScore = q.difficulty * multiply;
            line.append(Texts.text(" 「" + match.answer + "」 +" + SCORE_FORMAT.format(finalScore) + " 分", Texts.WHITE));
			if(multiply > 1)
				line.append(Texts.text( "(x" + SCORE_FORMAT.format(multiply) + "!)", Texts.GOLD));
		}

		match.results.add(result);
		broadcast(line);
		broadcast(Texts.prefix().append(scoreLine()));
		forEachPlayer(p -> sound(p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f));
		nextRound();
	}

	private Match.RoundResult findOrCreateResult(Match.Question q) {
        if(match.cachedResults.containsKey(q.name)) return match.cachedResults.get(q.name);

		Match.RoundResult result = new Match.RoundResult();
		result.round = match.index + 1;
		result.answer = match.answer;
		result.points = q.difficulty;
		result.guesses = match.history.size();
        match.cachedResults.put(q.name, result);
		return result;
	}

	/** 跳过当前回合，无人得分。 */
	public void skipRound(String reason) {
		if (!match.active) return;
		Match.RoundResult result = findOrCreateResult(match.current());
		result.skipped = true;
		match.results.add(result);
		broadcast(Texts.info("本回合已跳过（" + reason + "）。谜底是「" + match.answer + "」，无人得分"));
		nextRound();
	}

	private void nextRound() {
		detector = null;
		vote = null;
		match.index++;
		if (match.index >= match.questions.size()) {
			finish("全部回合已完成", false);
		} else {
			beginRound();
		}
	}

	/** 立刻结束本局并公布比分。 */
	public void finish(String reason, boolean endedByHost) {
		if (!match.active) return;
		match.active = false;
		detector = null;
		vote = null;
		broadcast(Texts.text("━━━━━━ 本局结束 ━━━━━━", Texts.GOLD));
		broadcast(Texts.info(reason));
		broadcast(Texts.prefix().append(Texts.text("最终比分：", Texts.WHITE)).append(scoreLine()));
		int best = match.getScores().values().stream().mapToInt(Float::intValue).max().orElse(0);
		List<String> leaders = new ArrayList<>();
		for (Map.Entry<String, Float> score : match.getScores().entrySet()) {
			if (score.getValue() == best) leaders.add(score.getKey());
		}
		if (leaders.size() == 1 && best > 0) {
			String winner = leaders.get(0);
			broadcast(Texts.prefix().append(Texts.text("获胜：" + teamName(winner) + "！", teamColor(winner))));
			forEachPlayer(p -> title(p, Texts.text(teamName(winner) + " 获胜", teamColor(winner)), Texts.text("最终比分 " + scoreText(), Texts.WHITE)));
		} else {
			broadcast(Texts.info(best > 0 ? "平局！" : "无人得分"));
			forEachPlayer(p -> title(p, Texts.text("本局结束", Texts.GOLD), Texts.text("最终比分 " + scoreText(), Texts.WHITE)));
		}
		save();
		sync();
		Match ended = match;
		try {
			host.onMatchEnded(server, ended, endedByHost);
		} catch (RuntimeException e) {
			GuessBingo.LOGGER.error("对局收尾失败", e);
		}
	}

	/** 宿主已经重置了世界：直接丢弃状态，不做任何播报。 */
	public void discard() {
		match = new Match();
		detector = null;
		vote = null;
		if (server != null) {
			save();
			sync();
		}
	}

	// ------------------------------------------------------------------ 跳过投票

	private List<ServerPlayer> voters() {
		List<ServerPlayer> out = new ArrayList<>();
		Map<UUID, TeamInfo> index = teamIndex();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (index.containsKey(player.getUUID())) out.add(player);
		}
		return out;
	}

	private static int needed(int voters) {
		return Math.max(1, (voters + 1) / 2);
	}

	public String startVote(String startedBy) {
		if (!match.active) return "现在没有进行中的对局";
		if (vote != null) return "已经有一场投票在进行";
		List<ServerPlayer> voters = voters();
		if (voters.isEmpty()) return "没有在线的参赛玩家，无法投票（可直接使用 /gb skip）";
		vote = new Vote();
		vote.deadlineTick = ticks + config.voteSeconds * 20;
		MutableComponent line = Texts.info(startedBy + " 发起了跳过本回合的投票，" + config.voteSeconds + " 秒内同意人数达到 "
				+ needed(voters.size()) + "/" + voters.size() + " 即跳过。 ");
		line.append(Texts.button("同意", Texts.GREEN, "gb vote yes", "点击投同意票"));
		line.append(Texts.text(" ", Texts.WHITE));
		line.append(Texts.button("反对", Texts.RED, "gb vote no", "点击投反对票"));
		broadcast(line);
		forEachPlayer(p -> sound(p, SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f));
		sync();
		return null;
	}

	public String castVote(ServerPlayer player, boolean agree) {
		if (!match.active || vote == null) return "现在没有进行中的投票";
		if (host.teamOf(server, player.getUUID()) == null) return "只有参赛玩家可以投票";
		UUID id = player.getUUID();
		vote.yes.remove(id);
		vote.no.remove(id);
		(agree ? vote.yes : vote.no).add(id);
		resolveVote(false);
		return null;
	}

	private void resolveVote(boolean timedOut) {
		if (vote == null) return;
		Set<UUID> online = new HashSet<>();
		for (ServerPlayer voter : voters()) online.add(voter.getUUID());
		long yes = vote.yes.stream().filter(online::contains).count();
		long no = vote.no.stream().filter(online::contains).count();
		int total = online.size();
		int need = needed(total);
		if (total > 0 && yes >= need) {
			vote = null;
			skipRound("投票通过，同意 " + yes + "/" + total);
		} else if (timedOut || total - no < need) {
			vote = null;
			broadcast(Texts.info("跳过投票未通过（同意 " + yes + "，反对 " + no + "，共 " + total + " 人），本回合继续"));
			sync();
		} else {
			sync();
		}
	}

	// ------------------------------------------------------------------ 每刻

	public void tick() {
		if (server == null) return;
		ticks++;
		if (!match.active) return;
		if (vote != null) {
			if (ticks >= vote.deadlineTick) resolveVote(true);
			else if (ticks % 20 == 0) sync();
		}
		if (match.challenging() && detector != null && detector.polled()
				&& (detector.pollEveryTick() || ticks % config.scanIntervalTicks == 0)) {
			LinkedHashMap<String, ServerPlayer> winners = scan();
			if (!winners.isEmpty()) {
				completeRound(winners, false);
				return;
			}
		}
		if (config.actionBarHints && ticks % 40 == 0) actionBarHints();
	}

	/** 没装客户端模组的玩家看不到面板，用动作栏给出当前回合的状态。 */
	private void actionBarHints() {
		Match.Question q = match.current();
		if (q == null) return;
		String head = "第 " + (match.index + 1) + "/" + match.questions.size() + " 回合 · ";
		Entry answer = lexicon.get(match.answer);
		Component hint = match.guessing()
				? Texts.text(head + "猜词中（已猜 " + match.history.size() + " 次）· 按 G 猜词", Texts.WHITE)
				: Texts.text(head + "谜底「" + match.answer + "」· " + (answer == null ? "" : answer.ch.describe()), Texts.GOLD);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!ServerPlayNetworking.canSend(player, StatePayload.TYPE)) {
				player.connection.send(new ClientboundSetActionBarTextPacket(hint));
			}
		}
	}

	// ------------------------------------------------------------------ 显示与同步

	public String teamName(String teamId) {
		return match.teamNames.getOrDefault(teamId, teamId);
	}

	public int teamColor(String teamId) {
		return match.teamColors.getOrDefault(teamId, Texts.WHITE);
	}

	private String scoreText() {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, Float> score : match.getScores().entrySet()) {
			if (!sb.isEmpty()) sb.append(" · ");
			sb.append(teamName(score.getKey())).append(" ").append(score.getValue());
		}
		return sb.toString();
	}

	public MutableComponent scoreLine() {
		MutableComponent out = Component.empty();
		boolean first = true;
		for (Map.Entry<String, Float> score : match.getScores().entrySet()) {
			if (!first) out.append(Texts.text(" · ", Texts.GRAY));
			first = false;
			out.append(Texts.text(teamName(score.getKey()) + " " + score.getValue(), teamColor(score.getKey())));
		}
		if (first) out.append(Texts.text("（暂无队伍）", Texts.GRAY));
		return out;
	}

	public HudState buildState() {
		HudState state = new HudState();
		state.active = match.active;
		if (!match.active) return state;
		Match.Question q = match.current();
		state.round = match.index + 1;
		state.rounds = match.questions.size();
		state.difficulty = q == null ? 0 : q.difficulty;
		state.phase = match.phase;
		if (match.challenging()) {
			Entry answer = lexicon.get(match.answer);
			state.answer = match.answer;
			if (answer != null) {
				state.type = answer.ty;
				state.task = answer.ch.describe();
			}
		}
		for (Map.Entry<String, Float> score : match.getScores().entrySet()) {
			HudState.Team team = new HudState.Team();
			team.id = score.getKey();
			team.name = teamName(score.getKey());
			team.color = teamColor(score.getKey());
			team.score = score.getValue();
			state.teams.add(team);
		}
		state.guesses = match.history.size();
		int from = Math.max(0, match.history.size() - config.hudRows);
		state.rows = new ArrayList<>(match.history.subList(from, match.history.size()));
		if (vote != null) {
			Set<UUID> online = new HashSet<>();
			for (ServerPlayer voter : voters()) online.add(voter.getUUID());
			HudState.Vote v = new HudState.Vote();
			v.yes = (int) vote.yes.stream().filter(online::contains).count();
			v.no = (int) vote.no.stream().filter(online::contains).count();
			v.need = needed(online.size());
			v.secondsLeft = Math.max(0, (vote.deadlineTick - ticks) / 20);
			state.vote = v;
		}
		return state;
	}

	public void sync() {
		if (server == null) return;
		StatePayload payload = new StatePayload(GSON.toJson(buildState()));
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, StatePayload.TYPE)) ServerPlayNetworking.send(player, payload);
		}
	}

	public void syncTo(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, StatePayload.TYPE)) {
			ServerPlayNetworking.send(player, new StatePayload(GSON.toJson(buildState())));
		}
	}

	// ------------------------------------------------------------------ 工具

	private interface PlayerAction {
		void run(ServerPlayer player);
	}

	private void forEachPlayer(PlayerAction action) {
		if (server == null) return;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) action.run(player);
	}

	public void broadcast(Component message) {
		if (server != null) server.getPlayerList().broadcastSystemMessage(message, false);
	}

	private static void title(ServerPlayer player, Component title, Component subtitle) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
		player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
		player.connection.send(new ClientboundSetTitleTextPacket(title));
	}

	public static void sound(ServerPlayer player, SoundEvent sound, float pitch) {
		player.connection.send(new ClientboundSoundPacket(Holder.direct(sound), SoundSource.MASTER,
				player.getX(), player.getY(), player.getZ(), 0.7f, pitch, player.getRandom().nextLong()));
	}

	// ------------------------------------------------------------------ 存档

	private Path saveFile() {
		return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(GuessBingo.MOD_ID).resolve("match.json");
	}

	public void save() {
		if (server == null) return;
		Path file = saveFile();
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(match, writer);
			}
			Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception e) {
			GuessBingo.LOGGER.error("保存对局状态失败：{}", file, e);
		}
	}

	private void load() {
		Path file = saveFile();
		match = new Match();
		if (!Files.exists(file)) return;
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			Match loaded = GSON.fromJson(reader, Match.class);
			if (loaded != null) match = loaded;
		} catch (Exception e) {
			GuessBingo.LOGGER.error("读取对局状态失败：{}", file, e);
		}
	}
}
