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

/**
 * 瞬时效果（例如饱和）不会留在玩家身上，轮询发现不了，只能在施加的那一刻记录。
 * 瞬间治疗与瞬间伤害覆写了这个方法，见 {@link HealOrHarmMobEffectMixin}。
 */
@Mixin(MobEffect.class)
public abstract class MobEffectMixin {
	@Inject(method = "applyInstantaneousEffect", at = @At("HEAD"))
	private void guessbingo$onInstantEffect(ServerLevel level, Entity source, Entity owner, LivingEntity target, int amplifier, double scale, CallbackInfo ci) {
		GuessBingo.onInstantEffect(target, (MobEffect) (Object) this);
	}
}
