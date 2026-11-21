package dev.guessbingo.data;

import com.mojang.serialization.Codec;
import dev.guessbingo.GuessBingo;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;

public class Attachments {
    public static final AttachmentType<Integer> SELECTING_ATTACHMENT = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(GuessBingo.MOD_ID, "selecting"),
            builder -> builder
                    .initializer(() -> 0)
                    .syncWith(
                            ByteBufCodecs.INT,
                            AttachmentSyncPredicate.targetOnly()
                    )
                    .copyOnDeath()
                    .persistent(Codec.INT)
    );
}
