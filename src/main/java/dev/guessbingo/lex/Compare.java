package dev.guessbingo.lex;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 「猜词」数据包判定规则的 Java 移植：七列比对、名称逐字提示、等同规则。
 * 判色编号见 {@link GuessRow}。
 */
public final class Compare {
	private static final Map<String, Integer> TYPE_GROUPS = Map.of(
			"方块", 1, "物品", 1, "实体", 1,
			"指令", 2, "更新", 2,
			"附魔", 3, "状态效果", 3, "属性", 3,
			"生物群系", 4, "结构", 4);

	private Compare() {
	}

	/**
	 * 猜测是否等同于谜底：同名，或同属陶片 / 盔甲纹饰系列，或同一铜系列，
	 * 或同一颜色系列且名称字数相同。等同时按猜中处理，谜底改为玩家猜的那个词。
	 */
	public static boolean equivalent(Entry guess, Entry answer) {
		if (guess.n.equals(answer.n)) return true;
		if ("sherd".equals(guess.ef) && "sherd".equals(answer.ef)) return true;
		if ("trim".equals(guess.ef) && "trim".equals(answer.ef)) return true;
		if (!guess.eu.isEmpty() && guess.eu.equals(answer.eu)) return true;
		if (guess.ec.isEmpty() || !guess.ec.equals(answer.ec)) return false;
		return guess.nameLength() == answer.nameLength();
	}

	public static GuessRow compare(Lexicon lexicon, Entry guess, Entry answer) {
		GuessRow row = new GuessRow();
		row.name = guess.n;
		row.ok = guess.n.equals(answer.n);
		paintName(row, guess, answer);
		row.letters = letters(guess, answer);
		row.cells.add(version(guess, answer));
		row.cells.add(type(guess, answer));
		row.cells.add(classification(lexicon, "tags", "标签", guess.tg, answer.tg));
		row.cells.add(classification(lexicon, "subtags", "子标签", guess.st, answer.st));
		row.cells.add(length(guess, answer));
		row.cells.add(dimension(guess, answer));
		return row;
	}

	/** 名称：同名为绿，同细分主体与状态为蓝，同系列或材料为黄，命名成分或用途相关为橙，其余为红。 */
	private static void paintName(GuessRow row, Entry guess, Entry answer) {
		if (guess.n.equals(answer.n)) {
			row.nk = GuessRow.GREEN;
			row.nh = "相同";
			return;
		}
		int kind = GuessRow.RED;
		if (!guess.nb.isEmpty() && guess.nb.equals(answer.nb)) kind = GuessRow.YELLOW;
		if (kind == GuessRow.YELLOW && !guess.nv.isEmpty() && guess.nv.equals(answer.nv)) kind = GuessRow.BLUE;
		if (kind == GuessRow.RED) {
			for (String field : guess.nf) {
				if (answer.nf.contains(field)) {
					kind = GuessRow.ORANGE;
					break;
				}
			}
		}
		// 明确配对只提升档位：蓝优先于黄，黄优先于橙
		if (kind == GuessRow.RED && guess.o.contains(answer.n)) kind = GuessRow.ORANGE;
		if (kind != GuessRow.BLUE && guess.y.contains(answer.n)) kind = GuessRow.YELLOW;
		if (guess.b.contains(answer.n)) kind = GuessRow.BLUE;
		row.nk = kind;
		row.nh = switch (kind) {
			case GuessRow.BLUE -> "同一细分主体和处理状态，仅形态不同";
			case GuessRow.YELLOW -> "同系列或同一基础材料的不同变体";
			case GuessRow.ORANGE -> "名称主体、命名成分或用途相关";
			default -> "不同";
		};
	}

	/**
	 * 逐字提示：左对齐或右对齐时字与位置相同为绿；答案中有此字但两种对齐位置均不同为蓝；
	 * 无此字或超出出现次数为红。重复字优先分配绿色，再分配蓝色；末尾类型括号不参与。
	 */
	static List<GuessRow.Letter> letters(Entry guess, Entry answer) {
		int glen = guess.nameLength();
		int alen = answer.nameLength();
		int[] kinds = new int[glen];
		boolean[] used = new boolean[alen];
		int offset = alen - glen;

		for (int i = 0; i < glen; i++) {
			char c = guess.n.charAt(i);
			// 两个对齐位置中，下标较小的先检查
			int first = offset < 0 ? i + offset : i;
			int second = offset < 0 ? i : i + offset;
			if (claim(answer.n, alen, used, first, c) || claim(answer.n, alen, used, second, c)) {
				kinds[i] = GuessRow.GREEN;
			}
		}
		for (int i = 0; i < glen; i++) {
			if (kinds[i] == GuessRow.GREEN) continue;
			char c = guess.n.charAt(i);
			kinds[i] = GuessRow.RED;
			for (int idx = 0; idx < alen; idx++) {
				if (claim(answer.n, alen, used, idx, c)) {
					kinds[i] = GuessRow.BLUE;
					break;
				}
			}
		}

		List<GuessRow.Letter> out = new ArrayList<>(guess.n.length());
		for (int i = 0; i < glen; i++) out.add(new GuessRow.Letter(String.valueOf(guess.n.charAt(i)), kinds[i]));
		if (glen < guess.n.length()) out.add(new GuessRow.Letter(guess.n.substring(glen), GuessRow.PLAIN));
		return out;
	}

