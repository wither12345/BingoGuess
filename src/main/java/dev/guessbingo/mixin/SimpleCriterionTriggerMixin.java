package dev.guessbingo.mixin;

import dev.guessbingo.GuessBingo;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Predicate;

@Mixin(SimpleCriterionTrigger.class)
public class SimpleCriterionTriggerMixin {
    @Inject(method = "trigger", at = @At("HEAD"))
    public void trigger(ServerPlayer player, Predicate<SimpleCriterionTrigger.SimpleInstance> matcher, CallbackInfo ci){
        GuessBingo.manager().testTrigger(player, (SimpleCriterionTrigger)(Object)this, matcher);
    }
}
