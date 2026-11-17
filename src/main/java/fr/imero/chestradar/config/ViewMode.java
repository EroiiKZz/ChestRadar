package fr.imero.chestradar.config;

public enum ViewMode {
	LIST, GRID;

	public ViewMode next() {
		return this == LIST ? GRID : LIST;
	}
}
