package dev.guessbingo.mixin;

import dev.guessbingo.entity.player.IPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public class PlayerMixin implements IPlayer {
    @Unique int guessCd = 0;

    @Inject(method = "tick", at = @At("HEAD"))
    private void tick(CallbackInfo ci) {
        if (this.guessCd > 0) this.guessCd--;
    }

    @Override
    public int guessbingo$getGuessCD() {
        return this.guessCd;
    }

    @Override
    public void guessbingo$resetGuessCD() {
        this.guessCd = 60;
    }
}
