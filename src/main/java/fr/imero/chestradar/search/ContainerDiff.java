package fr.imero.chestradar.search;

import fr.imero.chestradar.data.ContainerRecord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ContainerDiff {
	public record Change(String fingerprint, int before, int after) {
		public int delta() {
			return after - before;
		}
	}

	private ContainerDiff() {
	}

	public static List<Change> between(ContainerRecord before, ContainerRecord after) {
		if (before == null || after == null) {
			return List.of();
		}
		Set<String> fingerprints = new HashSet<>(before.allFingerprints());
		fingerprints.addAll(after.allFingerprints());

		List<Change> changes = new ArrayList<>();
		for (String fingerprint : fingerprints) {
			int was = total(before, fingerprint);
			int is = total(after, fingerprint);
			if (was != is) {
				changes.add(new Change(fingerprint, was, is));
			}
		}
		changes.sort(Comparator
			.comparingInt((Change c) -> Math.abs(c.delta())).reversed()
			.thenComparing(Change::fingerprint));
		return changes;
	}

	private static int total(ContainerRecord record, String fingerprint) {
		return record.countOf(fingerprint) + record.nestedCountOf(fingerprint);
	}
}
