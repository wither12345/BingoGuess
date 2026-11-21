package dev.guessbingo.lex;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

public class StringCompareResult {
    /**
     * 逐字提示：左对齐或右对齐时字与位置相同为绿；答案中有此字但两种对齐位置均不同为蓝；
     * 无此字或超出出现次数为红。重复字优先分配绿色，再分配蓝色；末尾类型括号不参与。
     */
    static List<StringState> letters(String guess, String answer) {
        int glen = guess.length();
        int alen = answer.length();
        List<StringState> out = new ArrayList<>(guess.length());
        boolean[] used = new boolean[alen];
        int offset = alen - glen;

        for (int i = 0; i < glen; i++) {
            out.add(StringState.INCORRECT);
        }

        for (int i = 0; i < glen; i++) {
            char c = guess.charAt(i);
            // 两个对齐位置中，下标较小的先检查
            int first = offset < 0 ? i + offset : i;
            int second = offset < 0 ? i : i + offset;
            if (claim(answer, alen, used, first, c) || claim(answer, alen, used, second, c)) {
                out.add(i, StringState.CORRECT);
            }
        }
        for (int i = 0; i < glen; i++) {
            if (out.get(i) == StringState.CORRECT) continue;
            char c = guess.charAt(i);
            out.set(i, StringState.INCORRECT);
            for (int idx = 0; idx < alen; idx++) {
                if (claim(answer, alen, used, idx, c)) {
                    out.set(i, StringState.WRONG_POS);
                    break;
                }
            }
        }

        return out;
    }

    private static boolean claim(String answer, int alen, boolean[] used, int idx, char c) {
        if (idx < 0 || idx >= alen || used[idx] || answer.charAt(idx) != c) return false;
        used[idx] = true;
        return true;
    }

    public enum StringState implements StringRepresentable {
        CORRECT("correct"),
        INCORRECT("incorrect"),
        WRONG_POS("wrong_pos");

        private final String name;
        StringState(String name){
            this.name = name;
        }

        public static final Codec<StringState> CODEC = StringRepresentable.fromEnum(StringState::values);
        public static final IntFunction<StringState> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
        public static final StreamCodec<ByteBuf, StringState> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Enum::ordinal);

        @Override
        public @NonNull String getSerializedName() {
            return name;
        }
    }
}
