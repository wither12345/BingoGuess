package dev.guessbingo.game;

import com.mojang.datafixers.util.Either;
import dev.guessbingo.GuessBingo;
import dev.guessbingo.game.Host.TeamInfo;
import dev.guessbingo.lex.*;
import dev.guessbingo.lex.wrapped.QuestionState;
import dev.guessbingo.lex.wrapped.WrappedAdvancement;
import dev.guessbingo.lex.wrapped.WrappedQuestion;
import dev.guessbingo.net.InitQuestion;
import dev.guessbingo.net.PushGuessResult;
import dev.guessbingo.ui.Texts;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 一局游戏的全部流程：选题、猜词、揭晓、挑战判定、计分、跳过投票、存档。
 * 所有方法都在服务器主线程上调用。
 */
public final class MatchManager {
    private static final DecimalFormat SCORE_FORMAT = new DecimalFormat("##.#");

	public enum Kind { ACCEPTED, REJECTED, CANDIDATES }

	/** 一次猜词提交的处理结果，交给指令层去反馈。 */
	public record GuessOutcome(Kind kind, String message, List<Entry> candidates) {
		static GuessOutcome rejected(String message) {
			return new GuessOutcome(Kind.REJECTED, message, List.of());
		}
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
		this.match = new Match();
	}

	public void onServerStopping() {
		server = null;
		detector = null;
		match.active = false;
	}

