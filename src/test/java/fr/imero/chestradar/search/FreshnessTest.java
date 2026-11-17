package fr.imero.chestradar.search;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FreshnessTest {
	@Test
	void picksTheLargestUnitThatStillReadsAsAWholeNumber() {
		assertEquals(new Freshness.Age(0, Freshness.Unit.NOW), Freshness.of(0));
		assertEquals(new Freshness.Age(0, Freshness.Unit.NOW), Freshness.of(59_999L));
		assertEquals(new Freshness.Age(1, Freshness.Unit.MINUTES), Freshness.of(Freshness.MINUTE_MS));
		assertEquals(new Freshness.Age(59, Freshness.Unit.MINUTES), Freshness.of(Freshness.HOUR_MS - 1));
		assertEquals(new Freshness.Age(1, Freshness.Unit.HOURS), Freshness.of(Freshness.HOUR_MS));
		assertEquals(new Freshness.Age(23, Freshness.Unit.HOURS), Freshness.of(Freshness.DAY_MS - 1));
		assertEquals(new Freshness.Age(1, Freshness.Unit.DAYS), Freshness.of(Freshness.DAY_MS));
		assertEquals(new Freshness.Age(400, Freshness.Unit.DAYS), Freshness.of(400 * Freshness.DAY_MS));
	}

	@Test
	void aNegativeAgeReadsAsNowRatherThanAsANegativeCount() {
		assertEquals(new Freshness.Age(0, Freshness.Unit.NOW), Freshness.of(-1));
		assertEquals(new Freshness.Age(0, Freshness.Unit.NOW), Freshness.of(-10 * Freshness.DAY_MS));
	}

	@Test
	void betweenIsTheDifferenceOfTheTwoInstants() {
		long now = 1_000_000_000_000L;
		assertEquals(new Freshness.Age(2, Freshness.Unit.DAYS),
			Freshness.between(now - 2 * Freshness.DAY_MS, now));
	}

	@Test
	void stalenessTriggersOnTheThresholdItself() {
		long now = 1_000_000_000_000L;
		assertFalse(Freshness.isStale(now - 6 * Freshness.DAY_MS, now, 7));
		assertTrue(Freshness.isStale(now - 7 * Freshness.DAY_MS, now, 7));
		assertTrue(Freshness.isStale(now - 30 * Freshness.DAY_MS, now, 7));
	}

	@Test
	void zeroDaysDisablesStalenessEntirely() {
		long now = 1_000_000_000_000L;
		assertFalse(Freshness.isStale(now - 3650 * Freshness.DAY_MS, now, 0));
		assertFalse(Freshness.isStale(0L, now, -1));
	}

	@Test
	void everyUnitHasATranslationKey() {
		for (Freshness.Unit unit : Freshness.Unit.values()) {
			assertTrue(Freshness.translationKey(unit).startsWith("chestradar.age."), unit.name());
		}
	}
}
