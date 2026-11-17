package fr.imero.chestradar.search;

import fr.imero.chestradar.data.ContainerRecord;

public record Hit(ContainerRecord container, double distance, int count, int nestedCount) {
	public int totalCount() {
		return count + nestedCount;
	}
}
