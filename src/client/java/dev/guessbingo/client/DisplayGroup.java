package dev.guessbingo.client;

import dev.guessbingo.lex.Question;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

import java.util.List;
import java.util.Optional;

public record DisplayGroup(Question question, ItemStack itemStack, List<Component> components) {
    public static DisplayGroup fromQuestion(Question question) {
        if (question.id().equals(Question.UNKNOWN_ID))
            return new DisplayGroup(question, ItemStack.EMPTY, List.of(Component.translatable("guessbingo.type.unknown")));
        return switch (question.type()) {
            case ITEM, BLOCK -> fromItem(question);
            case ENTITY_TYPE -> fromEntityType(question);
            case ADVANCEMENT -> fromAdvancement(question);
            default ->
                    new DisplayGroup(question, ItemStack.EMPTY, List.of(Component.translatable("guessbingo.type.unknown")));
        };
    }

    public static DisplayGroup fromItem(Question question) {
        ItemStack item = BuiltInRegistries.ITEM.getValue(question.id()).getDefaultInstance();
        return new DisplayGroup(question, item, List.of(item.getItemName()));
    }

    public static DisplayGroup fromEntityType(Question question) {
        ItemStack item = SpawnEggItem.byId(BuiltInRegistries.ENTITY_TYPE.getValue(question.id()))
                .map(holder -> holder.value().getDefaultInstance())
                .orElseGet(() -> BuiltInRegistries.ITEM.getOptional(question.id())
                        .map(Item::getDefaultInstance)
                        .orElse(ItemStack.EMPTY));
        return new DisplayGroup(question, item, List.of(item.getItemName()));
    }

    public static DisplayGroup fromAdvancement(Question question) {
        Optional<DisplayInfo> display = ClientAdvancementsStore.display(question.id());

        ItemStack item = display.map(d -> d.icon().create()).orElse(ItemStack.EMPTY);
        Component title = display.map(DisplayInfo::title).orElse(Component.literal(question.id().toString()));
        return new DisplayGroup(question, item, List.of(title));
    }
}
