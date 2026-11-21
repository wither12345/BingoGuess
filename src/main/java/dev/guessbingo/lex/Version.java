package dev.guessbingo.lex;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record Version(String version, int sequence, int major, int minor) {
    public static final Version UNKNOWN = new Version("unknown", -1, -1, -1);
    public static final Codec<Version> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
                Codec.STRING.fieldOf("version").forGetter(Version::version),
                Codec.INT.fieldOf("sequence").forGetter(Version::sequence),
                Codec.INT.fieldOf("major").forGetter(Version::major),
                Codec.INT.fieldOf("minor").forGetter(Version::minor)
        ).apply(instance, Version::new));

    public static final StreamCodec<ByteBuf, Version> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Version::version,
            ByteBufCodecs.VAR_INT, Version::sequence,
            ByteBufCodecs.VAR_INT, Version::major,
            ByteBufCodecs.VAR_INT, Version::minor,
            Version::new
    );

    public GuessResult.CompareDistance compareTo(Version other){
        int d = this.sequence - other.sequence;
        int groupDiff = this.major - other.major;
        int patchDiff = this.minor - other.minor;
        GuessResult.CompareDistance result = GuessResult.CompareDistance.FAR_OFF;
        if (Math.abs(groupDiff) == 1) result = GuessResult.CompareDistance.SLIGHTLY_CLOSE;
        if (groupDiff == 0) result = GuessResult.CompareDistance.CLOSE;
        if (groupDiff == 0 && Math.abs(patchDiff) == 1) result = GuessResult.CompareDistance.VERY_CLOSE;
        if (d == 0) result = GuessResult.CompareDistance.CORRECT;
        return result;
    }
}