	public void onPlayerJoin(ServerPlayer player) {
		if (!match.active) return;
		initQuestions(player);
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

	private List<WrappedQuestion> pickWrappedQuestions(List<Integer> difficulties) {
		List<WrappedQuestion> questions = new ArrayList<>();
		int size = 0;
		for(int d = 0; d < 5; d++){
			size += difficulties.get(d);
		}
		int[] order = getOrder(size);
		int index = 0;

		for (int d = 0; d < 5; d++) {
			int wanted = difficulties.get(d);
			if (wanted <= 0) continue;

			while(wanted -- > 0) {
				Question question = LexiconReader.getRandom(d + 1);
				if (question == null) continue;
				questions.add(WrappedQuestion.get(question, order[index++]));
			}
			questions.sort(Comparator.comparingInt(WrappedQuestion::index));
		}
		return questions;
	}

	private static int[] getOrder(int total){
		int[] arr = new int[total];
		for (int i = 0; i < total; i++) arr[i] = i;
		Random r = new Random();
		for (int i = total - 1; i > 0; i--) {
			int j = r.nextInt(i + 1);
			int t = arr[i]; arr[i] = arr[j]; arr[j] = t;
		}
		return arr;
	}

	/** 开始一局，按配置随机选题。成功返回 null，否则返回原因
	 *
	 */
	public String start() {
		if (server == null) return "服务器尚未就绪";
		if (match.active) return "已经有一局正在进行，可用 /gb stop 结束它";
		List<TeamInfo> teams = host.teams(server);
		List<Integer> difficulties = host.onMatchStarted(server, match);

		if (teams.isEmpty()) return "还没有任何队伍，请先让玩家加入队伍";
        List<WrappedQuestion> wrappedQuestions = pickWrappedQuestions(difficulties);


		match = new Match();
		match.active = true;
		match.setQuestions(wrappedQuestions);
		for (TeamInfo team : teams) registerTeam(team);
		detector = null;

		this.initQuestions();

		broadcast(Texts.text("━━━━━━ 猜词 Bingo 开始 ━━━━━━", Texts.GOLD));
		List<String> plan = new ArrayList<>();
		for (int d = 1; d <= 5; d++) {
			final int difficulty = d;
			long n = wrappedQuestions.stream().filter(q -> q.difficulty() == difficulty).count();
			if (n > 0) plan.add("难度" + d + "×" + n);
		}
		broadcast(Texts.info("本局共 " + wrappedQuestions.size() + " 题目" + String.join(" ", plan)));
		MutableComponent roster = Texts.info("参赛队伍：");
		for (int i = 0; i < teams.size(); i++) {
			TeamInfo team = teams.get(i);
			roster.append(Texts.text((i > 0 ? " " : "") + team.name() + "（" + team.members().size() + " 人）", team.color()));
		}
		broadcast(roster);
		broadcast(Texts.info("规则：先猜出谜底，再第一个完成它。猜词进度全员共享；谜底揭晓前，即使已经持有对应物品也不算完成。"));
		return null;
	}

	private void registerTeam(TeamInfo team) {
		match.teamNames.put(team.id(), team.name());
		match.teamColors.put(team.id(), team.color());
	}

	// ------------------------------------------------------------------ 猜词

	public boolean guess(ServerPlayer player, int id, RawQuestion question) {
		if (!match.active) return false;
		TeamInfo team = host.teamOf(player);
		if (team == null) return false;
		int size = this.match.getQuestions().size();
		id %= size;
		WrappedQuestion wrappedQuestion = this.match.getQuestions().get(id);
		if (wrappedQuestion == null || wrappedQuestion.getCurrentState() != QuestionState.State.GUESSING) return false;
		Question guess = LexiconReader.get(question);
		PushGuessResult result = wrappedQuestion.compare(team, guess);
		if(wrappedQuestion.getCurrentState() == QuestionState.State.COMPLETING){
			wrappedQuestion.correct(player);
			boolean completed = false;
			for(ServerPlayer serverPlayer : server.getPlayerList().getPlayers()){
				if(wrappedQuestion.test(serverPlayer)){
					wrappedQuestion.complete(serverPlayer);
					completed = true;
				}
			}
			if(completed){
				Map<Host.TeamInfo, Float> scores = wrappedQuestion.completeServer(server, this.match.getQuestions());
				match.updateScores(scores);
			}
		}

		server.getPlayerList().getPlayers().forEach(p -> ServerPlayNetworking.send(p, result));
		return true;
	}

	public void testTrigger(ServerPlayer player,
							SimpleCriterionTrigger<? extends SimpleCriterionTrigger.SimpleInstance> trigger,
							Predicate<SimpleCriterionTrigger.SimpleInstance> matcher){
		this.match.getQuestions().forEach(question -> {
			if(question.getCurrentState() == QuestionState.State.COMPLETING &&
					question.testTrigger(player, trigger, matcher)){
				question.complete(player);
				Map<Host.TeamInfo, Float> score = question.completeServer(player.level().getServer(), this.match.getQuestions());
				this.match.updateScores(score);
			}
		});
		tryFinish();
	}

	public void triggerAdvancement(ServerPlayer player, AdvancementHolder holder) {
		this.match.getQuestions().forEach(question -> {
			if(question.getCurrentState() == QuestionState.State.COMPLETING &&
					question instanceof WrappedAdvancement advancement &&
					advancement.isType(holder)){
				question.complete(player);
				Map<Host.TeamInfo, Float> score = question.completeServer(player.level().getServer(), this.match.getQuestions());
				this.match.updateScores(score);
			}
		});
		tryFinish();
	}

	private void tryFinish(){
		boolean flag = true;
		for(WrappedQuestion question : this.match.getQuestions()){
			if(question.getCurrentState() != QuestionState.State.COMPLETED){
				flag = false;
				break;
			}
		}
		if(flag){
			finish("所有题目都已完成", false);
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

	/** 立刻结束本局并公布比分。 */
	public void finish(String reason, boolean endedByHost) {
		if (!match.active) return;
		match.active = false;
		detector = null;
		broadcast(Texts.text("━━━━━━ 本局结束 ━━━━━━", Texts.GOLD));
		broadcast(Texts.info(reason));
		broadcast(Texts.prefix().append(Texts.text("最终比分：", Texts.WHITE)).append(scoreLine()));

		Map<Host.TeamInfo, Float> scores = this.match.getScores();
		int best = match.getScores().values().stream().mapToInt(Float::intValue).max().orElse(0);
		List<String> leaders = new ArrayList<>();

		for (Map.Entry<Host.TeamInfo, Float> score : scores.entrySet()) {
			if (score.getValue() == best) leaders.add(score.getKey().id());
		}

		if (leaders.size() == 1 && best > 0) {
			String winner = leaders.getFirst();
			broadcast(Texts.prefix().append(Texts.text("获胜：" + teamName(winner) + "！", teamColor(winner))));
			forEachPlayer(p -> title(p, Texts.text(teamName(winner) + " 获胜", teamColor(winner)), Texts.text("最终比分 " + scoreText(scores), Texts.WHITE)));
		} else {
			broadcast(Texts.info(best > 0 ? "平局！" : "无人得分"));
			forEachPlayer(p -> title(p, Texts.text("本局结束", Texts.GOLD), Texts.text("最终比分 " + scoreText(scores), Texts.WHITE)));
		}


		Match ended = match;
		try {
			host.onMatchEnded(server, ended, endedByHost);
		} catch (RuntimeException e) {
			GuessBingo.LOGGER.error("对局收尾失败", e);
		}
	}

	private static String scoreText(Map<Host.TeamInfo, Float> scores){
		return scores.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getValue))
				.map(score -> score.getKey().name() + " " + score.getValue())
				.collect(Collectors.joining(" · "));
	}

	/** 宿主已经重置了世界：直接丢弃状态，不做任何播报。 */
	public void discard() {
		match = new Match();
		detector = null;
	}

	// ------------------------------------------------------------------ 跳过投票

	// ------------------------------------------------------------------ 每刻


	// ------------------------------------------------------------------ 显示与同步

	public String teamName(String teamId) {
		return match.teamNames.getOrDefault(teamId, teamId);
	}

	public int teamColor(String teamId) {
		return match.teamColors.getOrDefault(teamId, Texts.WHITE);
	}

	public MutableComponent scoreLine() {
		MutableComponent out = Component.empty();
		boolean first = true;
		for (Map.Entry<Host.TeamInfo, Float> score : match.getScores().entrySet()) {
			if (!first) out.append(Texts.text(" · ", Texts.GRAY));
			first = false;
			out.append(Texts.text(teamName(score.getKey().name()) + " " + score.getValue(), teamColor(score.getKey().id())));
		}
		if (first) out.append(Texts.text("（暂无队伍）", Texts.GRAY));
		return out;
	}

	public void initQuestions(){
		List<Either<Integer, WrappedQuestion>> questions = new ArrayList<>();
		for (WrappedQuestion q : match.getQuestions()) {
			if(q.getCurrentState() == QuestionState.State.GUESSING)
				questions.add(Either.left(q.difficulty()));
			else questions.add(Either.right(q));
		}
		InitQuestion question = new InitQuestion(questions);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerPlayNetworking.send(player, question);
		}
	}
	public void initQuestions(ServerPlayer player){
		List<Either<Integer, WrappedQuestion>> questions = new ArrayList<>();
		for (WrappedQuestion q : match.getQuestions()) {
			if(q.getCurrentState() == QuestionState.State.GUESSING)
				questions.add(Either.left(q.difficulty()));
			else questions.add(Either.right(q));
		}
		ServerPlayNetworking.send(player, new InitQuestion(questions));
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
}
