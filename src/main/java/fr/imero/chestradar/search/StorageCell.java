package fr.imero.chestradar.search;

import fr.imero.chestradar.data.ContainerIndex;
import fr.imero.chestradar.data.ContainerRecord;
import fr.imero.chestradar.data.ItemIdentities;
import fr.imero.chestradar.data.ItemIdentity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class StorageCell {
	public static final Set<String> DECORATIVE_KEYS = Set.of(
		"minecraft:custom_name",
		"minecraft:item_name",
		"minecraft:lore",
		"minecraft:rarity",
		"minecraft:custom_data",
		"minecraft:custom_model_data",
		"minecraft:item_model",
		"minecraft:tooltip_style",
		"minecraft:tooltip_display",
		"minecraft:enchantment_glint_override",
		"minecraft:max_stack_size");

	private StorageCell() {
	}

	public static Map<String, Integer> collapse(Map<String, Integer> counts, int titleQuantity) {
		if (counts == null || counts.size() != 1 || titleQuantity <= 0) {
			return counts;
		}
		Map.Entry<String, Integer> only = counts.entrySet().iterator().next();
		int counted = only.getValue() == null ? 0 : only.getValue();
		if (titleQuantity <= counted) {
			return counts;
		}
		Map<String, Integer> collapsed = new LinkedHashMap<>();
		collapsed.put(only.getKey(), titleQuantity);
		return collapsed;
	}

	public static String plainComponents(String componentsJson) {
		return ItemIdentities.without(componentsJson, DECORATIVE_KEYS);
	}

	public static String plainFingerprint(ItemIdentity identity, Set<String> volatileKeys) {
		return ItemIdentities.fingerprint(identity.itemId(),
			plainComponents(identity.componentsJson()), volatileKeys);
	}

	public static int merge(ContainerIndex index, Pattern lorePattern, Pattern titlePattern,
			Set<String> volatileKeys) {
		Map<String, String> remap = remapping(index, lorePattern, titlePattern, volatileKeys);
		if (remap.isEmpty()) {
			return 0;
		}
		List<ContainerRecord> rewritten = new ArrayList<>();
		for (ContainerRecord record : index.all()) {
			Map<String, Integer> counts = rewrite(record.counts(), remap);
			Map<String, Integer> nested = rewrite(record.nestedCounts(), remap);
			if (counts == null && nested == null) {
				continue;
			}
			rewritten.add(new ContainerRecord(record.key(), record.secondary(), record.kind(),
				record.title(), record.lastSeenEpochMillis(), record.slotCount(),
				counts == null ? record.counts() : counts,
				nested == null ? record.nestedCounts() : nested));
		}
		for (ContainerRecord record : rewritten) {
			index.upsert(record);
		}
		index.pool().purgeOrphans(index.all());
		return remap.size();
	}

	private static Map<String, String> remapping(ContainerIndex index, Pattern lorePattern,
			Pattern titlePattern, Set<String> volatileKeys) {
		Map<String, String> out = new HashMap<>();
		for (ItemIdentity identity : List.copyOf(index.pool().all())) {
			if (BulkQuantity.parse(Collections.singletonList(identity.loreText()), lorePattern) > 0) {
				fold(out, index, identity, volatileKeys);
			}
		}
		for (ContainerRecord record : index.all()) {
			ItemIdentity identity = soleItemOfCell(index, record, titlePattern);
			if (identity != null) {
				fold(out, index, identity, volatileKeys);
			}
		}
		return out;
	}

	private static ItemIdentity soleItemOfCell(ContainerIndex index, ContainerRecord record,
			Pattern titlePattern) {
		if (record.title() == null || record.counts().size() != 1
			|| BulkQuantity.parse(Collections.singletonList(record.title()), titlePattern) <= 0) {
			return null;
		}
		return index.pool().get(record.counts().keySet().iterator().next());
	}

	private static void fold(Map<String, String> out, ContainerIndex index, ItemIdentity identity,
			Set<String> volatileKeys) {
		String plain = plainFingerprint(identity, volatileKeys);
		if (plain.equals(identity.fingerprint()) || index.pool().get(plain) == null) {
			return;
		}
		out.put(identity.fingerprint(), plain);
	}

	private static Map<String, Integer> rewrite(Map<String, Integer> counts, Map<String, String> remap) {
		boolean touched = false;
		Map<String, Integer> out = new LinkedHashMap<>();
		for (Map.Entry<String, Integer> entry : counts.entrySet()) {
			String target = remap.get(entry.getKey());
			touched |= target != null;
			out.merge(target == null ? entry.getKey() : target, entry.getValue(), Integer::sum);
		}
		return touched ? out : null;
	}
}
