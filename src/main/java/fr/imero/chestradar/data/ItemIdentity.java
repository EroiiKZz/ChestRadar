package fr.imero.chestradar.data;

import java.util.List;

public record ItemIdentity(
	String itemId,
	String componentsJson,
	String fingerprint,
	String displayName,
	String searchText,
	String loreText,
	List<String> enchantmentIds
) {
	public ItemIdentity {
		enchantmentIds = List.copyOf(enchantmentIds);
	}
}
