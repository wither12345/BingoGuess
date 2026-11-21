package dev.guessbingo.lex;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.guessbingo.GuessBingo;
import dev.guessbingo.game.Host;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record Question(Identifier id, Type type, int difficulty, Version version, List<String> majorTag, List<String> subTag, List<Identifier> dimension) {
    public static final Identifier UNKNOWN_ID = Identifier.fromNamespaceAndPath(GuessBingo.MOD_ID, "unknown");
    public static final Codec<Question> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("id").forGetter(Question::id),
            Type.CODEC.fieldOf("type").forGetter(Question::type),
            Codec.INT.fieldOf("difficulty").forGetter(Question::difficulty),
            Version.CODEC.fieldOf("version").forGetter(Question::version),
            Codec.STRING.listOf().fieldOf("major_tag").forGetter(Question::majorTag),
            Codec.STRING.listOf().fieldOf("sub_tag").forGetter(Question::subTag),
            Identifier.CODEC.listOf().fieldOf("dimension").forGetter(Question::dimension)
    ).apply(instance, Question::new));

    public static final StreamCodec<ByteBuf, Question> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, Question::id,
            Type.STREAM_CODEC, Question::type,
            ByteBufCodecs.VAR_INT, Question::difficulty,
            Version.STREAM_CODEC, Question::version,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Question::majorTag,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Question::subTag,
            Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()), Question::dimension,
            Question::new
    );

    public static Question Unknown(int difficulty) {
        return new Question(UNKNOWN_ID, Type.UNKNOWN, difficulty, Version.UNKNOWN, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    public RawQuestion toRaw() {
        return new RawQuestion(id, type);
    }

    public Question reveal(Question question) {
        if (!question.id.equals(UNKNOWN_ID))
            return question;
        return new Question(
                this.id,
                question.type() == Type.UNKNOWN ? this.type : question.type(),
                this.difficulty,
                question.version.equals(Version.UNKNOWN) ? this.version : question.version,
                question.majorTag.isEmpty() ? this.majorTag : question.majorTag,
                question.subTag.isEmpty() ? this.subTag : question.subTag,
                question.dimension.isEmpty() ? this.dimension : question.dimension
        );
    }

    /**
     * 对比猜测与谜底
     *
     * @param team    猜测团队
     * @param guess   猜测结果
     * @return 猜测结果
     */
    public GuessResult compare(Host.TeamInfo team, Question guess) {
        List<GuessResult.CompareDistance> distances = new ArrayList<>(6);
        List<StringCompareResult.StringState> letters = StringCompareResult.letters(this.id.toString(), guess.id.toString());

        distances.add(this.version.compareTo(guess.version));
        distances.add(this.type == guess.type ? GuessResult.CompareDistance.CORRECT : GuessResult.CompareDistance.FAR_OFF);
        distances.add(compareList(this.majorTag, guess.majorTag));
        distances.add(compareList(this.subTag, guess.subTag));
        distances.add(compareList(this.dimension, guess.dimension));

        return new GuessResult(team, guess, letters, distances);
    }

    private static GuessResult.CompareDistance compareList(List<?> answer, List<?> guess) {
        int match = 0;
        for (Object o : answer) {
            if (guess.contains(o))
                match++;
        }
        if (match == answer.size() && match == guess.size())
            return GuessResult.CompareDistance.CORRECT;
        if (match > 0) {
            if (match == guess.size())
                return GuessResult.CompareDistance.CLOSE;
            return GuessResult.CompareDistance.SLIGHTLY_CLOSE;
        }
        return GuessResult.CompareDistance.FAR_OFF;
    }

    public static class Builder {
        private Identifier id = UNKNOWN_ID;
        private Type type = Type.UNKNOWN;
        private final int difficulty;
        private Version version = Version.UNKNOWN;
        private List<String> majorTag = new ArrayList<>();
        private List<String> subTag = new ArrayList<>();
        private List<Identifier> dimension = new ArrayList<>();

        public Builder(int difficulty) {
            this.difficulty = difficulty;
        }

        public Builder id(Identifier id) {
            this.id = id;
            return this;
        }

        public Builder type(Type type) {
            this.type = type;
            return this;
        }

        public Builder version(Version version) {
            this.version = version;
            return this;
        }

        public Builder majorTag(List<String> tags) {
            majorTag = tags;
            return this;
        }

        public Builder subTag(List<String> tags) {
            subTag = tags;
            return this;
        }

        public Builder dimension(List<Identifier> tags) {
            dimension = tags;
            return this;
        }

        public Question build() {
            return new Question(id, type, difficulty, version, majorTag, subTag, dimension);
        }
    }
}