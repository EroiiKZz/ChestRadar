package fr.imero.chestradar.data;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public record ContainerRecord(
	ContainerKey key,
	ContainerKey secondary,
	ContainerKind kind,
	String title,
	long lastSeenEpochMillis,
	int slotCount,
	Map<String, Integer> counts,
	Map<String, Integer> nestedCounts
) {
	public ContainerRecord {
		counts = Map.copyOf(counts);
		nestedCounts = nestedCounts == null ? Map.of() : Map.copyOf(nestedCounts);
	}

	public Set<String> allFingerprints() {
		Set<String> all = new HashSet<>(counts.keySet());
		all.addAll(nestedCounts.keySet());
		return all;
	}

	public int countOf(String fingerprint) {
		return counts.getOrDefault(fingerprint, 0);
	}

	public int nestedCountOf(String fingerprint) {
		return nestedCounts.getOrDefault(fingerprint, 0);
	}
}
