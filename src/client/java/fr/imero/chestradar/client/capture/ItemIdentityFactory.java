package fr.imero.chestradar.client.capture;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import fr.imero.chestradar.ChestRadar;
import fr.imero.chestradar.data.ItemIdentities;
import fr.imero.chestradar.data.ItemIdentity;
import fr.imero.chestradar.search.BulkQuantity;
import fr.imero.chestradar.search.StorageCell;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryOps;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class ItemIdentityFactory {
	private static final String UNENCODABLE_SENTINEL = "{\"chestradar:unencodable\":true}";

	private ItemIdentityFactory() {
	}

	public static RegistryOps<JsonElement> opsFor(MinecraftClient client) {
		if (client.getNetworkHandler() == null) {
			return null;
		}
		return client.getNetworkHandler().getRegistryManager().getOps(JsonOps.INSTANCE);
	}

	public static ItemIdentity from(ItemStack stack, RegistryOps<JsonElement> ops) {
		return from(stack, ops, IdentityRules.none());
	}

	public static ItemIdentity from(ItemStack stack, RegistryOps<JsonElement> ops, IdentityRules rules) {
		return from(stack, ops, rules, false);
	}

	public static ItemIdentity from(ItemStack stack, RegistryOps<JsonElement> ops, IdentityRules rules,
			boolean insideCell) {
		IdentityRules effective = rules == null ? IdentityRules.none() : rules;
		Set<String> keys = effective.volatileKeys();
		String itemId = Registries.ITEM.getId(stack.getItem()).toString();
		String componentsJson = encodeComponents(stack, ops);
		List<String> loreLines = loreLines(stack);

		boolean bulk = effective.bulkQuantityPattern() != null
			&& (insideCell || BulkQuantity.parse(loreLines, effective.bulkQuantityPattern()) > 0);
		if (bulk) {
			return ItemIdentities.of(itemId, StorageCell.plainComponents(componentsJson),
				plainName(stack), "", extractEnchantments(stack), keys);
		}
		return ItemIdentities.of(itemId, componentsJson, stack.getName().getString(),
			String.join(" ", loreLines), extractEnchantments(stack), keys);
	}

	private static String plainName(ItemStack stack) {
		try {
			ItemStack plain = stack.copy();
			plain.remove(DataComponentTypes.CUSTOM_NAME);
			plain.remove(DataComponentTypes.ITEM_NAME);
			return plain.getName().getString();
		} catch (Exception e) {
			ChestRadar.LOGGER.debug("[chestradar] nom d'origine illisible pour {}",
				Registries.ITEM.getId(stack.getItem()), e);
			return stack.getName().getString();
		}
	}

	public static int quantityOf(ItemStack stack, IdentityRules rules) {
		if (rules == null || rules.bulkQuantityPattern() == null) {
			return stack.getCount();
		}
		int bulk = BulkQuantity.parse(loreLines(stack), rules.bulkQuantityPattern());
		return bulk > 0 ? bulk : stack.getCount();
	}

	private static String encodeComponents(ItemStack stack, RegistryOps<JsonElement> ops) {
		ComponentChanges changes = stack.getComponentChanges();
		try {
			var encoded = ComponentChanges.CODEC
				.encodeStart(ops, changes)
				.resultOrPartial(msg -> ChestRadar.LOGGER.warn(
					"[chestradar] encodage partiel des composants pour {} : {}", stack, msg));
			if (encoded.isPresent()) {
				return encoded.get().toString();
			}
			return changes.isEmpty() ? "{}" : UNENCODABLE_SENTINEL;
		} catch (Exception e) {
			ChestRadar.LOGGER.warn("[chestradar] encodage des composants impossible pour {}", stack, e);
			return changes.isEmpty() ? "{}" : UNENCODABLE_SENTINEL;
		}
	}

	private static List<String> loreLines(ItemStack stack) {
		try {
			var lore = stack.get(DataComponentTypes.LORE);
			if (lore == null) {
				return List.of();
			}
			List<String> lines = new ArrayList<>();
			for (Text line : lore.lines()) {
				lines.add(line.getString());
			}
			return lines;
		} catch (Exception e) {
			ChestRadar.LOGGER.debug("[chestradar] lecture du lore impossible pour {}",
				Registries.ITEM.getId(stack.getItem()), e);
			return List.of();
		}
	}

	private static List<String> extractEnchantments(ItemStack stack) {
		List<String> ids = new ArrayList<>();
		try {
			collect(stack.get(DataComponentTypes.ENCHANTMENTS), ids);
			collect(stack.get(DataComponentTypes.STORED_ENCHANTMENTS), ids);
		} catch (Exception e) {
			ChestRadar.LOGGER.debug("[chestradar] lecture des enchantements impossible", e);
		}
		Collections.sort(ids);
		return ids;
	}

	private static void collect(net.minecraft.component.type.ItemEnchantmentsComponent component, List<String> ids) {
		if (component == null) {
			return;
		}
		component.getEnchantments().forEach(entry ->
			entry.getKey().ifPresent(key -> ids.add(key.getValue().toString())));
	}
}
