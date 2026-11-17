package fr.imero.chestradar.search;

import fr.imero.chestradar.ChestRadar;
import fr.imero.chestradar.config.ChestRadarConfig;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BulkQuantity {
	public static final String DEFAULT_PATTERN =
		"(?i)(?:quantit[eé]s?|quantity|stock|amount|montant|total)"
			+ "[^0-9]{0,6}(\\d{1,3}(?:[  .,]\\d{3})+|\\d+)";

	public static final String DEFAULT_TITLE_PATTERN = "(\\d{1,3}(?:[  .,]\\d{3})+|\\d+)\\s*$";

	private static final Map<String, Pattern> CACHE = new HashMap<>();
	private static final Set<String> WARNED = new HashSet<>();

	private BulkQuantity() {
	}

	public static Pattern patternFor(ChestRadarConfig config) {
		return config.indexBulkQuantity
			? compile(config.bulkQuantityLorePattern, DEFAULT_PATTERN, "bulkQuantityLorePattern")
			: null;
	}

	public static Pattern titlePatternFor(ChestRadarConfig config) {
		return config.indexBulkQuantity
			? compile(config.bulkQuantityTitlePattern, DEFAULT_TITLE_PATTERN, "bulkQuantityTitlePattern")
			: null;
	}

	private static Pattern compile(String source, String fallback, String field) {
		String effective = source == null || source.isBlank() ? fallback : source;
		Pattern cached = CACHE.get(effective);
		if (cached != null) {
			return cached;
		}
		Pattern compiled;
		try {
			compiled = Pattern.compile(effective);
		} catch (Exception e) {
			if (WARNED.add(effective)) {
				ChestRadar.LOGGER.warn("[chestradar] {} invalide ({}), retour au motif par defaut",
					field, effective, e);
			}
			compiled = Pattern.compile(fallback);
		}
		CACHE.put(effective, compiled);
		return compiled;
	}

	public static int parse(List<String> loreLines, Pattern pattern) {
		if (pattern == null || loreLines == null) {
			return 0;
		}
		for (String line : loreLines) {
			if (line == null) {
				continue;
			}
			Matcher matcher = pattern.matcher(line);
			if (!matcher.find() || matcher.groupCount() < 1 || matcher.group(1) == null) {
				continue;
			}
			int value = toInt(matcher.group(1));
			if (value > 0) {
				return value;
			}
		}
		return 0;
	}

	private static int toInt(String captured) {
		String digits = captured.replaceAll("[^0-9]", "");
		if (digits.isEmpty()) {
			return 0;
		}
		try {
			return (int) Math.min(Long.parseLong(digits), Integer.MAX_VALUE);
		} catch (NumberFormatException e) {
			return Integer.MAX_VALUE;
		}
	}
}
