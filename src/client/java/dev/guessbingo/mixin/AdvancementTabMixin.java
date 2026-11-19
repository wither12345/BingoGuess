package dev.guessbingo.mixin;

import dev.guessbingo.GuessBingo;
import dev.guessbingo.client.IGuessableTab;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import net.minecraft.client.player.LocalPlayer;
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

        String guessAdvancement = this.hovered.getDisplay().title().getString();
        GuessBingo.LOGGER.info("猜了 {}", guessAdvancement);

        player.connection.sendCommand("guess " + guessAdvancement);
    }
}
