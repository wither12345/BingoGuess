package dev.guessbingo.client;

import com.mojang.datafixers.util.Pair;
import dev.guessbingo.GuessBingo;
import dev.guessbingo.data.Attachments;
import dev.guessbingo.game.Host;
import dev.guessbingo.lex.GuessResult;
import dev.guessbingo.lex.GuessRow;
import dev.guessbingo.lex.Question;
import dev.guessbingo.lex.StringCompareResult;
import dev.guessbingo.lex.wrapped.QuestionState;
import dev.guessbingo.lex.wrapped.WrappedQuestion;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;

/**
 * 左上角的猜词进度面板（原 bingo 棋盘的位置）：回合与谜底、各队比分、
 * 以及最近几次猜测的七列比对结果。配色沿用「猜词」数据包的大屏。
 */
public final class Hud {
	private static final int PANEL = 0xB4101014, HEADER = 0xFF2B2B33, PLAIN = 0xFF3A3A44;
	private static final int WHITE = 0xFFFFFFFF, BLACK = 0xFF000000, GRAY = 0xFFB0B0B8, GOLD = 0xFFFFC843, YELLOW = 0xFFFFFF55;
	private static final int MARGIN = 4, PAD = 3, GAP = 1, ROW = 11, LINE = 10;
	private static final Identifier SLOT = Identifier.fromNamespaceAndPath(GuessBingo.MOD_ID, "slot");
	private static final Identifier UNKNOWN = Identifier.fromNamespaceAndPath(GuessBingo.MOD_ID, "unknown");
	private static final Identifier SELECTING = Identifier.fromNamespaceAndPath(GuessBingo.MOD_ID, "selecting");
	private static final Identifier COMPLETE = Identifier.fromNamespaceAndPath(GuessBingo.MOD_ID, "complete");
	private static final String[] COLUMNS = {"name", "identifier", "version", "type", "tags", "subtags", "dimension"};

	private static final Map<Question, String> cathedNames = new HashMap<>();

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

		List<Pair<WrappedQuestion, DisplayGroup>> state = ClientState.getState();
		int select = Minecraft.getInstance().player.getAttachedOrElse(Attachments.SELECTING_ATTACHMENT, 0);

		int size = state.size();
		int side = getSide(size);
		for(int i = 0; i < side; i ++)
			for(int j = 0; j < side; j ++){
				int x0 = j * 18 + 15;
				int y0 = i * 18 + 5;
				int index = i * side + j;
				g.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, x0, y0, 18, 18);
				if(index >= size) continue;
				int diff = state.get(index).getFirst().difficulty();
				ItemStack itemStack = state.get(index).getSecond().itemStack();
				if(itemStack.is(Items.AIR))
					g.blitSprite(RenderPipelines.GUI_TEXTURED, UNKNOWN, x0 + 1, y0 + 1, 16, 16);
				else
					g.item(itemStack, x0 + 1, y0 + 1);

				if(index == select){
					g.blitSprite(RenderPipelines.GUI_TEXTURED, SELECTING, x0, y0, 18, 18);
				}

				int color = switch (diff){
					case 2 -> 0xff00ff00;
					case 3 -> 0xffffff00;
					case 4 -> 0xffff8800;
					case 5 -> 0xffff0000;
					default -> 0xffffffff;
				};
				g.text(Minecraft.getInstance().font, String.valueOf(diff), x0 + 11, y0 + 9, color);

