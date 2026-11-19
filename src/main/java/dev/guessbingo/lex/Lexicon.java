package dev.guessbingo.lex;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 词库：全部可猜词条、标签判色表，以及按名称查找和搜索。不依赖任何游戏类，便于单独测试。 */
public final class Lexicon {
	public static final List<String> TYPES = List.of("方块", "物品", "实体", "进度", "生物群系", "附魔", "状态效果", "结构");

	private static final class Data {
		int format;
		String minecraft;
		List<Entry> entries;
		Map<String, Map<String, Map<String, Integer>>> classColors;
	}

	private final List<Entry> entries;
	private final Map<String, Entry> byName = new LinkedHashMap<>();
	private final Map<String, Map<String, Map<String, Integer>>> classColors;

	private Lexicon(Data data) {
		this.entries = List.copyOf(data.entries);
		this.classColors = data.classColors == null ? Map.of() : data.classColors;
		for (Entry e : entries) byName.put(e.n, e);
	}

	public static Lexicon load(Reader reader) {
		Data data = new Gson().fromJson(reader, new TypeToken<Data>() { }.getType());
		if (data == null || data.entries == null || data.entries.isEmpty()) throw new IllegalStateException("词库为空");
		return new Lexicon(data);
	}

	public static Lexicon loadBundled() {
		try (InputStream in = Lexicon.class.getResourceAsStream("/guessbingo/lexicon.json")) {
			if (in == null) throw new IllegalStateException("找不到内置词库 /guessbingo/lexicon.json");
			return load(new InputStreamReader(in, StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new IllegalStateException("读取词库失败", e);
		}
	}

	public List<Entry> entries() {
		return entries;
	}

	public Entry get(String name) {
		return name == null ? null : byName.get(name);
	}

	/** 去掉首尾空白（含全角空格），名称内部保持原样。 */
	public static String normalize(String query) {
		if (query == null) return "";
		int start = 0, end = query.length();
		while (start < end && isBlank(query.charAt(start))) start++;
		while (end > start && isBlank(query.charAt(end - 1))) end--;
		return query.substring(start, end);
	}

	private static boolean isBlank(char c) {
		return Character.isWhitespace(c) || c == 0x3000;
	}

	/** 精确匹配；英文名不区分大小写。 */
	public Entry find(String query) {
		String q = normalize(query);
		Entry exact = byName.get(q);
		if (exact != null) return exact;
		String lower = q.toLowerCase(Locale.ROOT);
		for (Entry e : entries) {
			if (e.n.toLowerCase(Locale.ROOT).equals(lower)) return e;
		}
		return null;
	}

	/** 按关键词查候选：同名优先，其次前缀，再按名称长度和名称排序。 */
	public List<Entry> search(String query) {
		String q = normalize(query).toLowerCase(Locale.ROOT);
		List<Entry> out = new ArrayList<>();
		if (q.isEmpty()) return out;
		for (Entry e : entries) {
			if (e.n.toLowerCase(Locale.ROOT).contains(q)) out.add(e);
		}
		out.sort(Comparator
				.comparingInt((Entry e) -> rank(e, q))
				.thenComparingInt(e -> e.n.length())
				.thenComparing(e -> e.n));
		return out;
	}

	private static int rank(Entry e, String q) {
		String name = e.n.toLowerCase(Locale.ROOT);
		if (name.equals(q)) return 0;
		if (name.startsWith(q)) return 1;
		return 2;
	}

	/** 标签或子标签一列的判色；未列出的组合为红。 */
	public int classColor(String field, String guess, String answer) {
		Map<String, Map<String, Integer>> table = classColors.get(field);
		if (table == null) return GuessRow.RED;
		Map<String, Integer> row = table.get(guess);
		if (row == null) return GuessRow.RED;
		Integer kind = row.get(answer);
		return kind == null ? GuessRow.RED : kind;
	}
}
