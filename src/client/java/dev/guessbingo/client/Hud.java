package dev.guessbingo.client;

import dev.guessbingo.lex.GuessRow;
import dev.guessbingo.net.HudState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 左上角的猜词进度面板（原 bingo 棋盘的位置）：回合与谜底、各队比分、
 * 以及最近几次猜测的七列比对结果。配色沿用「猜词」数据包的大屏。
 */
public final class Hud {
	private static final int PANEL = 0xB4101014, HEADER = 0xFF2B2B33, PLAIN = 0xFF3A3A44;
	private static final int WHITE = 0xFFFFFFFF, BLACK = 0xFF000000, GRAY = 0xFFB0B0B8, GOLD = 0xFFFFC843, YELLOW = 0xFFFFFF55;
	private static final int MARGIN = 4, PAD = 3, GAP = 1, ROW = 11, LINE = 10;

	private Hud() {
	}

	/** 单元格底色，取自数据包大屏的配色。 */
	static int background(int kind) {
		return switch (kind) {
			case GuessRow.GREEN -> 0xFF2E7D32;
			case GuessRow.AMBER -> 0xFFF9A825;
			case GuessRow.YELLOW -> 0xFFFDD835;
			case GuessRow.ORANGE -> 0xFFFF9800;
			case GuessRow.CYAN -> 0xFF00838F;
			case GuessRow.BLUE -> 0xFF1976D2;
			case GuessRow.RED -> 0xFFC62828;
			default -> PLAIN;
		};
	}

	/** 绿、青、蓝、红用白字，黄、橙用黑字。 */
	static int foreground(int kind) {
		return switch (kind) {
			case GuessRow.AMBER, GuessRow.YELLOW, GuessRow.ORANGE -> BLACK;
			default -> WHITE;
		};
	}

	private record Segment(String text, int color) {
	}

	private static int width(Font font, List<Segment> line) {
		int w = 0;
		for (Segment segment : line) w += font.width(segment.text());
		return w;
	}

