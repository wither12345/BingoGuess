package dev.guessbingo.mixin;

import dev.guessbingo.client.ClientState;
import dev.guessbingo.client.screen.GuessCardScreen;
import me.jfenn.bingo.client.impl.screen.ScreenImpl;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Gui.class)
public class MinecraftScreenMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true, name = "screen")
    private Screen setScreen(Screen screen){
        if(ClientState.active() && screen instanceof ScreenImpl){
            return new GuessCardScreen(Component.literal("Guess Bingo"));
        }
        return screen;
    }
}