package dev.guessbingo;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

public class GuessByItem {
    public static String guess(ItemStack item){
        String itemName = item.getItemName().getString();
        if(item.getItem() instanceof SpawnEggItem){
            itemName = itemName.replace("刷怪蛋", "");
        }
        return itemName;
    }
}
