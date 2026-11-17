package fr.imero.chestradar.search;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Pins {
	public static final int MAX = 64;

	private Pins() {
	}

	public static Pin find(List<Pin> pins, String fingerprint) {
		if (pins == null || fingerprint == null) {
			return null;
		}
		for (Pin pin : pins) {
			if (pin != null && fingerprint.equals(pin.fingerprint())) {
				return pin;
			}
		}
		return null;
	}

	public static boolean isPinned(List<Pin> pins, String fingerprint) {
		return find(pins, fingerprint) != null;
	}

	public static Set<String> fingerprints(List<Pin> pins) {
		Set<String> out = new LinkedHashSet<>();
		if (pins != null) {
			for (Pin pin : pins) {
				if (pin != null && pin.fingerprint() != null) {
					out.add(pin.fingerprint());
				}
			}
		}
		return out;
	}

	public static List<Pin> toggle(List<Pin> pins, Pin candidate) {
		List<Pin> out = new ArrayList<>(pins == null ? List.of() : pins);
		if (candidate == null || candidate.fingerprint() == null || candidate.fingerprint().isBlank()) {
			return out;
		}
		if (out.removeIf(pin -> pin != null && candidate.fingerprint().equals(pin.fingerprint()))) {
			return out;
		}
		if (out.size() >= MAX) {
			return out;
		}
		out.add(candidate);
		return out;
	}

	public static List<Pin> withTarget(List<Pin> pins, String fingerprint, int target) {
		List<Pin> out = new ArrayList<>();
		if (pins == null) {
			return out;
		}
		for (Pin pin : pins) {
			if (pin != null && pin.fingerprint() != null && pin.fingerprint().equals(fingerprint)) {
				out.add(new Pin(pin.fingerprint(), pin.itemId(), pin.displayName(), Math.max(0, target)));
			} else if (pin != null) {
				out.add(pin);
			}
		}
		return out;
	}

	public static List<Pin> sanitize(List<Pin> pins) {
		Map<String, Pin> byFingerprint = new LinkedHashMap<>();
		if (pins != null) {
			for (Pin pin : pins) {
				if (pin == null || pin.fingerprint() == null || pin.fingerprint().isBlank()) {
					continue;
				}
				if (byFingerprint.size() >= MAX && !byFingerprint.containsKey(pin.fingerprint())) {
					continue;
				}
				byFingerprint.putIfAbsent(pin.fingerprint(), new Pin(
					pin.fingerprint(),
					pin.itemId() == null ? "" : pin.itemId(),
					pin.displayName() == null ? "" : pin.displayName(),
					Math.max(0, pin.target())));
			}
		}
		return new ArrayList<>(byFingerprint.values());
	}
}
