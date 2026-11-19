package dev.guessbingo.net;

import dev.guessbingo.lex.GuessRow;

import java.util.ArrayList;
import java.util.List;

/** 服务端同步给客户端、用于绘制左上角面板的全部信息。以 JSON 传输。 */
public final class HudState {
	public boolean active;
	/** 当前回合（从 1 开始）与总回合数 */
	public int round, rounds;
	public int difficulty;
	/** guess / challenge，见 {@link dev.guessbingo.game.Match} */
	public String phase = "guess";
	/** 谜底；猜词阶段为 null */
	public String answer;
	public String type;
	/** 挑战说明，例如“到达该结构” */
	public String task;
	public List<Team> teams = new ArrayList<>();
	/** 最近若干次猜测，旧的在前 */
	public List<GuessRow> rows = new ArrayList<>();
	/** 本回合猜测总数 */
	public int guesses;
	public Vote vote;

	public static final class Team {
		public String id, name;
		public int color;
		public float score;
	}

	public static final class Vote {
		public int yes, no, need, secondsLeft;
	}
}
