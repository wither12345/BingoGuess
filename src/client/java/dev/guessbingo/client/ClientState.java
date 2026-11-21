package dev.guessbingo.client;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import dev.guessbingo.game.Host;
import dev.guessbingo.lex.wrapped.WrappedQuestion;
import dev.guessbingo.lex.wrapped.WrappedUnknown;
import dev.guessbingo.net.PushGuessResult;
import dev.guessbingo.net.ScoreData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 客户端保存的最新一份面板状态。 */
public final class ClientState {

	private static final List<Pair<WrappedQuestion, DisplayGroup>> questions = new ArrayList<>(25);
	private static final Map<Host.TeamInfo, Float> scores = new HashMap<>();


	private ClientState() {
	}

	/** 更新问题列表 */
	public static void init(List<Either<Integer, WrappedQuestion>> newQuestions) {
		ClientState.questions.clear();
		scores.clear();
		for(int i = 0; i < newQuestions.size(); i++){
			final int index = i;
			WrappedQuestion question = newQuestions.get(i).map(
					integer -> new WrappedUnknown(integer, index),
					wrapped -> wrapped
			);
			ClientState.questions.add(Pair.of(question, DisplayGroup.fromQuestion(question.unwrap())));
		}
	}

	/** 更新分数数据 */
	public static void updateScoreData(ScoreData payload){
		scores.clear();
		scores.putAll(payload.scores());
	}

	public static Map<Host.TeamInfo, Float> getScores() {
		return scores;
	}

	public static void updateQuestionState(PushGuessResult result){
		int index = result.index();
		if(questions.size() > index && questions.get(index).getFirst() instanceof WrappedUnknown wrappedUnknown){
			WrappedQuestion wrappedQuestion = wrappedUnknown.update(result.reveal(), result.guessResult());
			questions.set(index, Pair.of(wrappedQuestion, DisplayGroup.fromQuestion(wrappedQuestion.unwrap())));
		}
	}

	public static void completeQuestion(int index) {
    	questions.get(index).getFirst().complete();
	}

	public static List<Pair<WrappedQuestion, DisplayGroup>> getState() {
		return questions;
	}

	public static boolean active() {
		return true;
	}
}