				if(state.get(index).getFirst().getCurrentState() == QuestionState.State.COMPLETED)
					g.blitSprite(RenderPipelines.GUI_TEXTURED, COMPLETE, x0 + 1, y0 + 1, 16, 16);
			}

		int xOff = side * 18;

		ClientConfig config = ClientConfig.get();
		/*
		if (s == null) return;
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

		 */
		if(state.isEmpty()) return;
		g.pose().pushMatrix();
		g.pose().translate(xOff, 0);
		g.pose().scaleAround(0.5f, xOff, 0);
		int yOff = extractScores(g);
		extractQuestionState(g, state.get(select).getFirst().getState(), yOff);
		g.pose().popMatrix();
		// ---- 表格列宽
		/*
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
		g.fill(0, xOff, panelWidth, panelHeight + xOff, PANEL);

		int y = MARGIN + xOff;
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

		 */
	}

	/** 分数行：按队伍名排序，逐段「队名 + 分数」用队伍色绘制，返回下一内容的 y 坐标。 */
	private static int extractScores(GuiGraphicsExtractor graphics) {
		Map<Host.TeamInfo, Float> scores = ClientState.getScores();
		if (scores.isEmpty()) return 0;
		Font font = Minecraft.getInstance().font;
		List<Map.Entry<Host.TeamInfo, Float>> entries = new ArrayList<>(scores.entrySet());
		entries.sort(Comparator.comparing(a -> a.getKey().name()));
		List<Segment> line = new ArrayList<>();
		for (Map.Entry<Host.TeamInfo, Float> entry : entries) {
			if (!line.isEmpty()) line.add(new Segment("   ", WHITE));
			line.add(new Segment(entry.getKey().name() + " " + formatScore(entry.getValue()), 0xFF000000 | entry.getKey().color()));
		}
		int x = MARGIN + 3;
		for (Segment segment : line) {
			graphics.text(font, segment.text(), x, 0, segment.color(), true);
			x += font.width(segment.text());
		}
		return 0 + LINE;
	}

	/** 分数按整数显示，含小数时才保留一位。 */
	private static String formatScore(float score) {
		return score == Math.floor(score) ? String.valueOf((int) score) : String.format("%.1f", score);
	}

	private static void extractQuestionState(GuiGraphicsExtractor graphics, QuestionState state, int y) {
		List<GuessResult> results = state.getGuessResults();
		if (results.isEmpty()) return;
		Font font = Minecraft.getInstance().font;
		int columns = COLUMNS.length;

		// ---- 列宽：表头文字兜底，其余按 guess 属性文本放宽（不再裁剪，不用 Limit）
		String[] names = new String[results.size()];
		String[] ids = new String[results.size()];
		String[][] cellTexts = new String[results.size()][5];
		int[] widths = new int[columns];
		for (int c = 0; c < columns; c++) widths[c] = font.width(COLUMNS[c]) + PAD * 2;
		for (int r = 0; r < results.size(); r++) {
			Question guess = results.get(r).guess();
			names[r] = displayName(guess);
			widths[0] = Math.max(widths[0], font.width(names[r]) + PAD * 2);
			ids[r] = guess.id().toString();
			widths[1] = Math.max(widths[1], PAD * 2 + font.width(ids[r]));
			cellTexts[r][0] = guess.version().version();
			cellTexts[r][1] = guess.type().getSerializedName();
			cellTexts[r][2] = joinSlash(guess.majorTag());
			cellTexts[r][3] = joinSlash(guess.subTag());
			cellTexts[r][4] = joinSlashDimension(guess.dimension());
			for (int d = 0; d < 5; d++)
				widths[d + 2] = Math.max(widths[d + 2], font.width(cellTexts[r][d]) + PAD * 2);
		}

		// ---- 表头
		int headX = MARGIN + 3 + GAP;
		for (int c = 0; c < columns; c++) {
			graphics.fill(headX, y, headX + widths[c], y + ROW, HEADER);
			graphics.text(font, COLUMNS[c], headX + PAD, y + 2, GRAY, false);
			headX += widths[c] + GAP;
		}
		y += ROW + GAP;

		// ---- 数据行
		for (int r = 0; r < results.size(); r++) {
			GuessResult result = results.get(r);
			// 最左侧小色块：提交这次猜测的队伍
			graphics.fill(MARGIN, y, MARGIN + 3, y + ROW, PLAIN);
			graphics.fill(MARGIN, y + 3, MARGIN + 3, y + ROW - 3, 0xFF000000 | result.team().color());
			int x = MARGIN + 3 + GAP;

			// name 列：猜测的显示名
			graphics.fill(x, y, x + widths[0], y + ROW, PLAIN);
			graphics.text(font, names[r], x + PAD, y + 2, WHITE, false);
			x += widths[0] + GAP;

			// identifier 列：逐字渲染猜测的 id，字形底色沿用逐字提示状态
			graphics.fill(x, y, x + widths[1], y + ROW, PLAIN);
			int lx = x + PAD;
			List<StringCompareResult.StringState> letters = result.letters();
			for (int i = 0; i < ids[r].length(); i++) {
				char c = ids[r].charAt(i);
				int lw = font.width(String.valueOf(c));
				int kind = i < letters.size() ? letterKind(letters.get(i)) : GuessRow.PLAIN;
				if (kind != GuessRow.PLAIN) graphics.fill(lx, y + 1, lx + lw, y + ROW - 1, background(kind));
				graphics.text(font, String.valueOf(c), lx + 1, y + 2, foreground(kind), false);
				lx += lw;
			}
			x += widths[1] + GAP;

			// version/type/tags/subtags/dimension：底色用 CompareDistance 自带颜色，文字按 guess 属性原样
			for (int d = 0; d < 5 && d < result.distance().size(); d++) {
				GuessResult.CompareDistance distance = result.distance().get(d);
				graphics.fill(x, y, x + widths[d + 2], y + ROW, distance.color());
				String text = cellTexts[r][d];
				if (!text.isEmpty()) {
					int tx = x + (widths[d + 2] - font.width(text)) / 2;
					graphics.text(font, text, tx, y + 2, onColor(distance.color()), false);
				}
				x += widths[d + 2] + GAP;
			}
			y += ROW + GAP;
		}
	}

	/** 猜测题目的显示名，取 DisplayGroup 的第一个组件。 */
	private static String displayName(Question question) {
		if(cathedNames.containsKey(question))
			return cathedNames.get(question);
		if(cathedNames.size() > 10)
			cathedNames.clear();
		List<Component> components = DisplayGroup.fromQuestion(question).components();
		String name = components.isEmpty() ? "" : components.getFirst().getString();
		cathedNames.put(question, name);
		return name;
	}

	private static String joinSlashDimension(List<Identifier> dimensions) {
		StringBuilder sb = new StringBuilder();
		for (Identifier value : dimensions) {
			if (!sb.isEmpty()) sb.append('/');
			sb.append(Component.translatable("dimension." + value.getNamespace() + '.' + value.getPath()).getString());
		}
		return sb.toString();
	}

	/** 把列表元素用 / 连接成一段原样文本。 */
	private static String joinSlash(List<?> values) {
		StringBuilder sb = new StringBuilder();
		for (Object value : values) {
			if (!sb.isEmpty()) sb.append('/');
			sb.append(value);
		}
		return sb.toString();
	}

	/** 底色偏亮返回黑字，否则返回白字，保证单元格文字可读。 */
	private static int onColor(int rgba) {
		int r = (rgba >> 16) & 0xFF, g = (rgba >> 8) & 0xFF, b = rgba & 0xFF;
		return (r * 299 + g * 587 + b * 114) / 1000 > 160 ? BLACK : WHITE;
	}

	/** 逐字提示状态对应的判色，供 background/foreground 使用。 */
	private static int letterKind(StringCompareResult.StringState letter) {
		return switch (letter) {
			case CORRECT -> GuessRow.GREEN;
			case WRONG_POS -> GuessRow.BLUE;
			case INCORRECT -> GuessRow.RED;
		};
	}

	private static int getSide(int s){
		for(int i = 0; i < 5 ; i ++){
			if(i * i >= s)
				return i;
		}
		return 5;
	}

}