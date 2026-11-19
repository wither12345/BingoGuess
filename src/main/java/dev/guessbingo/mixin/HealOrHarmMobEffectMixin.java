package dev.guessbingo.mixin;

import dev.guessbingo.GuessBingo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 瞬间治疗与瞬间伤害：喷溅、滞留药水和药箭都走这里。 */
@Mixin(targets = "net.minecraft.world.effect.HealOrHarmMobEffect")
public abstract class HealOrHarmMobEffectMixin {
	@Inject(method = "applyInstantaneousEffect", at = @At("HEAD"))
	private void guessbingo$onInstantEffect(ServerLevel level, Entity source, Entity owner, LivingEntity target, int amplifier, double scale, CallbackInfo ci) {
		GuessBingo.onInstantEffect(target, (MobEffect) (Object) this);
	}
}
