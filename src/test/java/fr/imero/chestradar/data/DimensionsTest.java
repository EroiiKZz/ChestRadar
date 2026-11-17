package fr.imero.chestradar.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DimensionsTest {
	@Test
	void sameDimensionIsTheUnchangedEuclideanDistance() {
		ContainerKey key = new ContainerKey(Dimensions.OVERWORLD, 3, 64, 4);
		assertEquals(5.0, Dimensions.distance(key, Dimensions.OVERWORLD, 0, 64, 0), 1e-9);
		assertEquals(key.distanceTo(0, 64, 0), Dimensions.distance(key, Dimensions.OVERWORLD, 0, 64, 0), 1e-9);
	}

	@Test
	void netherCoordinatesAreScaledIntoTheOverworldFrame() {
		ContainerKey key = new ContainerKey(Dimensions.NETHER, 100, 64, 0);
		assertEquals(800.0, Dimensions.distance(key, Dimensions.OVERWORLD, 0, 64, 0), 1e-9);
	}

	@Test
	void overworldCoordinatesAreScaledIntoTheNetherFrame() {
		ContainerKey key = new ContainerKey(Dimensions.OVERWORLD, 800, 64, 0);
		assertEquals(100.0, Dimensions.distance(key, Dimensions.NETHER, 0, 64, 0), 1e-9);
	}

	@Test
	void verticalDistanceIsNeverScaled() {
		ContainerKey key = new ContainerKey(Dimensions.NETHER, 0, 100, 0);
		assertEquals(36.0, Dimensions.distance(key, Dimensions.OVERWORLD, 0, 64, 0), 1e-9);
	}

	@Test
	void theEndIsNotScaledAgainstAnything() {
		assertEquals(1.0, Dimensions.horizontalScale(Dimensions.END, Dimensions.OVERWORLD));
		assertEquals(1.0, Dimensions.horizontalScale(Dimensions.OVERWORLD, Dimensions.END));
		assertEquals(1.0, Dimensions.horizontalScale("moddedpack:cavern", Dimensions.NETHER));
	}

	@Test
	void nullDimensionsNeverThrowAndNeverScale() {
		assertEquals(1.0, Dimensions.horizontalScale(null, Dimensions.OVERWORLD));
		assertEquals(1.0, Dimensions.horizontalScale(Dimensions.OVERWORLD, null));
	}

	@Test
	void shortNameDropsNamespaceAndTheUnderscore() {
		assertEquals("nether", Dimensions.shortName(Dimensions.NETHER));
		assertEquals("end", Dimensions.shortName(Dimensions.END));
		assertEquals("overworld", Dimensions.shortName(Dimensions.OVERWORLD));
		assertEquals("cavern", Dimensions.shortName("moddedpack:cavern"));
		assertEquals("?", Dimensions.shortName(null));
		assertEquals("?", Dimensions.shortName("  "));
	}

	@Test
	void matchesAcceptsTheShortNameAsWellAsTheFullId() {
		assertTrue(Dimensions.matches(Dimensions.NETHER, "nether"));
		assertTrue(Dimensions.matches(Dimensions.NETHER, "the_nether"));
		assertTrue(Dimensions.matches(Dimensions.NETHER, "MINECRAFT:the_neth"));
		assertFalse(Dimensions.matches(Dimensions.NETHER, "end"));
		assertTrue(Dimensions.matches(Dimensions.NETHER, ""));
	}
}