	private static String clip(Font font, String text, int maxWidth) {
		if (font.width(text) <= maxWidth) return text;
		String ellipsis = "…";
		return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(ellipsis))) + ellipsis;
	}

	private static String stars(int difficulty) {
		int d = Math.clamp(difficulty, 0, 5);
		return "★".repeat(d) + "☆".repeat(5 - d);
	}

	public static void extract(GuiGraphicsExtractor g, DeltaTracker tracker) {
		HudState s = ClientState.get();
		ClientConfig config = ClientConfig.get();
		if (s == null || !s.active || !config.enabled) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.getDebugOverlay().showDebugScreen()) return;
		Font font = mc.font;

		// ---- 文字行
		List<List<Segment>> lines = new ArrayList<>();
		lines.add(List.of(new Segment("猜词 Bingo · 第 " + s.round + "/" + s.rounds + " 回合 · " + stars(s.difficulty) + " " + s.difficulty + " 分", GOLD)));
		if (s.answer == null) {
			lines.add(List.of(new Segment("猜词中 · 已猜 " + s.guesses + " 次 · 按 G 猜词", WHITE)));
		} else {
			lines.add(List.of(new Segment("谜底：" + s.answer + (s.type == null ? "" : "（" + s.type + "）"), GOLD)));
			if (s.task != null) lines.add(List.of(new Segment("挑战：" + s.task, WHITE)));
		}
		if (!s.teams.isEmpty()) {
			List<Segment> scores = new ArrayList<>();
			for (HudState.Team team : s.teams) {
				if (!scores.isEmpty()) scores.add(new Segment("  ", WHITE));
				scores.add(new Segment(team.name + " " + team.score, 0xFF000000 | team.color));
			}
			lines.add(scores);
		}
		if (s.vote != null) {
			lines.add(List.of(new Segment("跳过投票：同意 " + s.vote.yes + "/" + s.vote.need + " · 反对 " + s.vote.no
					+ " · 剩 " + ClientState.voteSecondsLeft() + " 秒（/gb vote yes|no）", YELLOW)));
		}

		// ---- 表格列宽
		int columns = GuessRow.COLUMNS.length;
		int limit = config.maxColumnWidth;
		int[] widths = new int[columns];
		for (int c = 0; c < columns; c++) widths[c] = font.width(GuessRow.COLUMNS[c]) + PAD * 2;
		List<String[]> cellTexts = new ArrayList<>();
		for (GuessRow row : s.rows) {
			int nameWidth = PAD;
			for (GuessRow.Letter letter : row.letters) nameWidth += font.width(letter.c) + 2;
			widths[0] = Math.max(widths[0], nameWidth + PAD);
			String[] texts = new String[columns];
			for (int c = 1; c < columns && c - 1 < row.cells.size(); c++) {
				texts[c] = clip(font, row.cells.get(c - 1).display(), limit - PAD * 2);
				widths[c] = Math.max(widths[c], font.width(texts[c]) + PAD * 2);
			}
			cellTexts.add(texts);
		}
		int tableWidth = 3 + GAP;
		for (int w : widths) tableWidth += w + GAP;

		int contentWidth = s.rows.isEmpty() ? 0 : tableWidth;
		for (List<Segment> line : lines) contentWidth = Math.max(contentWidth, width(font, line));
		int panelWidth = contentWidth + MARGIN * 2;
		int panelHeight = MARGIN * 2 + lines.size() * LINE + (s.rows.isEmpty() ? 0 : 2 + (s.rows.size() + 1) * (ROW + GAP));

		// ---- 绘制：内容太宽时自动缩小，保证面板不超过屏幕宽度的指定比例
		float scale = config.scale;
		float maxWidth = g.guiWidth() * config.maxWidthFraction;
		if (panelWidth * scale > maxWidth) scale = maxWidth / panelWidth;
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(config.x, config.y);
		pose.scale(scale, scale);
		g.fill(0, 0, panelWidth, panelHeight, PANEL);

		int y = MARGIN;
		for (List<Segment> line : lines) {
			int x = MARGIN;
			for (Segment segment : line) {
				g.text(font, segment.text(), x, y, segment.color(), true);
				x += font.width(segment.text());
			}
			y += LINE;
		}

		if (!s.rows.isEmpty()) {
			y += 2;
			// 表头
			int x = MARGIN + 3 + GAP;
			for (int c = 0; c < columns; c++) {
				g.fill(x, y, x + widths[c], y + ROW, HEADER);
				g.text(font, GuessRow.COLUMNS[c], x + PAD, y + 2, GRAY, false);
				x += widths[c] + GAP;
			}
			y += ROW + GAP;

			for (int r = 0; r < s.rows.size(); r++) {
				GuessRow row = s.rows.get(r);
				String[] texts = cellTexts.get(r);
				// 最左侧的小色块：提交这次猜测的队伍
				g.fill(MARGIN, y, MARGIN + 3, y + ROW, PLAIN);
				g.fill(MARGIN, y + 3, MARGIN + 3, y + ROW - 3, teamColor(s, row.team));
				x = MARGIN + 3 + GAP;

				// 名称：单元格底色是名称整体的判色，每个字自己的底色是逐字提示
				g.fill(x, y, x + widths[0], y + ROW, background(row.nk));
				int lx = x + PAD;
				for (GuessRow.Letter letter : row.letters) {
					int lw = font.width(letter.c) + 2;
					if (letter.k != GuessRow.PLAIN) g.fill(lx, y + 1, lx + lw - 1, y + ROW - 1, background(letter.k));
					g.text(font, letter.c, lx + 1, y + 2, foreground(letter.k == GuessRow.PLAIN ? row.nk : letter.k), false);
					lx += lw;
				}
				x += widths[0] + GAP;

				for (int c = 1; c < columns; c++) {
					GuessRow.Cell cell = c - 1 < row.cells.size() ? row.cells.get(c - 1) : null;
					g.fill(x, y, x + widths[c], y + ROW, cell == null ? PLAIN : background(cell.k));
					if (cell != null && texts[c] != null) {
						int tx = x + (widths[c] - font.width(texts[c])) / 2;
						g.text(font, texts[c], tx, y + 2, foreground(cell.k), false);
					}
					x += widths[c] + GAP;
				}
				y += ROW + GAP;
			}
		}
		pose.popMatrix();
	}

	private static int teamColor(HudState s, String teamId) {
		for (HudState.Team team : s.teams) {
			if (team.id != null && team.id.equals(teamId)) return 0xFF000000 | team.color;
		}
		return GRAY;
	}
}
