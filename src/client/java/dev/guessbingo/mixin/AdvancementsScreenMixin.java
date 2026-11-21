package dev.guessbingo.mixin;

import dev.guessbingo.client.IGuessableTab;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AdvancementsScreen.class)
public class AdvancementsScreenMixin {
    @Shadow
    private @Nullable AdvancementTab selectedTab;

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    public void mouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if(this.selectedTab != null){
            ((IGuessableTab)this.selectedTab).guessbingo$guess(Minecraft.getInstance().player);
        }
    }
}