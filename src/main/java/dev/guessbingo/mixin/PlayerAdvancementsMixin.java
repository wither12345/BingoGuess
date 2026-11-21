package dev.guessbingo.mixin;

import dev.guessbingo.GuessBingo;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancements.class)
public class PlayerAdvancementsMixin {
    @Shadow
    private ServerPlayer player;

    @Inject(method = "award", at = @At("HEAD"))
    public void award(AdvancementHolder holder, String criterion, CallbackInfoReturnable<Boolean> cir) {
        GuessBingo.manager().triggerAdvancement(this.player, holder);
    }
}
