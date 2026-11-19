package dev.guessbingo.lex;

import java.util.List;

/** 词库中的一个词条。字段名与 lexicon.json 保持一致（缩写见 tools/build_lexicon.mjs）。 */
public final class Entry {
	/** 名称 */
	public String n;
	/** 类型：方块 / 物品 / 实体 / 进度 / 生物群系 / 附魔 / 状态效果 / 结构 */
	public String ty;
	/** 加入版本（显示文本） */
	public String v;
	/** 版本序号、所属大版本序号、大版本内的小版本号 */
	public int vi, vg, vp;
	/** 标签、子标签（显示文本，也是判色表的键） */
	public String tg = "无", st = "无";
	/** 维度（显示文本）与位掩码 */
	public String dm = "无";
	public int mk;
	/** 名称特征：主体、细分形态、命名字段，以及明确配对的蓝 / 黄 / 橙名单 */
	public String nb = "", nv = "";
	public List<String> nf = List.of(), b = List.of(), y = List.of(), o = List.of();
	/** 等同规则：颜色系列、陶片或纹饰系列、铜系列 */
	public String ec = "", ef = "", eu = "";
	/** 挑战；为 null 表示该词条只能猜、不能作为谜底 */
	public Challenge ch;
	/** 难度 1~5，同时也是分值 */
	public int d;
	/** 不能作为谜底的原因 */
	public String why;

	public boolean answerable() {
		return ch != null && d >= 1 && d <= 5;
	}

	/** 名称字数：与词条类型一致的末尾全角括号（如“击退（附魔）”）不计入。 */
	public int nameLength() {
		String suffix = "（" + ty + "）";
		int len = n.length();
		if (len >= suffix.length() && n.endsWith(suffix)) return len - suffix.length();
		return len;
	}

	@Override
	public String toString() {
		return n;
	}
}
