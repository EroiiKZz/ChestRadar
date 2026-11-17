package fr.imero.chestradar.search;

import fr.imero.chestradar.data.ContainerKey;
import fr.imero.chestradar.data.ContainerKind;
import fr.imero.chestradar.data.ContainerRecord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ContainerDiffTest {
	private static ContainerRecord record(Map<String, Integer> counts, Map<String, Integer> nested) {
		return new ContainerRecord(new ContainerKey("minecraft:overworld", 0, 64, 0), null,
			ContainerKind.CHEST, "Coffre", 1L, 27, counts, nested);
	}

	@Test
	void reportsAdditionsRemovalsAndUntouchedStacks() {
		ContainerRecord before = record(Map.of("fer", 10, "or", 5, "pierre", 64), Map.of());
		ContainerRecord after = record(Map.of("fer", 14, "pierre", 64, "diamant", 3), Map.of());

		List<ContainerDiff.Change> changes = ContainerDiff.between(before, after);

		assertEquals(3, changes.size());
		assertTrue(changes.stream().noneMatch(c -> c.fingerprint().equals("pierre")));

		ContainerDiff.Change iron = changes.stream()
			.filter(c -> c.fingerprint().equals("fer")).findFirst().orElseThrow();
		assertEquals(10, iron.before());
		assertEquals(14, iron.after());
		assertEquals(4, iron.delta());

		ContainerDiff.Change gold = changes.stream()
			.filter(c -> c.fingerprint().equals("or")).findFirst().orElseThrow();
		assertEquals(-5, gold.delta());

		ContainerDiff.Change diamond = changes.stream()
			.filter(c -> c.fingerprint().equals("diamant")).findFirst().orElseThrow();
		assertEquals(0, diamond.before());
		assertEquals(3, diamond.delta());
	}

	@Test
	void biggestChangeComesFirstRegardlessOfSign() {
		ContainerRecord before = record(Map.of("a", 1, "b", 100), Map.of());
		ContainerRecord after = record(Map.of("a", 3, "b", 0), Map.of());

		List<ContainerDiff.Change> changes = ContainerDiff.between(before, after);
		assertEquals("b", changes.get(0).fingerprint());
		assertEquals(-100, changes.get(0).delta());
		assertEquals("a", changes.get(1).fingerprint());
	}

	@Test
	void nestedContentsAreFoldedIntoTheSameTotal() {
		ContainerRecord before = record(Map.of("planche", 0), Map.of("planche", 64));
		ContainerRecord after = record(Map.of("planche", 64), Map.of());
		assertTrue(ContainerDiff.between(before, after).isEmpty());

		ContainerRecord emptied = record(Map.of(), Map.of());
		List<ContainerDiff.Change> changes = ContainerDiff.between(before, emptied);
		assertEquals(1, changes.size());
		assertEquals(-64, changes.get(0).delta());
	}

	@Test
	void aFirstSightIsNotReportedAsOneBigAddition() {
		assertTrue(ContainerDiff.between(null, record(Map.of("fer", 64), Map.of())).isEmpty());
		assertTrue(ContainerDiff.between(record(Map.of("fer", 64), Map.of()), null).isEmpty());
	}

	@Test
	void identicalRecordsProduceNothing() {
		ContainerRecord same = record(Map.of("fer", 10), Map.of("or", 2));
		assertTrue(ContainerDiff.between(same, same).isEmpty());
	}

	@Test
	void orderIsStableForEqualMagnitudes() {
		ContainerRecord before = record(Map.of("bbb", 5, "aaa", 5), Map.of());
		ContainerRecord after = record(Map.of(), Map.of());
		List<ContainerDiff.Change> changes = ContainerDiff.between(before, after);
		assertEquals(List.of("aaa", "bbb"), changes.stream().map(ContainerDiff.Change::fingerprint).toList());
	}
}
