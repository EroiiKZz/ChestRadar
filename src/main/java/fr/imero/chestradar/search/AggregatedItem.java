package fr.imero.chestradar.search;

import fr.imero.chestradar.data.ItemIdentity;

import java.util.List;

public record AggregatedItem(ItemIdentity identity, int totalCount, List<Hit> hits) {
	public AggregatedItem {
		hits = List.copyOf(hits);
	}

	public double nearestDistance() {
		return hits.stream().mapToDouble(Hit::distance).min().orElse(Double.MAX_VALUE);
	}

	public Hit nearestHit() {
		return hits.stream().min(java.util.Comparator.comparingDouble(Hit::distance)).orElse(null);
	}

	public int nestedTotal() {
		return hits.stream().mapToInt(Hit::nestedCount).sum();
	}
}
