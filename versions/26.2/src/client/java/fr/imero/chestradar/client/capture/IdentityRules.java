package fr.imero.chestradar.client.capture;

import fr.imero.chestradar.config.ChestRadarConfig;
import fr.imero.chestradar.search.BulkQuantity;

import java.util.Set;
import java.util.regex.Pattern;

public record IdentityRules(Set<String> volatileKeys, Pattern bulkQuantityPattern) {
	public static IdentityRules from(ChestRadarConfig config) {
		return new IdentityRules(NestedContents.volatileKeysFor(config), BulkQuantity.patternFor(config));
	}

	public static IdentityRules none() {
		return new IdentityRules(Set.of(), null);
	}
}
