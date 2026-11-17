package fr.imero.chestradar.client.craft;

import fr.imero.chestradar.ChestRadar;
import fr.imero.chestradar.data.ItemIdentities;
import fr.imero.chestradar.search.CraftPlan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.context.ContextMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RecipeCatalog {
	private static final int MAX_CANDIDATES_PER_SLOT = 48;

	public enum Station {
		CRAFTING, SMELTING, STONECUTTING, SMITHING
	}

	public record Entry(ItemStack output, String label, String itemId, String searchKey,
			List<CraftPlan.Slot> slots, Map<String, ItemStack> icons, Station station) {
		public Entry {
			slots = List.copyOf(slots);
			icons = Map.copyOf(icons);
		}

		public ItemStack iconFor(CraftPlan.Ingredient ingredient) {
			return icons.getOrDefault(ingredient.key(), output);
		}

		public int outputPerCraft() {
			return Math.max(1, output.getCount());
		}

		public int stackSize() {
			return Math.max(1, output.getMaxStackSize());
		}
	}

	private RecipeCatalog() {
	}

	public static List<Entry> build(Minecraft client) {
		List<Entry> entries = new ArrayList<>();
		if (client == null || client.player == null || client.level == null) {
			return entries;
		}
		try {
			ContextMap parameters = slotContext(client.level);
			Map<String, Entry> unique = new LinkedHashMap<>();
			for (var collection : client.player.getRecipeBook().getCollections()) {
				for (RecipeDisplayEntry entry : collection.getRecipes()) {
					Entry converted = convert(entry, parameters);
					if (converted == null) {
						continue;
					}
					unique.putIfAbsent(signature(converted), converted);
				}
			}
			entries.addAll(unique.values());
		} catch (Exception e) {
			ChestRadar.LOGGER.warn("[chestradar] lecture du livre de recettes impossible", e);
		}
		entries.sort(Comparator.comparing(Entry::label, String.CASE_INSENSITIVE_ORDER));
		return entries;
	}

	public static Entry from(Minecraft client, RecipeDisplayEntry entry) {
		if (client == null || client.level == null || entry == null) {
			return null;
		}
		try {
			return convert(entry, slotContext(client.level));
		} catch (Exception e) {
			ChestRadar.LOGGER.warn("[chestradar] conversion de la recette sélectionnée impossible", e);
			return null;
		}
	}

	public static List<Entry> search(List<Entry> catalog, String query, int limit) {
		String needle = ItemIdentities.normalizeSearch(query);
		List<Entry> out = new ArrayList<>();
		if (catalog == null) {
			return out;
		}
		if (needle.isEmpty()) {
			out.addAll(catalog.subList(0, Math.min(catalog.size(), Math.max(0, limit))));
			return out;
		}
		List<Entry> matches = new ArrayList<>();
		for (Entry entry : catalog) {
			if (entry.searchKey().contains(needle)) {
				matches.add(entry);
			}
		}
		matches.sort(Comparator.<Entry>comparingInt(entry -> rank(entry, needle))
			.thenComparing(Entry::label, String.CASE_INSENSITIVE_ORDER));
		return matches.subList(0, Math.min(matches.size(), Math.max(0, limit)));
	}

	public static Entry forItemId(List<Entry> catalog, String itemId) {
		if (catalog == null || itemId == null) {
			return null;
		}
		for (Entry entry : catalog) {
			if (itemId.equals(entry.itemId())) {
				return entry;
			}
		}
		return null;
	}

	private static int rank(Entry entry, String needle) {
		String name = ItemIdentities.normalizeSearch(entry.label());
		if (name.equals(needle)) {
			return 0;
		}
		if (name.startsWith(needle)) {
			return 1;
		}
		return name.contains(needle) ? 2 : 3;
	}

	private static String signature(Entry entry) {
		StringBuilder sb = new StringBuilder(entry.itemId()).append('|')
			.append(entry.output().getCount()).append('|').append(entry.station().name());
		for (CraftPlan.Slot slot : entry.slots()) {
			sb.append('#').append(String.join("+", slot.itemIds()));
		}
		return sb.toString();
	}

	private static ContextMap slotContext(ClientLevel level) {
		return SlotDisplayContext.fromLevel(level);
	}

	private static Entry convert(RecipeDisplayEntry entry, ContextMap parameters) {
		RecipeDisplay display = entry.display();
		List<SlotDisplay> inputs;
		Station station;
		if (display instanceof ShapedCraftingRecipeDisplay shaped) {
			inputs = shaped.ingredients();
			station = Station.CRAFTING;
		} else if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
			inputs = shapeless.ingredients();
			station = Station.CRAFTING;
		} else if (display instanceof FurnaceRecipeDisplay furnace) {
			inputs = List.of(furnace.ingredient());
			station = Station.SMELTING;
		} else if (display instanceof StonecutterRecipeDisplay stonecutter) {
			inputs = List.of(stonecutter.input());
			station = Station.STONECUTTING;
		} else if (display instanceof SmithingRecipeDisplay smithing) {
			inputs = List.of(smithing.template(), smithing.base(), smithing.addition());
			station = Station.SMITHING;
		} else {
			return null;
		}

		ItemStack output = first(display.result(), parameters);
		if (output == null || output.isEmpty()) {
			return null;
		}
		List<CraftPlan.Slot> slots = new ArrayList<>();
		Map<String, ItemStack> icons = new LinkedHashMap<>();
		for (SlotDisplay input : inputs) {
			CraftPlan.Slot slot = toSlot(input, parameters);
			if (slot == null) {
				continue;
			}
			slots.add(slot);
			ItemStack icon = first(input, parameters);
			if (icon != null) {
				icons.putIfAbsent(CraftPlan.keyOf(slot.itemIds()), icon);
			}
		}
		if (slots.isEmpty()) {
			return null;
		}
		String label = output.getHoverName().getString();
		String itemId = BuiltInRegistries.ITEM.getKey(output.getItem()).toString();
		String searchKey = ItemIdentities.normalizeSearch(label + " " + itemId);
		return new Entry(output, label, itemId, searchKey, slots, icons, station);
	}

	private static CraftPlan.Slot toSlot(SlotDisplay display, ContextMap parameters) {
		List<ItemStack> stacks = display.resolveForStacks(parameters);
		if (stacks.isEmpty()) {
			return null;
		}
		List<String> itemIds = new ArrayList<>();
		String label = "";
		for (ItemStack stack : stacks) {
			if (stack == null || stack.isEmpty() || itemIds.size() >= MAX_CANDIDATES_PER_SLOT) {
				continue;
			}
			String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			if (!itemIds.contains(id)) {
				itemIds.add(id);
			}
			if (label.isEmpty()) {
				label = stack.getHoverName().getString();
			}
		}
		return itemIds.isEmpty() ? null : new CraftPlan.Slot(itemIds, label);
	}

	private static ItemStack first(SlotDisplay display, ContextMap parameters) {
		for (ItemStack stack : display.resolveForStacks(parameters)) {
			if (stack != null && !stack.isEmpty()) {
				return stack;
			}
		}
		return null;
	}
}
