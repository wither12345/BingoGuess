package dev.guessbingo.game;

import dev.guessbingo.lex.Challenge;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Predicate;

/**
 * 把词库里的挑战描述解析成当前服务器上真实存在的注册表对象，并判断某个玩家是否已经完成。
 * 解析不到任何目标时 {@link #create} 返回 null，该词条就不会被选作谜底。
 */
public final class Detector {
	private final Challenge challenge;
	private final Set<Item> items = new HashSet<>();
	/** 药水、附魔、生物群系、结构：用 Holder#is(id) 比对 */
	private final List<Identifier> ids = new ArrayList<>();
	private final List<Holder<@NotNull MobEffect>> effects = new ArrayList<>();
	private final List<AdvancementHolder> advancements = new ArrayList<>();
	private final Set<EntityType<?>> entityTypes = new HashSet<>();

	private Detector(Challenge challenge) {
		this.challenge = challenge;
	}

	public static Detector create(MinecraftServer server, Challenge ch) {
		if (ch == null || ch.t == null) return null;
		Detector d = new Detector(ch);
		try {
			switch (ch.t) {
				case "item" -> {
					for (String id : safe(ch.ids)) item(id).ifPresent(d.items::add);
					return d.items.isEmpty() ? null : d;
				}
				case "potion" -> {
					for (String id : safe(ch.items)) item(id).ifPresent(d.items::add);
					for (String id : safe(ch.potions)) {
						Identifier key = Identifier.tryParse(id);
						if (key != null && BuiltInRegistries.POTION.containsKey(key)) d.ids.add(key);
					}
					return d.items.isEmpty() || d.ids.isEmpty() ? null : d;
				}
				case "enchant" -> {
					d.collect(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT), ch.ids);
					return d.ids.isEmpty() ? null : d;
				}
				case "biome" -> {
					d.collect(server.registryAccess().lookupOrThrow(Registries.BIOME), ch.ids);
					return d.ids.isEmpty() ? null : d;
				}
				case "structure" -> {
					d.collect(server.registryAccess().lookupOrThrow(Registries.STRUCTURE), ch.ids);
					return d.ids.isEmpty() ? null : d;
				}
				case "effect" -> {
					for (String id : safe(ch.ids)) {
						Identifier key = Identifier.tryParse(id);
						if (key != null) BuiltInRegistries.MOB_EFFECT.get(key).ifPresent(d.effects::add);
					}
					return d.effects.isEmpty() ? null : d;
				}
				case "advancement" -> {
					for (String id : safe(ch.ids)) {
						Identifier key = Identifier.tryParse(id);
						AdvancementHolder holder = key == null ? null : server.getAdvancements().get(key);
						if (holder != null) d.advancements.add(holder);
					}
					return d.advancements.isEmpty() ? null : d;
				}
				case "kill" -> {
					for (String id : safe(ch.ids)) {
						Identifier key = Identifier.tryParse(id);
						if (key != null) BuiltInRegistries.ENTITY_TYPE.getOptional(key).ifPresent(d.entityTypes::add);
					}
					return d.entityTypes.isEmpty() ? null : d;
				}
				default -> {
					return null;
				}
			}
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static List<String> safe(List<String> list) {
		return list == null ? List.of() : list;
	}

	private static Optional<Item> item(String id) {
		Identifier key = Identifier.tryParse(id);
		return key == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(key);
	}

	private void collect(Registry<?> registry, List<String> source) {
		for (String id : safe(source)) {
			Identifier key = Identifier.tryParse(id);
			if (key != null && registry.containsKey(key)) ids.add(key);
		}
	}

	public Challenge challenge() {
		return challenge;
	}

	/** 击杀只能靠事件发现；其余都可以轮询。 */
	public boolean polled() {
		return !"kill".equals(challenge.t);
	}

	/** 状态效果可能只持续几刻，需要每刻检查。 */
	public boolean pollEveryTick() {
		return "effect".equals(challenge.t);
	}

	public boolean matchesKill(EntityType<?> type) {
		return "kill".equals(challenge.t) && entityTypes.contains(type);
	}

	public boolean matchesEffect(MobEffect effect) {
		if (!"effect".equals(challenge.t)) return false;
		for (Holder<@NotNull MobEffect> holder : effects) {
			if (holder.value() == effect) return true;
		}
		return false;
	}

	/** 该玩家此刻是否满足挑战（击杀类恒为 false）。 */
	public boolean satisfied(ServerPlayer player) {
		return switch (challenge.t) {
			case "item" -> anyStack(player, stack -> items.contains(stack.getItem()));
			case "potion" -> anyStack(player, this::isWantedPotion);
			case "enchant" -> anyStack(player, this::hasWantedEnchantment);
			case "effect" -> {
				for (Holder<MobEffect> effect : effects) {
					if (player.hasEffect(effect)) yield true;
				}
				yield false;
			}
			case "advancement" -> {
				for (AdvancementHolder holder : advancements) {
					if (player.getAdvancements().getOrStartProgress(holder).isDone()) yield true;
				}
				yield false;
			}
			case "biome" -> {
				Holder<Biome> biome = player.level().getBiome(player.blockPosition());
				yield matchesAny(biome);
			}
			case "structure" -> {
				Predicate<Holder<Structure>> wanted = this::matchesAny;
				StructureStart start = player.level().structureManager().getStructureWithPieceAt(player.blockPosition(), wanted);
				yield start != null && start.isValid();
			}
			default -> false;
		};
	}

	private boolean matchesAny(Holder<?> holder) {
		for (Identifier id : ids) {
			if (holder.is(id)) return true;
		}
		return false;
	}

	private boolean isWantedPotion(ItemStack stack) {
		if (!items.contains(stack.getItem())) return false;
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		if (contents == null) return false;
		Optional<Holder<Potion>> potion = contents.potion();
		return potion.isPresent() && matchesAny(potion.get());
	}

	private boolean hasWantedEnchantment(ItemStack stack) {
		return containsWanted(stack.getEnchantments()) || containsWanted(stack.get(DataComponents.STORED_ENCHANTMENTS));
	}

	private boolean containsWanted(ItemEnchantments enchantments) {
		if (enchantments == null || enchantments.isEmpty()) return false;
		for (Holder<Enchantment> holder : enchantments.keySet()) {
			if (matchesAny(holder)) return true;
		}
		return false;
	}

	/** 物品栏全部格子（含盔甲与副手）加上鼠标上拿着的物品。 */
	private static boolean anyStack(ServerPlayer player, Predicate<ItemStack> test) {
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && test.test(stack)) return true;
		}
		ItemStack carried = player.containerMenu.getCarried();
		return !carried.isEmpty() && test.test(carried);
	}
}
