package dev.guessbingo.mixin;

import dev.guessbingo.client.screen.GuessingScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.server.dialog.Dialog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;

/**
 * 接管原版"快捷操作"键（默认 G）的触发点：原先它按 quick_actions 标签打开数据包对话框，
 * 现在改为直接打开题目搜索界面。quick_actions 标签已被移除，原路径为空操作。
 */
@Mixin(Minecraft.class)
public class QuickActionsMixin {

	@Redirect(
		method = "handleKeybinds",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getQuickActionsDialog()Ljava/util/Optional;")
	)
	private Optional<Holder<Dialog>> guessbingo$openSearch(Minecraft instance) {
		if (instance.player != null && !(instance.gui.screen() instanceof GuessingScreen)) {
			instance.gui.setScreen(new GuessingScreen());
		}
		return Optional.empty();
	}
}