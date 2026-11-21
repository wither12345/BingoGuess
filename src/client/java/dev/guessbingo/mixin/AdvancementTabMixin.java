package dev.guessbingo.mixin;

import dev.guessbingo.GuessBingo;
import dev.guessbingo.client.IGuessableTab;
import dev.guessbingo.data.Attachments;
import dev.guessbingo.lex.RawQuestion;
import dev.guessbingo.lex.Type;
import dev.guessbingo.net.GuessData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(AdvancementTab.class)
public class AdvancementTabMixin implements IGuessableTab {
    @Shadow
    private @Nullable AdvancementWidget hovered;

    @Override
    public void guessbingo$guess(LocalPlayer player) {
        if(this.hovered == null)
            return;

        Identifier id = this.hovered.getAdvancement().id();
        ClientPlayNetworking.send(new GuessData(player.getAttachedOrElse(Attachments.SELECTING_ATTACHMENT, 0), new RawQuestion(id, Type.ADVANCEMENT)));
        GuessBingo.LOGGER.info("Guessed {}", id);
    }
}