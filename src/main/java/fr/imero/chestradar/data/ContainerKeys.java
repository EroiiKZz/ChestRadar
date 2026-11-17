package fr.imero.chestradar.data;

import java.util.Comparator;

public final class ContainerKeys {
	private static final Comparator<ContainerKey> ORDER =
		Comparator.comparingInt(ContainerKey::x)
			.thenComparingInt(ContainerKey::y)
			.thenComparingInt(ContainerKey::z);

	private ContainerKeys() {
	}

	public static ContainerKey primaryOf(ContainerKey a, ContainerKey b) {
		if (b == null) {
			return a;
		}
		if (a == null) {
			return b;
		}
		return ORDER.compare(a, b) <= 0 ? a : b;
	}
}
