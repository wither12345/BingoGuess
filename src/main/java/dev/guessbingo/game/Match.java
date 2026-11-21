package dev.guessbingo.game;

import dev.guessbingo.lex.GuessRow;
import dev.guessbingo.lex.wrapped.WrappedQuestion;

import java.util.*;

/** 一局游戏的全部状态。整体以 JSON 存进存档，服务器重启后可以接着玩。 */
public final class Match {
	public boolean active = false;
	/** 当前谜底的名称（猜中等同变种时会换成玩家猜的那个） */
	public String answer;
	/** 本回合全部猜测，按提交顺序 */
	public List<GuessRow> history = new ArrayList<>();
	public Map<String, String> teamNames = new LinkedHashMap<>();
	public Map<String, Integer> teamColors = new LinkedHashMap<>();
    public Map<Host.TeamInfo, Float> scores = new HashMap<>();

    private final List<WrappedQuestion> questionList = new ArrayList<>();

    public Map<Host.TeamInfo, Float> getScores() {
        return scores;
    }

    public void updateScores(Map<Host.TeamInfo, Float> winners){
        scores.clear();
        scores.putAll(winners);
    }

    public void setQuestions(List<WrappedQuestion> questions) {
        this.questionList.clear();
        this.questionList.addAll(questions);
    }

    public List<WrappedQuestion> getQuestions() {
        return questionList;
    }
}
