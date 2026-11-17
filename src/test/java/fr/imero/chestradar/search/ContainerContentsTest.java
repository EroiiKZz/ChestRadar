package fr.imero.chestradar.search;

import fr.imero.chestradar.data.ContainerKey;
import fr.imero.chestradar.data.ContainerKind;
import fr.imero.chestradar.data.ContainerRecord;
import fr.imero.chestradar.data.ItemIdentities;
import fr.imero.chestradar.data.ItemIdentityPool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ContainerContentsTest {
	private final ItemIdentityPool pool = new ItemIdentityPool();

	private String intern(String id, String name) {
		return pool.intern(ItemIdentities.of(id, "{}", name, "", List.of()));
	}

	private static ContainerRecord record(Map<String, Integer> counts, Map<String, Integer> nested) {
		return new ContainerRecord(new ContainerKey("minecraft:overworld", 0, 64, 0), null,
			ContainerKind.CHEST, "Coffre", 1L, 27, counts, nested);
	}

	@Test
	void sortsByDescendingQuantity() {
		String iron = intern("minecraft:iron_ingot", "Fer");
		String gold = intern("minecraft:gold_ingot", "Or");
		String stone = intern("minecraft:stone", "Pierre");

		List<ContainerContents.Entry> all = ContainerContents.all(
			record(Map.of(iron, 12, gold, 3, stone, 128), Map.of()), pool);

		assertEquals(List.of("Pierre", "Fer", "Or"),
			all.stream().map(e -> e.identity().displayName()).toList());
	}

	@Test
	void nestedContentsAreAddedToTheDirectCount() {
		String plank = intern("minecraft:oak_planks", "Planches");
		List<ContainerContents.Entry> all = ContainerContents.all(
			record(Map.of(plank, 12), Map.of(plank, 64)), pool);
		assertEquals(1, all.size());
		assertEquals(76, all.get(0).count());
	}

	@Test
	void orderIsStableAcrossCallsDespiteTheUnderlyingHashSet() {
		String a = intern("minecraft:stone", "Alpha");
		String b = intern("minecraft:dirt", "Beta");
		String c = intern("minecraft:sand", "Gamma");
		ContainerRecord chest = record(Map.of(a, 5, b, 5, c, 5), Map.of());

		List<String> first = ContainerContents.all(chest, pool).stream()
			.map(e -> e.identity().displayName()).toList();
		assertEquals(List.of("Alpha", "Beta", "Gamma"), first);
		for (int i = 0; i < 20; i++) {
			assertEquals(first, ContainerContents.all(chest, pool).stream()
				.map(e -> e.identity().displayName()).toList());
		}
	}

	@Test
	void previewReportsWhatItHadToTruncate() {
		String iron = intern("minecraft:iron_ingot", "Fer");
		String gold = intern("minecraft:gold_ingot", "Or");
		String stone = intern("minecraft:stone", "Pierre");
		String dirt = intern("minecraft:dirt", "Terre");

		ContainerContents.Preview preview = ContainerContents.preview(
			record(Map.of(stone, 128, iron, 12, gold, 3, dirt, 1), Map.of()), pool, 2);

		assertEquals(2, preview.shown().size());
		assertEquals("Pierre", preview.shown().get(0).identity().displayName());
		assertEquals(2, preview.hiddenEntries());
		assertEquals(4, preview.hiddenCount());
	}

	@Test
	void previewWithRoomToSpareHidesNothing() {
		String iron = intern("minecraft:iron_ingot", "Fer");
		ContainerContents.Preview preview = ContainerContents.preview(
			record(Map.of(iron, 12), Map.of()), pool, 8);
		assertEquals(1, preview.shown().size());
		assertEquals(0, preview.hiddenEntries());
		assertEquals(0, preview.hiddenCount());
		assertFalse(preview.isEmpty());
	}

	@Test
	void anEmptyContainerPreviewsAsEmpty() {
		assertTrue(ContainerContents.preview(record(Map.of(), Map.of()), pool, 8).isEmpty());
		assertEquals(0, ContainerContents.totalCount(record(Map.of(), Map.of()), pool));
	}

	@Test
	void aFingerprintMissingFromThePoolIsSkippedRatherThanCrashing() {
		String iron = intern("minecraft:iron_ingot", "Fer");
		ContainerRecord chest = record(Map.of(iron, 5, "ffffffffffffffff", 99), Map.of());
		assertEquals(1, ContainerContents.all(chest, pool).size());
		assertEquals(5, ContainerContents.totalCount(chest, pool));
	}

	@Test
	void nullArgumentsNeverThrow() {
		assertTrue(ContainerContents.all(null, pool).isEmpty());
		assertTrue(ContainerContents.all(record(Map.of(), Map.of()), null).isEmpty());
		assertTrue(ContainerContents.preview(null, pool, 4).isEmpty());
	}
}
