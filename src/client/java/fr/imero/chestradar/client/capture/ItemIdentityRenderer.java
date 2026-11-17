package fr.imero.chestradar.client.capture;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import fr.imero.chestradar.ChestRadar;
import fr.imero.chestradar.data.ItemIdentity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.ComponentChanges;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryOps;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ItemIdentityRenderer {
	private static final int MAX_CACHED_STACKS = 512;

	private static final Map<String, ItemStack> CACHE = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, ItemStack> eldest) {
			return size() > MAX_CACHED_STACKS;
		}
	};

	private static DynamicRegistryManager lastRegistryManager;

	private ItemIdentityRenderer() {
	}

	public static void clearCache() {
		CACHE.clear();
	}

	public static ItemStack toStack(ItemIdentity identity, MinecraftClient client) {
		invalidateIfSessionChanged(client);

		ItemStack cached = CACHE.get(identity.fingerprint());
		if (cached != null) {
			return cached;
		}

		RegistryOps<JsonElement> ops = ItemIdentityFactory.opsFor(client);
		ItemStack built = build(identity, ops);
		if (ops != null) {
			CACHE.put(identity.fingerprint(), built);
		}
		return built;
	}

	private static void invalidateIfSessionChanged(MinecraftClient client) {
		DynamicRegistryManager current = client.getNetworkHandler() == null
			? null
			: client.getNetworkHandler().getRegistryManager();
		if (current != lastRegistryManager) {
			CACHE.clear();
			lastRegistryManager = current;
		}
	}

	private static ItemStack build(ItemIdentity identity, RegistryOps<JsonElement> ops) {
		try {
			ItemStack stack = baseStack(identity);
			String componentsJson = identity.componentsJson();
			if (ops == null || componentsJson == null || componentsJson.isEmpty()) {
				return stack;
			}

			JsonElement json = JsonParser.parseString(componentsJson);
			ComponentChanges changes = ComponentChanges.CODEC.parse(ops, json)
				.resultOrPartial(msg -> ChestRadar.LOGGER.debug(
					"[chestradar] decodage partiel des composants pour {} : {}", identity.fingerprint(), msg))
				.orElse(null);
			if (changes != null) {
				stack.applyUnvalidatedChanges(changes);
			}
			return stack;
		} catch (Exception e) {
			ChestRadar.LOGGER.debug("[chestradar] construction de la pile impossible pour {}",
				identity.fingerprint(), e);
			return new ItemStack(Items.BARRIER);
		}
	}

	private static ItemStack baseStack(ItemIdentity identity) {
		Identifier id = Identifier.tryParse(identity.itemId());
		if (id == null || !Registries.ITEM.containsId(id)) {
			return new ItemStack(Items.BARRIER);
		}
		return new ItemStack(Registries.ITEM.get(id));
	}
}
