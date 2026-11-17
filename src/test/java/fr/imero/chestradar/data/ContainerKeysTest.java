package fr.imero.chestradar.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContainerKeysTest {
	@Test
	void distanceIsEuclideanInThreeDimensions() {
		ContainerKey key = new ContainerKey("minecraft:overworld", 3, 4, 0);
		assertEquals(5.0, key.distanceTo(0, 0, 0), 1e-9);
	}

	@Test
	void primaryIsStableRegardlessOfArgumentOrder() {
		ContainerKey a = new ContainerKey("minecraft:overworld", 10, 64, 20);
		ContainerKey b = new ContainerKey("minecraft:overworld", 11, 64, 20);
		assertEquals(ContainerKeys.primaryOf(a, b), ContainerKeys.primaryOf(b, a),
			"ouvrir l'une ou l'autre moitié doit désigner la même entrée (spec § 5.3)");
	}

	@Test
	void primaryPicksLowestCoordinateDeterministically() {
		ContainerKey a = new ContainerKey("minecraft:overworld", 10, 64, 20);
		ContainerKey b = new ContainerKey("minecraft:overworld", 11, 64, 20);
		assertEquals(a, ContainerKeys.primaryOf(a, b));
	}

	@Test
	void primaryBreaksTiesOnYThenZ() {
		ContainerKey low = new ContainerKey("minecraft:overworld", 5, 60, 20);
		ContainerKey high = new ContainerKey("minecraft:overworld", 5, 70, 20);
		assertEquals(low, ContainerKeys.primaryOf(high, low));

		ContainerKey near = new ContainerKey("minecraft:overworld", 5, 60, 19);
		assertEquals(near, ContainerKeys.primaryOf(low, near));
	}

	@Test
	void primaryOfSingleContainerIsItself() {
		ContainerKey a = new ContainerKey("minecraft:overworld", 10, 64, 20);
		assertEquals(a, ContainerKeys.primaryOf(a, null));
	}

	@Test
	void keysAreValueEqualForMapUsage() {
		assertEquals(new ContainerKey("minecraft:the_nether", 1, 2, 3),
			new ContainerKey("minecraft:the_nether", 1, 2, 3));
		assertEquals(new ContainerKey("minecraft:the_nether", 1, 2, 3).hashCode(),
			new ContainerKey("minecraft:the_nether", 1, 2, 3).hashCode());
	}

	@Test
	void sameCoordinatesInDifferentDimensionsAreDifferentKeys() {
		assertNotEquals(new ContainerKey("minecraft:overworld", 1, 2, 3),
			new ContainerKey("minecraft:the_nether", 1, 2, 3));
	}
}
