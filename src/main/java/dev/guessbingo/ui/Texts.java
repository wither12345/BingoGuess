package dev.guessbingo.ui;

import dev.guessbingo.lex.GuessRow;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

/** 聊天栏文字的统一样式。全部用字面文本和 RGB 颜色，没装客户端模组的玩家也能正常显示。 */
public final class Texts {
	public static final int WHITE = 0xFFFFFF, GRAY = 0xAAAAAA, DARK_GRAY = 0x555555, GOLD = 0xFFAA00;
	public static final int GREEN = 0x55FF55, RED = 0xFF5555, YELLOW = 0xFFFF55, AQUA = 0x55FFFF;
	public static final int ORANGE = 0xFF9800, CYAN = 0x26C6DA, BLUE = 0x55AAFF;

	private Texts() {
	}

	public static MutableComponent text(String s, int color) {
		return Component.literal(s).withColor(color);
	}

	public static MutableComponent prefix() {
		return text("[猜词] ", GOLD);
	}

	/** 带前缀的普通提示。 */
	public static MutableComponent info(String s) {
		return prefix().append(text(s, WHITE));
	}

	public static MutableComponent warn(String s) {
		return prefix().append(text(s, RED));
	}

	public static MutableComponent good(String s) {
		return prefix().append(text(s, GREEN));
	}

	/** 判色编号对应的聊天栏文字颜色。 */
	public static int chatColor(int kind) {
		return switch (kind) {
			case GuessRow.GREEN -> GREEN;
			case GuessRow.AMBER, GuessRow.YELLOW -> YELLOW;
			case GuessRow.ORANGE -> ORANGE;
			case GuessRow.CYAN -> CYAN;
			case GuessRow.BLUE -> BLUE;
			case GuessRow.RED -> RED;
			default -> WHITE;
		};
	}

	public static String kindName(int kind) {
		return switch (kind) {
			case GuessRow.GREEN -> "绿";
			case GuessRow.AMBER, GuessRow.YELLOW -> "黄";
			case GuessRow.ORANGE -> "橙";
			case GuessRow.CYAN -> "青";
			case GuessRow.BLUE -> "蓝";
			case GuessRow.RED -> "红";
			default -> "";
		};
	}

	public static MutableComponent hover(MutableComponent component, String tip) {
		if (tip == null || tip.isEmpty()) return component;
		return component.withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(Component.literal(tip))));
	}

	/** 点击后以玩家身份执行指令（不带斜杠）。 */
	public static MutableComponent button(String label, int color, String command, String tip) {
		MutableComponent component = text("[" + label + "]", color)
				.withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand(command)));
		return hover(component, tip);
	}

	public static String stars(int difficulty) {
		int d = Math.max(0, Math.min(5, difficulty));
		return "★".repeat(d) + "☆".repeat(5 - d);
	}

	/** 名称列：开头的色块是名称整体的判色，每个字的颜色是逐字提示。 */
	public static MutableComponent name(GuessRow row) {
		MutableComponent out = Component.empty();
		out.append(hover(text("■", chatColor(row.nk)), "名称：" + row.nh));
		for (GuessRow.Letter letter : row.letters) {
			if (letter.k == GuessRow.PLAIN) {
				out.append(text(letter.c, GRAY));
				continue;
			}
			String tip = switch (letter.k) {
				case GuessRow.GREEN -> "左对齐或右对齐时字与位置相同";
				case GuessRow.BLUE -> "答案中有此字，左右对齐位置均不同";
				default -> "答案中无此字或已超出出现次数";
			};
			out.append(hover(text(letter.c, chatColor(letter.k)), tip));
		}
		return out;
	}

	/** 一次猜测的完整七列结果。列与列之间用细竖线分隔，尽量让一行放得下。 */
	public static MutableComponent row(GuessRow row) {
		MutableComponent out = name(row);
		for (int i = 0; i < row.cells.size(); i++) {
			GuessRow.Cell cell = row.cells.get(i);
			out.append(text("|", DARK_GRAY));
			String column = i + 1 < GuessRow.COLUMNS.length ? GuessRow.COLUMNS[i + 1] : "";
			out.append(hover(text(cell.display(), chatColor(cell.k)), column + "：" + cell.h));
		}
		return out;
	}

	/** 带提交者的一行：玩家名用队伍颜色，悬停显示队伍。 */
	public static MutableComponent guessLine(GuessRow row, String teamName, int teamColor) {
		return Component.empty()
				.append(hover(text(row.by, teamColor), teamName))
				.append(text(" ", WHITE))
				.append(row(row));
	}

	public static MutableComponent header() {
		MutableComponent out = Component.empty();
		for (int i = 0; i < GuessRow.COLUMNS.length; i++) {
			if (i > 0) out.append(text("|", DARK_GRAY));
			out.append(text(GuessRow.COLUMNS[i], GRAY));
		}
		return out;
	}
}
