package fr.imero.chestradar.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public final class ItemIdentities {
	private static final Set<String> VOLATILE_KEYS = Set.of(
		"minecraft:damage", "!minecraft:damage",
		"minecraft:repair_cost", "!minecraft:repair_cost");

	private ItemIdentities() {
	}

	public static String stripVolatile(String json) {
		return canonicalize(json, VOLATILE_KEYS);
	}

	public static String canonicalize(String json) {
		return canonicalize(json, Set.of());
	}

	public static String without(String json, Set<String> keys) {
		if (json == null || keys.isEmpty()) {
			return json;
		}
		Set<String> removable = new HashSet<>();
		for (String key : keys) {
			removable.add(key);
			removable.add("!" + key);
		}
		return canonicalize(json, removable);
	}

	private static String canonicalize(String json, Set<String> volatileKeys) {
		try {
			return canonicalizeElement(JsonParser.parseString(json), volatileKeys).toString();
		} catch (Exception e) {
			return json;
		}
	}

	private static JsonElement canonicalizeElement(JsonElement element, Set<String> volatileKeys) {
		if (element.isJsonObject()) {
			JsonObject source = element.getAsJsonObject();
			JsonObject sorted = new JsonObject();
			for (String key : new TreeSet<>(source.keySet())) {
				if (volatileKeys.contains(key)) {
					continue;
				}
				sorted.add(key, canonicalizeElement(source.get(key), volatileKeys));
			}
			return sorted;
		}
		if (element.isJsonArray()) {
			JsonArray source = element.getAsJsonArray();
			JsonArray out = new JsonArray();
			for (JsonElement child : source) {
				out.add(canonicalizeElement(child, volatileKeys));
			}
			return out;
		}
		return element;
	}

	private static Set<String> effectiveVolatileKeys(Set<String> extraVolatileKeys) {
		if (extraVolatileKeys.isEmpty()) {
			return VOLATILE_KEYS;
		}
		Set<String> merged = new HashSet<>(VOLATILE_KEYS);
		for (String key : extraVolatileKeys) {
			merged.add(key);
			merged.add("!" + key);
		}
		return merged;
	}

	public static String fingerprint(String itemId, String componentsJson) {
		return fingerprint(itemId, componentsJson, Set.of());
	}

	public static String fingerprint(String itemId, String componentsJson, Set<String> extraVolatileKeys) {
		String payload = itemId + "|" + canonicalize(componentsJson, effectiveVolatileKeys(extraVolatileKeys));
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
				.digest(payload.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder(16);
			for (int i = 0; i < 8; i++) {
				sb.append(String.format("%02x", digest[i]));
			}
			return sb.toString();
		} catch (Exception e) {
			throw new IllegalStateException("SHA-256 indisponible", e);
		}
	}

	public static String normalizeSearch(String text) {
		if (text == null) {
			return "";
		}
		return text.replaceAll("§[0-9a-fk-orxA-FK-ORX]", "").toLowerCase(Locale.ROOT).trim();
	}

	public static ItemIdentity of(String itemId, String componentsJson, String displayName,
			String loreText, List<String> enchantmentIds) {
		return of(itemId, componentsJson, displayName, loreText, enchantmentIds, Set.of());
	}

	public static ItemIdentity of(String itemId, String componentsJson, String displayName,
			String loreText, List<String> enchantmentIds, Set<String> extraVolatileKeys) {
		List<String> parts = new ArrayList<>();
		parts.add(normalizeSearch(displayName));
		parts.add(normalizeSearch(itemId));
		for (String enchantment : enchantmentIds) {
			parts.add(normalizeSearch(enchantment));
		}
		return new ItemIdentity(
			itemId,
			componentsJson,
			fingerprint(itemId, componentsJson, extraVolatileKeys),
			displayName,
			String.join(" ", parts),
			normalizeSearch(loreText),
			enchantmentIds);
	}
}
