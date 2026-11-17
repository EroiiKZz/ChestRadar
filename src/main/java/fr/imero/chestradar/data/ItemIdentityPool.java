package fr.imero.chestradar.data;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ItemIdentityPool {
	private final Map<String, ItemIdentity> byFingerprint = new HashMap<>();

	public String intern(ItemIdentity identity) {
		byFingerprint.putIfAbsent(identity.fingerprint(), identity);
		return identity.fingerprint();
	}

	public ItemIdentity get(String fingerprint) {
		return byFingerprint.get(fingerprint);
	}

	public Collection<ItemIdentity> all() {
		return byFingerprint.values();
	}

	public void purgeOrphans(Collection<ContainerRecord> records) {
		Set<String> referenced = new HashSet<>();
		for (ContainerRecord record : records) {
			referenced.addAll(record.allFingerprints());
		}
		byFingerprint.keySet().retainAll(referenced);
	}

	public int size() {
		return byFingerprint.size();
	}

	public void clear() {
		byFingerprint.clear();
	}
}
