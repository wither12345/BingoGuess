package dev.guessbingo.lex.wrapped;

import dev.guessbingo.lex.GuessResult;
import dev.guessbingo.lex.Question;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Predicate;

public class WrappedUnknown extends WrappedQuestion{
    public WrappedUnknown(int diff, int index) {
        super(Question.Unknown(diff), index, new QuestionState());
    }

    public WrappedUnknown(Question question, int index, QuestionState state) {
        super(question, index, state);
    }

    public WrappedQuestion update(Question question, GuessResult result){
        return WrappedQuestion.get(question, this.index, this.state.putResult(result));
    }

    @Override
    public boolean testTrigger(ServerPlayer player, SimpleCriterionTrigger<? extends SimpleCriterionTrigger.SimpleInstance> trigger, Predicate<SimpleCriterionTrigger.SimpleInstance> matcher) {
        return false;
    }
}