	private static boolean claim(String answer, int alen, boolean[] used, int idx, char c) {
		if (idx < 0 || idx >= alen || used[idx] || answer.charAt(idx) != c) return false;
		used[idx] = true;
		return true;
	}

	/** 加入版本：同版为绿，同大版本相差一小版为青、其余为黄，相邻大版本为橙，其余为红；箭头指向答案。 */
	private static GuessRow.Cell version(Entry guess, Entry answer) {
		int d = answer.vi - guess.vi;
		int groupDiff = answer.vg - guess.vg;
		int patchDiff = answer.vp - guess.vp;
		int kind = GuessRow.RED;
		if (Math.abs(groupDiff) == 1) kind = GuessRow.ORANGE;
		if (groupDiff == 0) kind = GuessRow.YELLOW;
		if (groupDiff == 0 && Math.abs(patchDiff) == 1) kind = GuessRow.CYAN;
		if (d == 0) kind = GuessRow.GREEN;
		int arrow = Integer.signum(d);
		String later = arrow > 0 ? "答案更晚" : "答案更早";
		String hover = switch (kind) {
			case GuessRow.GREEN -> "加入版本相同";
			case GuessRow.CYAN -> "相差一个小版本，" + later;
			case GuessRow.YELLOW -> "同一大版本，" + later;
			case GuessRow.ORANGE -> "相差一个大版本，" + later;
			default -> "不同，" + later;
		};
		return new GuessRow.Cell(guess.v, kind, arrow, hover);
	}

	/** 类型：相同为绿，同组的不同类型为黄，跨组为红。 */
	private static GuessRow.Cell type(Entry guess, Entry answer) {
		if (guess.ty.equals(answer.ty)) return new GuessRow.Cell(guess.ty, GuessRow.GREEN, 0, "类型相同");
		int g = TYPE_GROUPS.getOrDefault(guess.ty, 0);
		int a = TYPE_GROUPS.getOrDefault(answer.ty, 0);
		if (g > 0 && g == a) return new GuessRow.Cell(guess.ty, GuessRow.YELLOW, 0, "同组的不同类型");
		return new GuessRow.Cell(guess.ty, GuessRow.RED, 0, "类型不同");
	}

	private static GuessRow.Cell classification(Lexicon lexicon, String field, String label, String guess, String answer) {
		int kind = lexicon.classColor(field, guess, answer);
		String hover = switch (kind) {
			case GuessRow.GREEN -> label + "完全相同";
			case GuessRow.BLUE -> label + "完整对应且直接相近";
			case GuessRow.YELLOW -> "存在相同" + label;
			case GuessRow.ORANGE -> "仅存在相近或相关" + label;
			default -> label + "无关联";
		};
		return new GuessRow.Cell(guess, kind, 0, hover);
	}

	/** 字数：相同为绿，相差 1 字为黄，其余为红；箭头指向答案的字数。 */
	private static GuessRow.Cell length(Entry guess, Entry answer) {
		int g = guess.nameLength();
		int d = answer.nameLength() - g;
		int kind = d == 0 ? GuessRow.GREEN : Math.abs(d) == 1 ? GuessRow.YELLOW : GuessRow.RED;
		String hover;
		if (d == 0) hover = "字数相同";
		else if (d == 1) hover = "相差 1 字，答案字数更多";
		else if (d == -1) hover = "相差 1 字，答案字数更少";
		else hover = d > 0 ? "答案字数更多" : "答案字数更少";
		return new GuessRow.Cell(String.valueOf(g), kind, Integer.signum(d), hover);
	}

	/** 维度：集合相同为绿，有交集为黄，无交集为红；无与无为绿。 */
	private static GuessRow.Cell dimension(Entry guess, Entry answer) {
		if (guess.mk == answer.mk) return new GuessRow.Cell(guess.dm, GuessRow.GREEN, 0, "维度相同");
		if ((guess.mk & answer.mk) != 0) return new GuessRow.Cell(guess.dm, GuessRow.YELLOW, 0, "维度有交集");
		return new GuessRow.Cell(guess.dm, GuessRow.RED, 0, "维度没有交集");
	}
}
