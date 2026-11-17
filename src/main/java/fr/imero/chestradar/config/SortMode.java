package fr.imero.chestradar.config;

public enum SortMode {
	NAME, COUNT, DISTANCE;

	public SortMode next() {
		return values()[(ordinal() + 1) % values().length];
	}
}
