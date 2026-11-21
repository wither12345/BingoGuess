package dev.guessbingo.cmd;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.guessbingo.game.Config;
import dev.guessbingo.game.Match;
import dev.guessbingo.game.MatchManager;
import dev.guessbingo.lex.Entry;
import dev.guessbingo.lex.GuessRow;
import dev.guessbingo.lex.Lexicon;
import dev.guessbingo.ui.Dialogs;
import dev.guessbingo.ui.Texts;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * 指令：
 * <pre>
 * /guess [名称]            提交猜测；不带名称时打开输入框
 * /gb                      当前状态          /gb history   本回合全部猜测
 * /gb search 关键词        查询候选名称      /gb rules     玩法说明
 * /gb vote yes|no          对跳过投票表态
 * 管理员：
 * /gb start | stop | skip | voteskip | answer | reload
 * /gb config [count 难度 数量 | weight 类型 权重 | votetime 秒 | hudrows 行数]
 * /gb config difficulty 难度 名称 | disable 名称 | enable 名称 | reset 名称 | entry 名称
 * </pre>
 */
public final class GbCommands {
	private GbCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, MatchManager manager) {
		dispatcher.register(literal("guess")
				.executes(ctx -> openInput(ctx.getSource())));
				//.then(argument("name", StringArgumentType.greedyString())
				//		.executes(ctx -> guess(ctx.getSource(), manager, StringArgumentType.getString(ctx, "name")))));

		LiteralArgumentBuilder<CommandSourceStack> gb = literal("gb")
				.then(literal("history").executes(ctx -> history(ctx.getSource(), manager)))
				.then(literal("rules").executes(ctx -> rules(ctx.getSource())))
				.then(literal("search")
						.executes(ctx -> openInput(ctx.getSource()))
						.then(argument("keyword", StringArgumentType.greedyString())
								.executes(ctx -> search(ctx.getSource(), manager, StringArgumentType.getString(ctx, "keyword")))))
				.then(literal("start").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(ctx -> start(ctx.getSource(), manager)))
				.then(literal("stop").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(ctx -> stop(ctx.getSource(), manager)))
				.then(literal("answer").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(ctx -> answer(ctx.getSource(), manager)))
				.then(literal("reload").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(ctx -> {
					manager.reloadConfig();
					ctx.getSource().sendSuccess(() -> Texts.good("已重新读取配置"), true);
					return 1;
				}))
				.then(config(manager));

		LiteralCommandNode<CommandSourceStack> node = dispatcher.register(gb);
	}

	private static LiteralArgumentBuilder<CommandSourceStack> config(MatchManager manager) {
		LiteralArgumentBuilder<CommandSourceStack> weight = literal("weight");
		for (String type : Lexicon.TYPES) {
			weight.then(literal(type).then(argument("value", DoubleArgumentType.doubleArg(0, 100)).executes(ctx -> {
				manager.config().typeWeights.put(type, DoubleArgumentType.getDouble(ctx, "value"));
				manager.saveConfig();
				ctx.getSource().sendSuccess(() -> Texts.good("「" + type + "」的抽取权重已设为 " + DoubleArgumentType.getDouble(ctx, "value")), true);
				return 1;
			})));
		}
		return literal("config").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(ctx -> showConfig(ctx.getSource(), manager))
				.then(literal("count")
						.then(argument("difficulty", IntegerArgumentType.integer(1, 5))
								.then(argument("count", IntegerArgumentType.integer(0, Config.MAX_PER_DIFFICULTY)).executes(ctx -> {
									int d = IntegerArgumentType.getInteger(ctx, "difficulty");
									int count = IntegerArgumentType.getInteger(ctx, "count");
									manager.config().countsByDifficulty[d - 1] = count;
									manager.saveConfig();
									ctx.getSource().sendSuccess(() -> Texts.good("难度 " + d + " 的题数已设为 " + count + "（下一局生效，本局共 "
											+ manager.config().totalQuestions() + " 题）"), true);
									return 1;
								}))))
				.then(weight)
				.then(literal("votetime").then(argument("seconds", IntegerArgumentType.integer(5, 600)).executes(ctx -> {
					manager.config().voteSeconds = IntegerArgumentType.getInteger(ctx, "seconds");
					manager.saveConfig();
					ctx.getSource().sendSuccess(() -> Texts.good("跳过投票时限已设为 " + manager.config().voteSeconds + " 秒"), true);
					return 1;
				})))
				.then(literal("hudrows").then(argument("rows", IntegerArgumentType.integer(1, 12)).executes(ctx -> {
					manager.config().hudRows = IntegerArgumentType.getInteger(ctx, "rows");
					manager.saveConfig();
					ctx.getSource().sendSuccess(() -> Texts.good("面板显示的猜测行数已设为 " + manager.config().hudRows), true);
					return 1;
				})))
				.then(literal("difficulty").then(argument("difficulty", IntegerArgumentType.integer(1, 5))
						.then(argument("name", StringArgumentType.greedyString()).executes(ctx ->
								override(ctx.getSource(), manager, StringArgumentType.getString(ctx, "name"), IntegerArgumentType.getInteger(ctx, "difficulty"), null)))))
				.then(literal("disable").then(argument("name", StringArgumentType.greedyString()).executes(ctx ->
						override(ctx.getSource(), manager, StringArgumentType.getString(ctx, "name"), null, false))))
				.then(literal("enable").then(argument("name", StringArgumentType.greedyString()).executes(ctx ->
						override(ctx.getSource(), manager, StringArgumentType.getString(ctx, "name"), null, true))))
				.then(literal("reset").then(argument("name", StringArgumentType.greedyString()).executes(ctx -> {
					Entry entry = manager.lexicon().find(StringArgumentType.getString(ctx, "name"));
					if (entry == null) return fail(ctx.getSource(), "词库里没有这个名称");
					manager.config().overrides.remove(entry.n);
					manager.saveConfig();
					ctx.getSource().sendSuccess(() -> Texts.good("已恢复「" + entry.n + "」的默认设置"), true);
					return 1;
				})))
				.then(literal("entry").then(argument("name", StringArgumentType.greedyString()).executes(ctx ->
						entryInfo(ctx.getSource(), manager, StringArgumentType.getString(ctx, "name")))));
	}

	private static int fail(CommandSourceStack source, String message) {
		source.sendFailure(Texts.warn(message));
		return 0;
	}

	private static ServerPlayer playerOrNull(CommandSourceStack source) {
		return source.getPlayer();
	}

	private static int openInput(CommandSourceStack source) {
		ServerPlayer player = playerOrNull(source);
		if (player == null) return fail(source, "只有玩家可以打开猜词窗口；请使用 /guess <名称>");
		if (!Dialogs.openInput(player)) return fail(source, "猜词窗口未加载，请直接输入 /guess <名称>");
		return 1;
	}

	private static int search(CommandSourceStack source, MatchManager manager, String keyword) {
		String query = Lexicon.normalize(keyword);
		List<Entry> candidates = manager.lexicon().search(query);
		if (candidates.isEmpty()) return fail(source, "词库里没有包含「" + query + "」的名称");
		ServerPlayer player = playerOrNull(source);
		if (player != null) {
			Dialogs.showCandidates(player, query, candidates);
		} else {
			StringBuilder sb = new StringBuilder();
			for (Entry entry : candidates.subList(0, Math.min(30, candidates.size()))) sb.append(entry.n).append("（").append(entry.ty).append("） ");
			source.sendSuccess(() -> Texts.info("共 " + candidates.size() + " 个：" + sb), false);
		}
		return candidates.size();
	}


	private static int history(CommandSourceStack source, MatchManager manager) {
		ServerPlayer player = playerOrNull(source);
		if (player != null) {
			return 1;
		}
		Match match = manager.match();
		if (!match.active) return fail(source, "现在没有进行中的对局");
		for (GuessRow row : match.history) {
			MutableComponent line = Component.literal(row.by + " ").append(Texts.row(row));
			source.sendSuccess(() -> line, false);
		}
		return match.history.size();
	}

	private static int rules(CommandSourceStack source) {
		ServerPlayer player = playerOrNull(source);
		if (player != null) {
			Dialogs.showRules(player);
		} else {
			for (String rule : Dialogs.rules()) source.sendSuccess(() -> Component.literal(rule), false);
		}
		return 1;
	}


	private static int start(CommandSourceStack source, MatchManager manager) {
		String blocker = manager.host().manualStartBlocker(source.getServer());
		if (blocker != null) return fail(source, blocker);
		String error = manager.start();
		if (error != null) return fail(source, error);
		return 1;
	}

	private static int stop(CommandSourceStack source, MatchManager manager) {
		if (!manager.match().active) return fail(source, "现在没有进行中的对局");
		manager.finish(source.getTextName() + " 结束了本局", false);
		return 1;
	}

	private static int answer(CommandSourceStack source, MatchManager manager) {
		Match match = manager.match();
		if (!match.active) return fail(source, "现在没有进行中的对局");
		Entry entry = manager.lexicon().get(match.answer);
		String detail = entry == null ? "" : "（" + entry.ty + "，" + entry.ch.describe() + "）";
		source.sendSuccess(() -> Texts.info("当前谜底：「" + match.answer + "」" + detail + "。仅你可见"), false);
		return 1;
	}

	private static String countsText(Config config) {
		StringBuilder sb = new StringBuilder();
		for (int d = 1; d <= 5; d++) {
			if (d > 1) sb.append(" ");
			sb.append("难度").append(d).append("×").append(config.countsByDifficulty[d - 1]);
		}
		return sb.toString();
	}

	private static int showConfig(CommandSourceStack source, MatchManager manager) {
		Config config = manager.config();
		source.sendSuccess(() -> Texts.info("各难度题数：" + countsText(config) + "（共 " + config.totalQuestions() + " 题，每项上限 " + Config.MAX_PER_DIFFICULTY + "）"), false);
		int[] available = new int[5];
		for (Entry entry : manager.lexicon().entries()) {
			if (manager.isValidAnswer(entry)) available[config.difficultyOf(entry) - 1]++;
		}
		StringBuilder pool = new StringBuilder();
		for (int d = 1; d <= 5; d++) pool.append(" 难度").append(d).append(" ").append(available[d - 1]);
		source.sendSuccess(() -> Texts.info("可用谜底数：" + pool.toString().trim()), false);
		StringBuilder weights = new StringBuilder();
		for (String type : Lexicon.TYPES) weights.append(type).append(" ").append(config.typeWeights.getOrDefault(type, 1.0)).append("  ");
		source.sendSuccess(() -> Texts.info("类型权重：" + weights.toString().trim()), false);
		source.sendSuccess(() -> Texts.info("跳过投票时限 " + config.voteSeconds + " 秒 · 面板行数 " + config.hudRows
				+ " · 逐条覆盖 " + config.overrides.size() + " 条 · 队伍来源：" + manager.host().label()), false);
		return 1;
	}

	private static int override(CommandSourceStack source, MatchManager manager, String name, Integer difficulty, Boolean enabled) {
		Entry entry = manager.lexicon().find(name);
		if (entry == null) return fail(source, "词库里没有这个名称");
		if (!entry.answerable()) return fail(source, "「" + entry.n + "」不能作为谜底：" + entry.why);
		Config.Override override = manager.config().overrides.computeIfAbsent(entry.n, k -> new Config.Override());
		if (difficulty != null) override.difficulty = difficulty;
		if (enabled != null) override.enabled = enabled;
		manager.saveConfig();
		return entryInfo(source, manager, entry.n);
	}

	private static int entryInfo(CommandSourceStack source, MatchManager manager, String name) {
		Entry entry = manager.lexicon().find(name);
		if (entry == null) return fail(source, "词库里没有这个名称");
		String text;
		if (!entry.answerable()) {
			text = "「" + entry.n + "」（" + entry.ty + "）只能猜，不能作为谜底：" + entry.why;
		} else {
			text = "「" + entry.n + "」（" + entry.ty + "）难度 " + manager.config().difficultyOf(entry)
					+ (manager.config().difficultyOf(entry) != entry.d ? "（默认 " + entry.d + "）" : "")
					+ " · 挑战：" + entry.ch.describe()
					+ " · " + (manager.isValidAnswer(entry) ? "可作为谜底" : manager.config().enabled(entry) ? "在本服务器上无效" : "已被禁用");
		}
		source.sendSuccess(() -> Texts.info(text), false);
		return 1;
	}
}
