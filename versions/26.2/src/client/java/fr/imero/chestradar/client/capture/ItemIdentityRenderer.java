package fr.imero.chestradar.client.capture;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import fr.imero.chestradar.ChestRadar;
import fr.imero.chestradar.data.ItemIdentity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.Identifier;

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

	private static RegistryAccess lastRegistryManager;

	private ItemIdentityRenderer() {
	}

	public static void clearCache() {
		CACHE.clear();
	}

	public static ItemStack toStack(ItemIdentity identity, Minecraft client) {
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

	private static void invalidateIfSessionChanged(Minecraft client) {
		RegistryAccess current = client.getConnection() == null
			? null
			: client.getConnection().registryAccess();
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
			DataComponentPatch changes = DataComponentPatch.CODEC.parse(ops, json)
				.resultOrPartial(msg -> ChestRadar.LOGGER.debug(
					"[chestradar] decodage partiel des composants pour {} : {}", identity.fingerprint(), msg))
				.orElse(null);
			if (changes != null) {
				stack.applyComponentsAndValidate(changes);
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
		if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
			return new ItemStack(Items.BARRIER);
		}
		return new ItemStack(BuiltInRegistries.ITEM.getValue(id));
	}
}
