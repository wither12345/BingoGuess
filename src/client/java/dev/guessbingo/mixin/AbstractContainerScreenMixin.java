package dev.guessbingo.mixin;

import dev.guessbingo.GuessBingo;
import dev.guessbingo.data.Attachments;
import dev.guessbingo.lex.RawQuestion;
import dev.guessbingo.lex.Type;
import dev.guessbingo.net.GuessData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin {
    @Shadow
    @Nullable
    protected Slot hoveredSlot;

    @Inject(method = "keyPressed", at = @At("HEAD"))
    public void keyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if(event.keycode() == 103){
            GuessBingo.LOGGER.info("Key pressed: {}", event);
            LocalPlayer player = Minecraft.getInstance().player;
            Slot slot = this.hoveredSlot;
            if(player == null || slot == null || slot.getItem().isEmpty())
                return;
            Item item = slot.getItem().getItem();
            Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
            ClientPlayNetworking.send(new GuessData(
                    player.getAttachedOrElse(Attachments.SELECTING_ATTACHMENT, 0),
                    new RawQuestion(itemId, item instanceof BlockItem ? Type.BLOCK : Type.ITEM))
            );
        }
    }
}