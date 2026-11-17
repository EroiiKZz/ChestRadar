package fr.imero.chestradar.search;

import fr.imero.chestradar.config.SortMode;
import fr.imero.chestradar.data.ContainerIndex;
import fr.imero.chestradar.data.ContainerRecord;
import fr.imero.chestradar.data.Dimensions;
import fr.imero.chestradar.data.ItemIdentities;
import fr.imero.chestradar.data.ItemIdentity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public final class RadarQuery {
	public static final Predicate<ContainerRecord> ANY = record -> true;

	private RadarQuery() {
	}

	public static List<Hit> findByFingerprint(ContainerIndex index, String dimensionId,
			double px, double py, double pz, double radius, String fingerprint) {
		return findByFingerprint(index, dimensionId, px, py, pz, radius, fingerprint, false, ANY);
	}

	public static List<Hit> findByFingerprint(ContainerIndex index, String dimensionId,
			double px, double py, double pz, double radius, String fingerprint,
			boolean allDimensions, Predicate<ContainerRecord> accept) {
		List<Hit> hits = new ArrayList<>();
		for (ContainerRecord record : index.within(dimensionId, px, py, pz, radius, allDimensions)) {
			if (!accept.test(record)) {
				continue;
			}
			int direct = record.countOf(fingerprint);
			int nested = record.nestedCountOf(fingerprint);
			if (direct + nested > 0) {
				hits.add(new Hit(record, Dimensions.distance(record.key(), dimensionId, px, py, pz),
					direct, nested));
			}
		}
		hits.sort(Comparator.comparingDouble(Hit::distance));
		return hits;
	}

	public static List<Hit> findByItemId(ContainerIndex index, String dimensionId,
			double px, double py, double pz, double radius, String itemId) {
		return findByItemId(index, dimensionId, px, py, pz, radius, itemId, false, ANY);
	}

	public static List<Hit> findByItemId(ContainerIndex index, String dimensionId,
			double px, double py, double pz, double radius, String itemId,
			boolean allDimensions, Predicate<ContainerRecord> accept) {
		if (itemId == null) {
			return new ArrayList<>();
		}
		return findByItemIds(index, dimensionId, px, py, pz, radius, Set.of(itemId),
			allDimensions, accept);
	}

	public static List<Hit> findByItemIds(ContainerIndex index, String dimensionId,
			double px, double py, double pz, double radius, Set<String> itemIds,
			boolean allDimensions, Predicate<ContainerRecord> accept) {
		List<Hit> hits = new ArrayList<>();
		if (itemIds == null || itemIds.isEmpty()) {
			return hits;
		}
		for (ContainerRecord record : index.within(dimensionId, px, py, pz, radius, allDimensions)) {
			if (!accept.test(record)) {
				continue;
			}
			int direct = 0;
			int nested = 0;
			for (String fingerprint : record.allFingerprints()) {
				ItemIdentity identity = index.pool().get(fingerprint);
				if (identity != null && itemIds.contains(identity.itemId())) {
					direct += record.countOf(fingerprint);
					nested += record.nestedCountOf(fingerprint);
				}
			}
			if (direct + nested > 0) {
				hits.add(new Hit(record, Dimensions.distance(record.key(), dimensionId, px, py, pz),
					direct, nested));
			}
		}
		hits.sort(Comparator.comparingDouble(Hit::distance));
		return hits;
	}

	public static List<AggregatedItem> aggregate(ContainerIndex index, String dimensionId,
			double px, double py, double pz, double radius) {
		return aggregate(index, dimensionId, px, py, pz, radius, false, ANY);
	}

	public static List<AggregatedItem> aggregate(ContainerIndex index, String dimensionId,
			double px, double py, double pz, double radius,
			boolean allDimensions, Predicate<ContainerRecord> accept) {
		Map<String, Integer> totals = new LinkedHashMap<>();
		Map<String, List<Hit>> hitsByFingerprint = new LinkedHashMap<>();

		for (ContainerRecord record : index.within(dimensionId, px, py, pz, radius, allDimensions)) {
			if (!accept.test(record)) {
				continue;
			}
			double distance = Dimensions.distance(record.key(), dimensionId, px, py, pz);
			for (String fingerprint : record.allFingerprints()) {
				if (index.pool().get(fingerprint) == null) {
					continue;
				}
				int direct = record.countOf(fingerprint);
				int nested = record.nestedCountOf(fingerprint);
				totals.merge(fingerprint, direct + nested, Integer::sum);
				hitsByFingerprint.computeIfAbsent(fingerprint, k -> new ArrayList<>())
					.add(new Hit(record, distance, direct, nested));
			}
		}

		List<AggregatedItem> out = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : totals.entrySet()) {
			List<Hit> hits = hitsByFingerprint.get(entry.getKey());
			hits.sort(Comparator.comparingDouble(Hit::distance));
			out.add(new AggregatedItem(index.pool().get(entry.getKey()), entry.getValue(), hits));
		}
		return out;
	}

	public static List<AggregatedItem> filter(List<AggregatedItem> items, String query, boolean includeLore) {
		String needle = ItemIdentities.normalizeSearch(query);
		if (needle.isEmpty()) {
			return new ArrayList<>(items);
		}
		List<AggregatedItem> out = new ArrayList<>();
		for (AggregatedItem item : items) {
			boolean matches = item.identity().searchText().contains(needle)
				|| (includeLore && item.identity().loreText().contains(needle));
			if (matches) {
				out.add(item);
			}
		}
		return out;
	}

	public static List<AggregatedItem> filter(List<AggregatedItem> items, SearchQuery query,
			SearchQuery.Context context) {
		if (query.isEmpty()) {
			return new ArrayList<>(items);
		}
		List<AggregatedItem> out = new ArrayList<>();
		for (AggregatedItem item : items) {
			if (query.matches(item, context)) {
				out.add(item);
			}
		}
		return out;
	}

	public static List<AggregatedItem> sort(List<AggregatedItem> items, SortMode mode) {
		return sort(items, mode, Set.of());
	}

	public static List<AggregatedItem> sort(List<AggregatedItem> items, SortMode mode, Set<String> pinned) {
		List<AggregatedItem> out = new ArrayList<>(items);
		Comparator<AggregatedItem> comparator = switch (mode) {
			case NAME -> Comparator.comparing(a -> a.identity().displayName(), String.CASE_INSENSITIVE_ORDER);
			case COUNT -> Comparator.comparingInt(AggregatedItem::totalCount).reversed();
			case DISTANCE -> Comparator.comparingDouble(AggregatedItem::nearestDistance);
		};
		if (!pinned.isEmpty()) {
			comparator = Comparator
				.comparing((AggregatedItem a) -> pinned.contains(a.identity().fingerprint()) ? 0 : 1)
				.thenComparing(comparator);
		}
		out.sort(comparator);
		return out;
	}
}
