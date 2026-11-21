package dev.guessbingo.lex.wrapped;

import dev.guessbingo.lex.Question;
import net.minecraft.advancements.predicates.DataComponentMatchers;
import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.advancements.predicates.entity.EntityPredicate;
import net.minecraft.advancements.predicates.entity.EntityTypePredicate;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.advancements.triggers.InventoryChangeTrigger;
import net.minecraft.advancements.triggers.KilledTrigger;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class WrappedEntity extends WrappedQuestion {
    private final SimpleCriterionTrigger.SimpleInstance trigger;
    private final boolean itemForm;

    @SuppressWarnings("deprecation")
    public WrappedEntity(Question question, int index, QuestionState state) {
        super(question, index, state);
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(question.id());
        Optional<Holder.Reference<Item>> itemHolder = BuiltInRegistries.ITEM.get(question.id());
        if(itemHolder.isEmpty()) {
            this.itemForm = false;
            this.trigger = new KilledTrigger.TriggerInstance(
                    Optional.empty(),
                    Optional.of(EntityPredicate.wrap(
                            EntityPredicate.Builder.entity()
                                    .entityType(new EntityTypePredicate(HolderSet.direct(type.builtInRegistryHolder()))))),
                    Optional.empty()
            );
        }
        else {
            this.itemForm = true;
            Holder<Item> item = itemHolder.get();
            this.trigger = new InventoryChangeTrigger.TriggerInstance(
                    Optional.empty(),
                    InventoryChangeTrigger.TriggerInstance.Slots.ANY,
                    List.of(new ItemPredicate(Optional.of(HolderSet.direct((i) -> i.value().builtInRegistryHolder(), item)), MinMaxBounds.Ints.ANY, DataComponentMatchers.ANY))
            );
        }
    }

    @Override
    public boolean testTrigger(ServerPlayer player,
                            SimpleCriterionTrigger<? extends SimpleCriterionTrigger.SimpleInstance> trigger,
                            Predicate<SimpleCriterionTrigger.SimpleInstance> matcher) {
        if((this.itemForm && trigger != CriteriaTriggers.INVENTORY_CHANGED) || (!this.itemForm && trigger != CriteriaTriggers.PLAYER_KILLED_ENTITY))
            return false;
        return matcher.test(this.trigger);
    }
}