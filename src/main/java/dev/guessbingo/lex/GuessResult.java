package dev.guessbingo.lex;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.guessbingo.game.Host;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.function.IntFunction;

/**
 * 猜测结果
 * @param team 猜测团队
 * @param guess 猜测
 * @param distance 比较距离
 * 顺序依次:"加入版本", "类型", "标签", "子标签", "维度"
 */
public record GuessResult(Host.TeamInfo team, Question guess, List<StringCompareResult.StringState> letters, List<CompareDistance> distance) {
    public static final Codec<GuessResult> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Host.TeamInfo.CODEC.fieldOf("team").forGetter(GuessResult::team),
            Question.CODEC.fieldOf("guess").forGetter(GuessResult::guess),
            StringCompareResult.StringState.CODEC.listOf().fieldOf("letters").forGetter(GuessResult::letters),
            CompareDistance.CODEC.listOf().fieldOf("distance").forGetter(GuessResult::distance)
    ).apply(instance, GuessResult::new));

    public static final StreamCodec<ByteBuf, GuessResult> STREAM_CODEC = StreamCodec.composite(
            Host.TeamInfo.STREAM_CODEC, GuessResult::team,
            Question.STREAM_CODEC, GuessResult::guess,
            StringCompareResult.StringState.STREAM_CODEC.apply(ByteBufCodecs.list()), GuessResult::letters,
            CompareDistance.STREAM_CODEC.apply(ByteBufCodecs.list()), GuessResult::distance,
            GuessResult::new
    );

    /**答案与谜底的比较结果。正确接近相近略近不同 */
    public enum CompareDistance implements StringRepresentable {
        CORRECT("correct", 0xFF2E7D32),
        VERY_CLOSE("very_close", 0xFFF9A825),
        CLOSE("close", 0xFFFDD835),
        SLIGHTLY_CLOSE("slightly_close", 0xFFFF9800),
        FAR_OFF("far_off", 0xFFC62828);

        public static final Codec<CompareDistance> CODEC = StringRepresentable.fromEnum(CompareDistance::values);
        public static final IntFunction<CompareDistance> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
        public static final StreamCodec<ByteBuf, CompareDistance> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Enum::ordinal);

        private final String name;
        private final int color;
        CompareDistance(String name, int color) {
            this.name = name;
            this.color = color;
        }

        @Override
        public @NonNull String getSerializedName() {
            return name;
        }

        public int color(){
            return color;
        }
    }
}
