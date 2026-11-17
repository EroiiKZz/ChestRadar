package fr.imero.chestradar.client;

import fr.imero.chestradar.config.ChestRadarConfig;
import fr.imero.chestradar.data.ContainerRecord;
import fr.imero.chestradar.search.Freshness;
import fr.imero.chestradar.search.Pins;
import fr.imero.chestradar.search.RadarQuery;
import fr.imero.chestradar.search.SearchQuery;

import java.util.Set;
import java.util.function.Predicate;

public final class RadarScope {
	private RadarScope() {
	}

	private static ChestRadarConfig config() {
		return RadarState.config();
	}

	public static boolean allDimensions() {
		return config().searchAllDimensions;
	}

	public static Predicate<ContainerRecord> accept() {
		ChestRadarConfig config = config();
		if (!staleness(config) || !config.hideStaleContainers) {
			return RadarQuery.ANY;
		}
		long now = System.currentTimeMillis();
		int days = config.staleAfterDays;
		return record -> !Freshness.isStale(record.lastSeenEpochMillis(), now, days);
	}

	public static boolean isStale(ContainerRecord record) {
		return record != null && staleness(config())
			&& Freshness.isStale(record.lastSeenEpochMillis(), System.currentTimeMillis(),
				config().staleAfterDays);
	}

	private static boolean staleness(ChestRadarConfig config) {
		return config.autoRefreshIndex && config.staleAfterDays > 0;
	}

	public static SearchQuery parse(String raw) {
		return config().structuredSearch ? SearchQuery.parse(raw) : SearchQuery.plain(raw);
	}

	public static SearchQuery.Context context() {
		return new SearchQuery.Context(config().searchIncludesLore, System.currentTimeMillis());
	}

	public static Set<String> pinned() {
		ChestRadarConfig config = config();
		return config.pinsEnabled ? Pins.fingerprints(config.pins) : Set.of();
	}
}
