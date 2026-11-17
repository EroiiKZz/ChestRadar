package fr.imero.chestradar.client.capture;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import fr.imero.chestradar.ChestRadar;
import fr.imero.chestradar.data.ItemIdentities;
import fr.imero.chestradar.data.ItemIdentity;
import fr.imero.chestradar.search.BulkQuantity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ItemIdentityFactory {
	private static final String UNENCODABLE_SENTINEL = "{\"chestradar:unencodable\":true}";

	private ItemIdentityFactory() {
	}

	public static RegistryOps<JsonElement> opsFor(Minecraft client) {
		if (client.getConnection() == null) {
			return null;
		}
		return client.getConnection().registryAccess().createSerializationContext(JsonOps.INSTANCE);
	}

	private static final String LORE_KEY = "minecraft:lore";

	public static ItemIdentity from(ItemStack stack, RegistryOps<JsonElement> ops) {
		return from(stack, ops, IdentityRules.none());
	}

	public static ItemIdentity from(ItemStack stack, RegistryOps<JsonElement> ops, IdentityRules rules) {
		IdentityRules effective = rules == null ? IdentityRules.none() : rules;
		Set<String> keys = effective.volatileKeys();
		String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		String componentsJson = encodeComponents(stack, ops);
		String displayName = stack.getHoverName().getString();
		List<String> loreLines = loreLines(stack);

		if (BulkQuantity.parse(loreLines, effective.bulkQuantityPattern()) > 0) {
			keys = new HashSet<>(keys);
			keys.add(LORE_KEY);
		}
		return ItemIdentities.of(itemId, componentsJson, displayName, String.join(" ", loreLines),
			extractEnchantments(stack), keys);
	}

	public static int quantityOf(ItemStack stack, IdentityRules rules) {
		if (rules == null || rules.bulkQuantityPattern() == null) {
			return stack.getCount();
		}
		int bulk = BulkQuantity.parse(loreLines(stack), rules.bulkQuantityPattern());
		return bulk > 0 ? bulk : stack.getCount();
	}

	private static String encodeComponents(ItemStack stack, RegistryOps<JsonElement> ops) {
		DataComponentPatch changes = stack.getComponentsPatch();
		try {
			var encoded = DataComponentPatch.CODEC
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
			var lore = stack.get(DataComponents.LORE);
			if (lore == null) {
				return List.of();
			}
			List<String> lines = new ArrayList<>();
			for (Component line : lore.lines()) {
				lines.add(line.getString());
			}
			return lines;
		} catch (Exception e) {
			ChestRadar.LOGGER.debug("[chestradar] lecture du lore impossible pour {}",
				BuiltInRegistries.ITEM.getKey(stack.getItem()), e);
			return List.of();
		}
	}

	private static List<String> extractEnchantments(ItemStack stack) {
		List<String> ids = new ArrayList<>();
		try {
			collect(stack.get(DataComponents.ENCHANTMENTS), ids);
			collect(stack.get(DataComponents.STORED_ENCHANTMENTS), ids);
		} catch (Exception e) {
			ChestRadar.LOGGER.debug("[chestradar] lecture des enchantements impossible", e);
		}
		Collections.sort(ids);
		return ids;
	}

	private static void collect(net.minecraft.world.item.enchantment.ItemEnchantments component, List<String> ids) {
		if (component == null) {
			return;
		}
		component.keySet().forEach(entry ->
			entry.unwrapKey().ifPresent(key -> ids.add(key.identifier().toString())));
	}
}
