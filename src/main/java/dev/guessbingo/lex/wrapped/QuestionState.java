package dev.guessbingo.lex.wrapped;

import com.mojang.serialization.Codec;
import dev.guessbingo.game.Host;
import dev.guessbingo.lex.GuessResult;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

public class QuestionState {
    private final HashMap<Host.TeamInfo, Integer> contributors ;
    private final HashMap<Host.TeamInfo, Float> winners;
    private final List<GuessResult> guessResults;
    private int competition;
    private State state;

    public QuestionState(HashMap<Host.TeamInfo, Integer> contributors,
                         HashMap<Host.TeamInfo , Float> winners,
                         List<GuessResult> guessResults,
                         int competition, State state){
        this.contributors = contributors;
        this.winners = winners;
        this.guessResults = guessResults;
        this.competition = competition;
        this.state = state;
    }

    public QuestionState(State state, List<GuessResult> results){
        this(new HashMap<>(), new HashMap<>(), results, 0, state);
    }

    public QuestionState(){
        this(State.GUESSING, new ArrayList<>());
    }

    public static final StreamCodec<ByteBuf, QuestionState> STREAM_CODEC = StreamCodec.composite(
            State.STREAM_CODEC, QuestionState::getState,
            GuessResult.STREAM_CODEC.apply(ByteBufCodecs.list()), QuestionState::getGuessResults,
            QuestionState::new
    );

    public void putContributor(Host.TeamInfo teamId, Integer count) {
        if(contributors.containsKey(teamId)){
            contributors.put(teamId, contributors.get(teamId) + count);
        }
        else
            contributors.put(teamId, count);
    }

    public void put2TotalMap(Map<Host.TeamInfo, Float> map){
        for(Host.TeamInfo teamId : winners.keySet()){
            map.put(teamId, map.getOrDefault(teamId, 0.0f) + winners.get(teamId));
        }
    }

    public QuestionState putResult(GuessResult result){
        int contribute = 0;
        for(int i = 0; i < 5; i ++){
            if((competition & (1 << i)) == 0 && result.distance().get(i) == GuessResult.CompareDistance.CORRECT) {
                competition |= (1 << i);
                contribute ++ ;
            }
        }
        this.putContributor(result.team(), contribute);

        guessResults.add(result);
        if(guessResults.size() > 6)
            guessResults.removeFirst();
        return this;
    }

    public List<GuessResult> getGuessResults() {
        return guessResults;
    }

    public void putWinner(Host.TeamInfo teamId, int score) {
        winners.put(teamId, score * getScoreMultiply(contributors.getOrDefault(teamId, 0)));
    }

    public float getScoreMultiply(int count) {
        return 1 + count * 0.2f;
    }

    public void setState(State state) {
        this.state = state;
    }

    public State getState() {
        return state;
    }

    public enum State implements StringRepresentable {
        GUESSING("guessing"),
        COMPLETING("completing"),
        COMPLETED("completed");

        public static final Codec<State> CODEC = StringRepresentable.fromEnum(State::values);
        public static final IntFunction<State> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
        public static final StreamCodec<ByteBuf, State> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Enum::ordinal);

        private final String serializedName;

        State(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public @NonNull String getSerializedName() {
            return serializedName;
        }
    }
}