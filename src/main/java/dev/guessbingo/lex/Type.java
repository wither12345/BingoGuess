package dev.guessbingo.lex;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.function.IntFunction;

public enum Type implements StringRepresentable {
    ITEM("item"),
    BLOCK("block"),
    ENTITY_TYPE("entity"),
    ADVANCEMENT("advancement"),
    ENCHANTMENT("enchantment"),
    MOB_EFFECT("mob_effect"),
    STRUCTURE("structure"),
    UNKNOWN("unknown");

    public static final Codec<Type> CODEC = StringRepresentable.fromEnum(Type::values);
    public static final IntFunction<Type> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, Type> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Enum::ordinal);

    private final String name;

    Type(String name) {
        this.name = name;
    }

    @Override
    public @NonNull String getSerializedName() {
        return name;
    }
}
