package dev.guessbingo.game;

import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.UUID;

/**
 * 对局的“宿主”：提供队伍，并负责一局结束后的收尾。
 * 装有 bingo 模组时由它提供大厅、分队与换种子重开；否则退回到原版记分板队伍。
 */
public interface Host {
	record TeamInfo(String id, String name, int color, List<UUID> members) {
	}

	String label();

	/** 当前参赛的队伍（至少有一名成员）。 */
	List<TeamInfo> teams(MinecraftServer server);

	default TeamInfo teamOf(MinecraftServer server, UUID player) {
		for (TeamInfo team : teams(server)) {
			if (team.members().contains(player)) return team;
		}
		return null;
	}

	/** 能否由 /gb start 手动开局；不能时给出原因。 */
	default String manualStartBlocker(MinecraftServer server) {
		return null;
	}

	/** 对局开始时调用。 */
	List<Integer> onMatchStarted(MinecraftServer server, Match match) ;

	/**
	 * 对局结束时调用。
	 *
	 * @param endedByHost 为 true 表示是宿主自己先结束的（例如管理员执行了 /bingo end），不需要再通知它
	 */
	void onMatchEnded(MinecraftServer server, Match match, boolean endedByHost);
}
