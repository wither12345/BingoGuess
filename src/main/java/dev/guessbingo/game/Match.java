package dev.guessbingo.game;

import dev.guessbingo.lex.GuessRow;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/** 一局游戏的全部状态。整体以 JSON 存进存档，服务器重启后可以接着玩。 */
public final class Match {
	/** 猜词阶段：谜底隐藏，即使已经拥有对应物品也不算完成 */
	public static final String GUESS = "guess";
	/** 挑战阶段：谜底已公开，谁先完成谁得分 */
	public static final String CHALLENGE = "challenge";

	public boolean active;
	public List<Question> questions = new ArrayList<>();
	/** 当前回合在 questions 中的下标 */
	public int index;
    private int lastIndex = -1;
	public String phase = GUESS;
	/** 当前谜底的名称（猜中等同变种时会换成玩家猜的那个） */
	public String answer;
	/** 本回合全部猜测，按提交顺序 */
	public List<GuessRow> history = new ArrayList<>();
	public Map<String, String> teamNames = new LinkedHashMap<>();
	public Map<String, Integer> teamColors = new LinkedHashMap<>();

    public Map<String, RoundResult> cachedResults = new LinkedHashMap<>();
	public List<RoundResult> results = new ArrayList<>();
	/** 独立模式：开局前各玩家的位置，结束时传送回去。UUID -> 维度与坐标 */
	public Map<String, ReturnPoint> returnPoints = new LinkedHashMap<>();
    private GuessRow.Progress progress = new GuessRow.Progress();
    private int resultSize = 0;
    private final Map<String, Float> scores = new LinkedHashMap<>();

    public void pushHistory(GuessRow guess, RoundResult result){
        if(index != lastIndex){
            lastIndex = index;
            this.progress = new GuessRow.Progress();
        }
        String team = guess.team;

        int progressVal = guess.getProgress(this.progress);
        result.putContributor(team, progressVal);
        history.add(guess);
    }

    /** 队伍 ID -> 得分 */
    public Map<String, Float> getScores(){
        if(resultSize == results.size())
            return scores;
        scores.clear();
        for(RoundResult result : results){
            result.put2Map(scores);
        }
        resultSize = results.size();
        return scores;
    }

	public static final class Question {
		public String name;
		public int difficulty;

		public Question(String name, int difficulty) {
			this.name = name;
			this.difficulty = difficulty;
		}
	}

	public static final class RoundResult {
		public int round;
		public String answer;
		public int points;
		public int guesses;
		public boolean skipped;
        /** 参与猜测次数 */
        private final HashMap<String, Integer> contributors = new HashMap<>();
		/** 得分的队伍 ID */
        private final HashMap<String , Float> winners = new HashMap<>();
		/** 完成挑战的玩家名，与 winners 一一对应 */
		public List<String> players = new ArrayList<>();

        public void putContributor(String teamId, Integer count) {
            if(contributors.containsKey(teamId)){
                contributors.put(teamId, contributors.get(teamId) + count);
            }
            else
                contributors.put(teamId, count);
        }

        public void put2Map(Map<String, Float> map){
            for(String teamId : winners.keySet()){
                map.put(teamId, map.getOrDefault(teamId, 0.0f) + winners.get(teamId));
            }
        }

        public void putWinner(String teamId, int score) {
            winners.put(teamId, score * getScoreMultiply(contributors.getOrDefault(teamId, 0)));
        }

        public float getScoreMultiply(String teamId){
            return getScoreMultiply(contributors.getOrDefault(teamId, 0));
        }

        public float getScoreMultiply(int count) {
            return 1 + count * 0.2f;
        }
	}

	public static final class ReturnPoint {
		public String dimension;
		public double x, y, z;
		public float yaw, pitch;
	}

    @Nullable
	public Question current() {
		return index >= 0 && index < questions.size() ? questions.get(index) : null;
	}

	public boolean guessing() {
		return active && GUESS.equals(phase);
	}

	public boolean challenging() {
		return active && CHALLENGE.equals(phase);
	}
}
