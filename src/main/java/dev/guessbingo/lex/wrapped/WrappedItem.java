package dev.guessbingo.lex.wrapped;

import dev.guessbingo.lex.Question;
import net.minecraft.advancements.predicates.DataComponentMatchers;
import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.advancements.triggers.InventoryChangeTrigger;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class WrappedItem extends WrappedQuestion {
    private final Holder<Item> itemHolder;
    private final InventoryChangeTrigger.TriggerInstance instance;

    @SuppressWarnings("deprecation")
    public WrappedItem(Question question, int index, QuestionState state) {
        super(question, index, state);
        this.itemHolder = BuiltInRegistries.ITEM.get(question.id()).orElse(Items.AIR.builtInRegistryHolder());
        this.instance = new InventoryChangeTrigger.TriggerInstance(
                Optional.empty(),
                InventoryChangeTrigger.TriggerInstance.Slots.ANY,
                List.of(new ItemPredicate(Optional.of(HolderSet.direct((i) -> i.value().builtInRegistryHolder(), itemHolder)), MinMaxBounds.Ints.ANY, DataComponentMatchers.ANY))
        );

    }

    @Override
    public boolean testTrigger(ServerPlayer player,
                            SimpleCriterionTrigger<? extends SimpleCriterionTrigger.SimpleInstance> trigger,
                            Predicate<SimpleCriterionTrigger.SimpleInstance> matcher) {
        if(trigger != CriteriaTriggers.INVENTORY_CHANGED)
            return false;
        return matcher.test(instance);
    }

    @Override
    public boolean test(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if(stack.isEmpty()) continue;
            if(stack.is(itemHolder))
                return true;
        }
        ItemStack carried = player.containerMenu.getCarried();
        return !carried.isEmpty() && carried.is(itemHolder);
    }

}
