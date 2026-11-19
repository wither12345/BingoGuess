package dev.guessbingo.mixin;

import dev.guessbingo.GuessBingo;
import dev.guessbingo.GuessByItem;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin extends AbstractContainerScreen<CreativeModeInventoryScreen.@NotNull ItemPickerMenu> {
    public CreativeModeInventoryScreenMixin(CreativeModeInventoryScreen.ItemPickerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Shadow protected abstract boolean isCreativeSlot(@Nullable Slot slot);

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void slotClicked(Slot slot, int slotId, int buttonNum, ContainerInput containerInput, CallbackInfo ci) {
        GuessBingo.LOGGER.info("slotClicked: {}", slotId);
        LocalPlayer player = this.minecraft.player;
        if (player != null && isCreativeSlot(slot) && !player.isCreative()) {
            String itemName = GuessByItem.guess(slot.getItem());
            GuessBingo.LOGGER.info("itemName: {}", itemName);
            player.connection.sendCommand("guess " + itemName);
            ci.cancel();
        }

    }
}