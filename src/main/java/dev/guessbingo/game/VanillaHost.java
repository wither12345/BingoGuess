package dev.guessbingo.game;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.TeamColor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 没有 bingo 模组时的独立模式：队伍取自原版记分板（/team），用 /gb start 开局。
 * 一局结束后把参赛玩家传送回开局前的位置，并清空物品栏、经验、状态效果和进度；
 * 这个模式不会更换世界种子。
 */
public final class VanillaHost implements Host {
	private static final int[] PALETTE = {0xFF5555, 0x5555FF, 0x55FF55, 0xFFFF55, 0x55FFFF, 0xFF55FF, 0xFFAA00, 0xAAAAAA};

	@Override
	public String label() {
		return "独立模式（原版记分板队伍）";
	}

	@Override
	public List<TeamInfo> teams(MinecraftServer server) {
		List<TeamInfo> out = new ArrayList<>();
		int index = 0;
		for (PlayerTeam team : server.getScoreboard().getPlayerTeams()) {
			List<UUID> members = new ArrayList<>();
			for (String name : team.getPlayers()) {
				ServerPlayer player = server.getPlayerList().getPlayerByName(name);
				if (player != null) members.add(player.getUUID());
			}
			int fallback = PALETTE[index % PALETTE.length];
			index++;
			if (members.isEmpty()) continue;
			int color = team.getColor().map(TeamColor::rgb).orElse(fallback);
			out.add(new TeamInfo(team.getName(), team.getDisplayName().getString(), color, members));
		}
		return out;
	}

	@Override
	public List<Integer> onMatchStarted(MinecraftServer server, Match match) {
		return List.of(1,1,1,1,1);
	}

	@Override
	public void onMatchEnded(MinecraftServer server, Match match, boolean endedByHost) {
	}
}
