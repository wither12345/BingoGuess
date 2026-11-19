package dev.guessbingo.lex;

import java.util.List;

/** 谜底揭晓后要完成的挑战。字段直接对应 lexicon.json 里的 "ch"。 */
public final class Challenge {
	/** item / potion / enchant / effect / advancement / biome / structure / kill */
	public String t;
	public List<String> ids;
	/** 仅 potion：可接受的容器物品（药水、喷溅、滞留或药箭） */
	public List<String> items;
	/** 仅 potion：可接受的药水类型（含延长、加强版） */
	public List<String> potions;
	/** 仅 effect：瞬时效果，无法靠轮询发现，只能靠事件 */
	public boolean instant;

	/** 击杀类挑战必须在揭晓后发生，不做“揭晓时已满足”的检查。 */
	public boolean requiresFreshAction() {
		return "kill".equals(t);
	}

	public String describe() {
		return switch (t) {
			case "item" -> "获得该物品";
			case "potion" -> "获得该药水或药箭（强度、时长、使用方式不限）";
			case "enchant" -> "获得带有该附魔的任意物品";
			case "effect" -> "获得该状态效果";
			case "advancement" -> "达成该进度";
			case "biome" -> "到达该生物群系";
			case "structure" -> "到达该结构";
			case "kill" -> "击杀该生物";
			default -> "完成挑战";
		};
	}
}
