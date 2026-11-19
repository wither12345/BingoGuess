package dev.guessbingo.ui;

import dev.guessbingo.GuessBingo;
import dev.guessbingo.game.Match;
import dev.guessbingo.game.MatchManager;
import dev.guessbingo.lex.Entry;
import dev.guessbingo.lex.GuessRow;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 原版对话框界面（26.x 的 dialog 系统）。由服务端构造并下发，玩家不需要安装任何模组。
 * 猜词输入框是数据包里的 guessbingo:guess，挂在“快捷操作”（默认按 G）下；其余界面在这里动态生成。
 */
public final class Dialogs {
	public static final Identifier INPUT = Identifier.fromNamespaceAndPath(GuessBingo.MOD_ID, "guess");
	private static final int WIDTH = 320;
	/** 候选名称一页最多显示多少个 */
	public static final int MAX_CANDIDATES = 40;

	private Dialogs() {
	}

	/** 打开猜词输入框；数据包未加载时返回 false。 */
	public static boolean openInput(ServerPlayer player) {
		Optional<Holder.Reference<Dialog>> dialog = player.level().getServer().registryAccess()
				.lookupOrThrow(Registries.DIALOG).get(INPUT);
		if (dialog.isEmpty()) return false;
		player.openDialog(dialog.get());
		return true;
	}

	private static CommonDialogData common(String title, List<DialogBody> body) {
		return new CommonDialogData(Component.literal(title), Optional.empty(), true, false, DialogAction.CLOSE, body, List.of());
	}

	private static DialogBody line(Component text) {
		return new PlainMessage(text, WIDTH);
	}

	private static ActionButton button(String label, String tooltip, int width, String command) {
		CommonButtonData data = tooltip == null
				? new CommonButtonData(Component.literal(label), width)
				: new CommonButtonData(Component.literal(label), Optional.of(Component.literal(tooltip)), width);
		return new ActionButton(data, command == null ? Optional.empty() : Optional.of(new StaticAction(new ClickEvent.RunCommand(command))));
	}

	private static void notice(ServerPlayer player, String title, List<DialogBody> body) {
		player.openDialog(Holder.direct(new NoticeDialog(common(title, body), button("关闭", null, 150, null))));
	}

	/** 查询结果：点击候选名称即提交猜测。 */
	public static void showCandidates(ServerPlayer player, String query, List<Entry> candidates) {
		int shown = Math.min(candidates.size(), MAX_CANDIDATES);
		List<ActionButton> buttons = new ArrayList<>();
		for (Entry entry : candidates.subList(0, shown)) {
			buttons.add(button(entry.n, entry.ty + " · " + entry.v, 150, "guess " + entry.n));
		}
		String summary = "包含「" + query + "」的名称共 " + candidates.size() + " 个"
				+ (candidates.size() > shown ? "，这里显示前 " + shown + " 个，可以输入更长的关键词缩小范围" : "")
				+ "。点击名称即提交猜测。";
		List<DialogBody> body = List.of(line(Component.literal(summary)));
		ActionButton back = button("返回输入", null, 150, "guess");
		player.openDialog(Holder.direct(new MultiActionDialog(common("查询结果", body), buttons, Optional.of(back), 2)));
	}

	/** 本回合全部猜测记录。 */
	public static void showHistory(ServerPlayer player, MatchManager manager) {
		Match match = manager.match();
		List<DialogBody> body = new ArrayList<>();
		if (!match.active) {
			body.add(line(Component.literal("现在没有进行中的对局。")));
		} else {
			body.add(line(Texts.text("第 " + (match.index + 1) + "/" + match.questions.size() + " 回合，共 " + match.history.size() + " 次猜测", Texts.GOLD)));
			body.add(line(Texts.header()));
			if (match.history.isEmpty()) body.add(line(Texts.text("还没有人猜过", Texts.GRAY)));
			for (GuessRow row : match.history) {
				body.add(line(Texts.guessLine(row, manager.teamName(row.team), manager.teamColor(row.team))));
			}
		}
		notice(player, "历史记录", body);
	}

	public static List<String> rules() {
		return List.of(
				"每回合有一个隐藏的谜底。所有人共享猜词进度：任何一名参赛玩家猜中，谜底就对全员公开。",
				"谜底公开之前，即使你已经拥有对应的物品、站在对应的地点，也不算完成；公开之后第一个完成挑战的队伍得分，分值等于难度（1~5）。",
				"公开时已经满足条件的队伍各得一次分，并立刻进入下一回合。击杀类谜底必须在公开之后击杀才算。",
				"挑战内容：方块、物品——获得它（氧化程度、是否涂蜡、纹样图案、药水的强度时长和使用方式不作区分；颜色、加工形态、木材种类要区分）；"
						+ "实体——击杀该生物，非生物实体则获得它的物品形式；结构、生物群系——到达那里；附魔——获得带该附魔的任意物品；"
						+ "状态效果——获得该效果；进度——达成该进度。",
				"按 G（快捷操作键）打开猜词窗口，输入名称后选择“提交猜测”，名称不完整时选择“查询”查找候选。也可以直接输入 /guess <名称>。",
				"每次猜测给出七列提示：名称、加入版本、类型、标签、子标签、字数、维度。绿色表示相同；"
						+ "名称列蓝色为同一主体仅形态不同，黄色为同系列或同材料，橙色为名称成分或用途相关，红色为无关联。",
				"名称逐字提示：左对齐或右对齐时字与位置相同为绿，答案中有此字但位置不同为蓝，没有此字为红；末尾的类型括号不参与。",
				"加入版本：同一大版本中相差一个小版本为青、其余为黄，相差一个大版本为橙；↑表示答案更晚，↓表示更早。"
						+ "类型：同组的不同类型为黄，四组为“方块、物品、实体”“附魔、状态效果”“生物群系、结构”和进度。字数：相差 1 字为黄，箭头指向答案的字数。维度：有交集为黄。",
				"颜色系列、陶片、盔甲纹饰、不同氧化程度的同一铜制品互相等同：猜到其中一个即算猜中，谜底改为你猜的那个。",
				"管理员可以发起跳过投票，同意人数达到在线参赛人数的一半即跳过本回合，无人得分。");
	}

	public static void showRules(ServerPlayer player) {
		List<DialogBody> body = new ArrayList<>();
		for (String rule : rules()) body.add(line(Component.literal(rule)));
		notice(player, "猜词 Bingo 玩法说明", body);
	}
}
