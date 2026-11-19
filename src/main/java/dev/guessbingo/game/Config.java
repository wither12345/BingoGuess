package dev.guessbingo.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.guessbingo.GuessBingo;
import dev.guessbingo.lex.Entry;
import dev.guessbingo.lex.Lexicon;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 服务端配置，保存在 config/guessbingo.json。 */
public final class Config {
	public static final int MAX_PER_DIFFICULTY = 15;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** 一局内难度 1~5 各出多少题（每项 0~15）。按从易到难依次揭晓。 */
	public int[] countsByDifficulty = {3, 3, 2, 1, 1};
	/** 各类型被抽中的权重；先按权重选类型，再在该类型内等概率选词。 */
	public Map<String, Double> typeWeights = new LinkedHashMap<>();
	/** 跳过投票的时限（秒） */
	public int voteSeconds = 60;
	/** 物品栏、位置等判定的检查间隔（刻） */
	public int scanIntervalTicks = 10;
	/** 左上角显示最近多少次猜测 */
	public int hudRows = 8;
	/** 是否在动作栏给出当前回合提示（没装客户端模组的玩家靠它看状态） */
	public boolean actionBarHints = true;
	/** 装有 bingo 模组时，是否跟随 bingo 的开局自动开始 */
	public boolean autoStartWithBingo = true;
	/** 装有 bingo 模组时，是否把棋盘换成无法完成的格子，避免它自己播报或结束游戏 */
	public boolean neutralizeBingoCard = true;
	/** 逐条覆盖：名称 -> 难度 / 是否允许作为谜底 */
	public Map<String, Override> overrides = new LinkedHashMap<>();
	/** 最近若干局出过的谜底，选题时尽量避开；由模组自动维护 */
	public List<String> recentAnswers = new ArrayList<>();
	/** 记住最近多少个谜底，0 表示不避重 */
	public int recentAnswerMemory = 60;

	public static final class Override {
		public Integer difficulty;
		public Boolean enabled;
	}

	public static Config load(Path file) {
		Config config = null;
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				config = GSON.fromJson(reader, Config.class);
			} catch (Exception e) {
				GuessBingo.LOGGER.error("读取配置 {} 失败，改用默认值", file, e);
			}
		}
		if (config == null) config = new Config();
		config.sanitize();
		config.save(file);
		return config;
	}

	public void save(Path file) {
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			GuessBingo.LOGGER.error("保存配置 {} 失败", file, e);
		}
	}

	private void sanitize() {
		int[] counts = new int[5];
		for (int i = 0; i < 5; i++) {
			int value = countsByDifficulty != null && i < countsByDifficulty.length ? countsByDifficulty[i] : 0;
			counts[i] = Math.max(0, Math.min(MAX_PER_DIFFICULTY, value));
		}
		countsByDifficulty = counts;
		if (typeWeights == null) typeWeights = new LinkedHashMap<>();
		for (String type : Lexicon.TYPES) typeWeights.putIfAbsent(type, 1.0);
		typeWeights.replaceAll((type, weight) -> weight == null || weight < 0 ? 0.0 : weight);
		voteSeconds = Math.max(5, Math.min(600, voteSeconds));
		scanIntervalTicks = Math.max(1, Math.min(200, scanIntervalTicks));
		hudRows = Math.max(1, Math.min(12, hudRows));
		if (overrides == null) overrides = new LinkedHashMap<>();
		if (recentAnswers == null) recentAnswers = new ArrayList<>();
		recentAnswerMemory = Math.max(0, Math.min(1000, recentAnswerMemory));
		trimRecent();
	}

	public void rememberAnswer(String name) {
		recentAnswers.remove(name);
		recentAnswers.add(name);
		trimRecent();
	}

	private void trimRecent() {
		while (recentAnswers.size() > recentAnswerMemory) recentAnswers.remove(0);
	}

	public int totalQuestions() {
		int total = 0;
		for (int count : countsByDifficulty) total += count;
		return total;
	}

	/** 该词条在当前配置下的难度（也是分值）。 */
	public int difficultyOf(Entry entry) {
		Override override = overrides.get(entry.n);
		if (override != null && override.difficulty != null) return Math.max(1, Math.min(5, override.difficulty));
		return entry.d;
	}

	public boolean enabled(Entry entry) {
		Override override = overrides.get(entry.n);
		return override == null || override.enabled == null || override.enabled;
	}
}
