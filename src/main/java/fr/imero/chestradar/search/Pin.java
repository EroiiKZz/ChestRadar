package fr.imero.chestradar.search;

public record Pin(String fingerprint, String itemId, String displayName, int target) {
	public boolean hasTarget() {
		return target > 0;
	}
}
