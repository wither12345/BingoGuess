package dev.guessbingo.lex.wrapped;

import dev.guessbingo.GuessBingo;
import dev.guessbingo.game.Host;
import dev.guessbingo.lex.GuessResult;
import dev.guessbingo.lex.Question;
import dev.guessbingo.net.CompleteData;
import dev.guessbingo.net.PushGuessResult;
import dev.guessbingo.net.ScoreData;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public abstract class WrappedQuestion {
    protected final Question question;
    protected final int index;
    protected final QuestionState state;

    public static final StreamCodec<ByteBuf, WrappedQuestion> STREAM_CODEC = StreamCodec.composite(
            Question.STREAM_CODEC, WrappedQuestion::unwrap,
            ByteBufCodecs.INT, WrappedQuestion::index,
            QuestionState.STREAM_CODEC, WrappedQuestion::getState,
            WrappedQuestion::get
    );

    protected WrappedQuestion(Question question, int index, QuestionState state) {
        this.question = question;
        this.index = index;
        this.state = state;
    }

    public static WrappedQuestion get(Question question, int index){
        return get(question, index, new QuestionState());
    }

    public static WrappedQuestion get(Question question, int index, QuestionState state){
        if(question.id().equals(Question.UNKNOWN_ID))
            return new WrappedUnknown(question, index, state);
        return switch (question.type()){
            case ITEM, BLOCK -> new WrappedItem(question, index, state);
            case ENTITY_TYPE -> new WrappedEntity(question, index, state);
            case ADVANCEMENT -> new WrappedAdvancement(question, index, state);
            default -> new WrappedUnknown(question, index, state);
        };
    }


    public QuestionState getState() {
        return state;
    }

    public QuestionState.State getCurrentState(){
        return state.getState();
    }

    public int index() {
        return index;
    }

    public PushGuessResult compare(Host.TeamInfo team, Question guess){
        GuessResult result = this.question.compare(team, guess);
        this.state.putResult(result);
        boolean flag = true;

        Question.Builder builder = new Question.Builder(question.difficulty());
        List<GuessResult.CompareDistance> distances = result.distance();

        if(distances.getFirst() == GuessResult.CompareDistance.CORRECT)
            builder.version(question.version());
        else flag = false;

        if(distances.get(1) == GuessResult.CompareDistance.CORRECT)
            builder.type(question.type());
        else flag = false;

        if(distances.get(2) == GuessResult.CompareDistance.CORRECT)
            builder.majorTag(question.majorTag());
        else flag = false;

        if(distances.get(3) == GuessResult.CompareDistance.CORRECT)
            builder.subTag(question.subTag());
        else flag = false;

        if(distances.get(4) == GuessResult.CompareDistance.CORRECT)
            builder.dimension(question.dimension());
        else flag = false;

        if(flag && this.question.id().equals(guess.id())) {
            state.setState(QuestionState.State.COMPLETING);
            builder.id(question.id());
        }
        return new PushGuessResult(index, result, builder.build());
    }

    public Question unwrap(){
        return question;
    }

    public abstract boolean testTrigger(ServerPlayer player,
                                     SimpleCriterionTrigger<? extends SimpleCriterionTrigger.SimpleInstance> trigger,
                                     Predicate<SimpleCriterionTrigger.SimpleInstance> matcher);

    public boolean test(ServerPlayer player){
        return false;
    }

    public int difficulty() {
        return question.difficulty();
    }

    public void complete() {
        this.getState().setState(QuestionState.State.COMPLETED);
    }

    public Map<Host.TeamInfo, Float> completeServer(MinecraftServer server, List<WrappedQuestion> questionList){
        complete();
        Map<Host.TeamInfo, Float> winners = new HashMap<>();

        CompleteData payload = new CompleteData(this.index());
        ScoreData scoreData = new ScoreData(winners);

        for(WrappedQuestion q : questionList){
            q.getState().put2TotalMap(winners);
        }

        server.getPlayerList().getPlayers().forEach(p -> {
            p.sendSystemMessage(Component.translatable("guessbingo.lore.completed", index + 1));
            ServerPlayNetworking.send(p, payload);
            ServerPlayNetworking.send(p, scoreData);
        });
        return winners;
    }

    public void complete(ServerPlayer player) {
        Host.TeamInfo info = GuessBingo.manager().host().teamOf(player);
        this.state.putWinner(info, question.difficulty());
        player.sendSystemMessage(Component.translatable("guessbingo.lore.complete", index + 1));
    }

    public void correct(ServerPlayer player) {
        player.sendSystemMessage(Component.translatable("guessbingo.lore.correct", index + 1));
        player.level().getServer().getPlayerList().getPlayers().forEach(p ->
                p.sendSystemMessage(Component.translatable("guessbingo.lore.achievable", index + 1)));
    }
}
