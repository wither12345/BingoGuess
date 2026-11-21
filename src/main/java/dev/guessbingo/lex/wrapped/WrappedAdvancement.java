package dev.guessbingo.lex.wrapped;

import dev.guessbingo.lex.Question;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Predicate;

public class WrappedAdvancement extends WrappedQuestion {
    protected WrappedAdvancement(Question question, int index, QuestionState state) {
        super(question, index, state);
    }

    @Override
    public boolean testTrigger(ServerPlayer player, SimpleCriterionTrigger<? extends SimpleCriterionTrigger.SimpleInstance> trigger, Predicate<SimpleCriterionTrigger.SimpleInstance> matcher) {
        return false;
    }

    @Override
    public boolean test(ServerPlayer player) {
        AdvancementHolder holder = player.level().getServer().getAdvancements().get(question.id());
        if(holder == null) return false;
        return player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    public boolean isType(AdvancementHolder holder){
        return this.question.id().equals(holder.id());
    }
}
