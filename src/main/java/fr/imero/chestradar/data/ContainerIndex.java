package fr.imero.chestradar.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ContainerIndex {
	private final Map<ContainerKey, ContainerRecord> records = new LinkedHashMap<>();
	private final ItemIdentityPool pool = new ItemIdentityPool();

	public void upsert(ContainerRecord record) {
		if (record.secondary() != null) {
			records.remove(record.secondary());
		}
		records.put(record.key(), record);
	}

	public void remove(ContainerKey key) {
		records.remove(key);
	}

	public ContainerKey removeAt(ContainerKey key) {
		if (key == null) {
			return null;
		}
		if (records.remove(key) != null) {
			return key;
		}
		ContainerKey owner = null;
		for (ContainerRecord record : records.values()) {
			if (key.equals(record.secondary())) {
				owner = record.key();
				break;
			}
		}
		if (owner == null) {
			return null;
		}
		records.remove(owner);
		return owner;
	}

	public Collection<ContainerRecord> all() {
		return records.values();
	}

	public ContainerRecord get(ContainerKey key) {
		return key == null ? null : records.get(key);
	}

	public ContainerRecord at(ContainerKey key) {
		ContainerRecord direct = get(key);
		if (direct != null) {
			return direct;
		}
		if (key == null) {
			return null;
		}
		for (ContainerRecord record : records.values()) {
			if (key.equals(record.secondary())) {
				return record;
			}
		}
		return null;
	}

	public List<ContainerRecord> within(String dimensionId, double px, double py, double pz, double radius) {
		return within(dimensionId, px, py, pz, radius, false);
	}

	public List<ContainerRecord> within(String dimensionId, double px, double py, double pz,
			double radius, boolean allDimensions) {
		List<ContainerRecord> out = new ArrayList<>();
		for (ContainerRecord record : records.values()) {
			boolean sameDimension = record.key().dimensionId().equals(dimensionId);
			if (!sameDimension && !allDimensions) {
				continue;
			}
			if (Dimensions.distance(record.key(), dimensionId, px, py, pz) <= radius) {
				out.add(record);
			}
		}
		return out;
	}

	public void evictBeyond(int max) {
		if (records.size() <= max) {
			return;
		}
		List<ContainerRecord> oldestFirst = new ArrayList<>(records.values());
		oldestFirst.sort(Comparator.comparingLong(ContainerRecord::lastSeenEpochMillis));
		int toDrop = records.size() - max;
		for (int i = 0; i < toDrop; i++) {
			records.remove(oldestFirst.get(i).key());
		}
		pool.purgeOrphans(records.values());
	}

	public int size() {
		return records.size();
	}

	public ItemIdentityPool pool() {
		return pool;
	}

	public void clear() {
		records.clear();
		pool.clear();
	}
}
