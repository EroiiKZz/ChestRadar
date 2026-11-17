package fr.imero.chestradar.search;

import fr.imero.chestradar.data.ContainerRecord;
import fr.imero.chestradar.data.ItemIdentity;
import fr.imero.chestradar.data.ItemIdentityPool;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ContainerContents {
	public record Entry(ItemIdentity identity, int count) {
	}

	public record Preview(List<Entry> shown, int hiddenEntries, int hiddenCount) {
		public Preview {
			shown = List.copyOf(shown);
		}

		public boolean isEmpty() {
			return shown.isEmpty() && hiddenEntries == 0;
		}
	}

	private ContainerContents() {
	}

	public static List<Entry> all(ContainerRecord record, ItemIdentityPool pool) {
		List<Entry> entries = new ArrayList<>();
		if (record == null || pool == null) {
			return entries;
		}
		for (String fingerprint : record.allFingerprints()) {
			ItemIdentity identity = pool.get(fingerprint);
			if (identity == null) {
				continue;
			}
			int count = record.countOf(fingerprint) + record.nestedCountOf(fingerprint);
			if (count > 0) {
				entries.add(new Entry(identity, count));
			}
		}
		entries.sort(Comparator
			.comparingInt(Entry::count).reversed()
			.thenComparing(e -> e.identity().displayName(), String.CASE_INSENSITIVE_ORDER)
			.thenComparing(e -> e.identity().fingerprint()));
		return entries;
	}

	public static Preview preview(ContainerRecord record, ItemIdentityPool pool, int limit) {
		List<Entry> entries = all(record, pool);
		if (limit <= 0 || entries.size() <= limit) {
			return new Preview(entries, 0, 0);
		}
		List<Entry> shown = entries.subList(0, limit);
		int hiddenCount = 0;
		for (Entry entry : entries.subList(limit, entries.size())) {
			hiddenCount += entry.count();
		}
		return new Preview(shown, entries.size() - limit, hiddenCount);
	}

	public static int totalCount(ContainerRecord record, ItemIdentityPool pool) {
		int total = 0;
		for (Entry entry : all(record, pool)) {
			total += entry.count();
		}
		return total;
	}
}
