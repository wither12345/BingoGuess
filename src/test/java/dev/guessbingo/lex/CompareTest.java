package dev.guessbingo.lex;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompareTest {
	static Lexicon lexicon;

	@BeforeAll
	static void load() {
		lexicon = Lexicon.loadBundled();
	}

	private static Entry e(String name) {
		Entry entry = lexicon.get(name);
		assertNotNull(entry, "词库缺少：" + name);
		return entry;
	}

	private static String kinds(List<GuessRow.Letter> letters) {
		return letters.stream().map(l -> String.valueOf(l.k)).collect(Collectors.joining());
	}

	@Test
	void lexiconDropsRemovedCategories() {
		assertTrue(lexicon.entries().size() > 2000);
		assertNull(lexicon.get("gamemode"), "指令类应已去除");
		assertNull(lexicon.get("万圣节更新"), "更新类应已去除");
		for (Entry entry : lexicon.entries()) {
			assertTrue(Lexicon.TYPES.contains(entry.ty), entry.n + " 的类型不应保留：" + entry.ty);
		}
	}

	@Test
	void sameNameIsAllGreen() {
		Entry a = e("林地府邸");
		GuessRow row = Compare.compare(lexicon, a, a);
		assertTrue(row.ok);
		assertEquals(GuessRow.GREEN, row.nk);
		assertEquals("1111", kinds(row.letters));
		for (GuessRow.Cell cell : row.cells) assertEquals(GuessRow.GREEN, cell.k, cell.x);
	}

	@Test
	void lettersUseBothAlignmentsThenBlue() {
		// 石头 对 石砖：首字位置相同为绿，“头”不在答案中为红
		assertEquals("12", kinds(Compare.letters(e("石头"), e("石砖"))));
		// 圆石 对 石头：“石”在答案中但左右对齐都不在同一位置，为蓝
		assertEquals("28", kinds(Compare.letters(e("圆石"), e("石头"))));
		// 右对齐：后两个字与较长答案的末尾对齐为绿
		assertEquals("211", kinds(Compare.letters(e("红砖块"), e("下界砖块"))));
		// 右对齐：较短的猜测与较长答案的末尾对齐
		assertEquals("11", kinds(Compare.letters(e("石砖"), e("苔石砖"))));
	}

	@Test
	void typeSuffixIsNotCounted() {
		Entry knockback = e("击退（附魔）");
		assertEquals(2, knockback.nameLength());
		List<GuessRow.Letter> letters = Compare.letters(knockback, e("锋利"));
		assertEquals(3, letters.size(), "两个字加一段类型括号");
		assertEquals(GuessRow.PLAIN, letters.get(2).k);
		assertEquals("（附魔）", letters.get(2).c);
	}

	@Test
	void versionArrowPointsAtAnswer() {
		Entry stone = e("石头");
		Entry mansion = e("林地府邸");
		GuessRow.Cell cell = Compare.compare(lexicon, stone, mansion).cells.get(0);
		assertEquals(1, cell.a, "林地府邸加入得更晚");
		assertEquals(-1, Compare.compare(lexicon, mansion, stone).cells.get(0).a);
	}

	@Test
	void typeGroupsGiveYellow() {
		GuessRow.Cell sameGroup = Compare.compare(lexicon, e("石头"), e("苹果")).cells.get(1);
		assertEquals(GuessRow.YELLOW, sameGroup.k, "方块与物品同组");
		GuessRow.Cell otherGroup = Compare.compare(lexicon, e("石头"), e("林地府邸")).cells.get(1);
		assertEquals(GuessRow.RED, otherGroup.k);
	}

	@Test
	void dimensionOverlap() {
		assertEquals(GuessRow.GREEN, Compare.compare(lexicon, e("石头"), e("苹果")).cells.get(5).k);
		assertEquals(GuessRow.RED, Compare.compare(lexicon, e("石头"), e("末地城")).cells.get(5).k);
	}

	@Test
	void colourVariantsAreEquivalentButShapesAreNot() {
		assertTrue(Compare.equivalent(e("白色床"), e("红色床")), "同形状不同颜色等同");
		assertFalse(Compare.equivalent(e("石头"), e("圆石")));
		assertTrue(Compare.equivalent(e("举臂纹样陶片"), e("佳酿纹样陶片")), "陶片系列等同");
	}

	@Test
	void searchRanksExactThenPrefix() {
		List<Entry> hits = lexicon.search("石头");
		assertFalse(hits.isEmpty());
		assertEquals("石头", hits.get(0).n);
		assertNotNull(lexicon.find("  石头　"), "首尾空白应被忽略");
		assertNotNull(lexicon.find("tnt"), "英文名不区分大小写");
	}

	@Test
	void everyAnswerHasAChallengeAndDifficulty() {
		int answerable = 0;
		for (Entry entry : lexicon.entries()) {
			if (entry.ch == null) {
				assertNotNull(entry.why, entry.n + " 不能出题却没有原因");
				continue;
			}
			answerable++;
			assertTrue(entry.d >= 1 && entry.d <= 5, entry.n + " 难度越界：" + entry.d);
			boolean hasTargets = "potion".equals(entry.ch.t)
					? entry.ch.items != null && !entry.ch.items.isEmpty() && entry.ch.potions != null && !entry.ch.potions.isEmpty()
					: entry.ch.ids != null && !entry.ch.ids.isEmpty();
			assertTrue(hasTargets, entry.n + " 的挑战没有目标 ID");
		}
		assertTrue(answerable > 1500);
	}
}
