package fr.imero.chestradar.data;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ContainerIndexTest {
	private static ItemIdentity identity(String name) {
		return ItemIdentities.of("minecraft:enchanted_book",
			"{\"minecraft:custom_name\":\"'" + name + "'\"}", name, "", List.of());
	}

	private static ContainerRecord record(int x, int z, long seen, Map<String, Integer> counts) {
		return new ContainerRecord(new ContainerKey("minecraft:overworld", x, 64, z),
			null, ContainerKind.CHEST, "Chest", seen, 27, counts, Map.of());
	}

	@Test
	void upsertReplacesRatherThanDuplicates() {
		ContainerIndex index = new ContainerIndex();
		index.upsert(record(0, 0, 1L, Map.of("aa", 5)));
		index.upsert(record(0, 0, 2L, Map.of("aa", 9)));
		assertEquals(1, index.size());
		assertEquals(9, index.all().iterator().next().counts().get("aa"));
	}

	@Test
	void upsertOfDoubleChestRemovesPreviouslyIndexedOtherHalf() {
		ContainerIndex index = new ContainerIndex();
		index.upsert(record(11, 20, 1L, Map.of("aa", 1)));

		ContainerKey primary = new ContainerKey("minecraft:overworld", 10, 64, 20);
		ContainerKey secondary = new ContainerKey("minecraft:overworld", 11, 64, 20);
		index.upsert(new ContainerRecord(primary, secondary, ContainerKind.CHEST,
			"Large Chest", 2L, 54, Map.of("aa", 3), Map.of()));

		assertEquals(1, index.size(), "la moitié orpheline doit disparaître (spec § 5.3)");
		assertEquals(primary, index.all().iterator().next().key());
	}

	@Test
	void withinExcludesContainersBeyondRadius() {
		ContainerIndex index = new ContainerIndex();
		index.upsert(record(10, 0, 1L, Map.of("aa", 1)));
		index.upsert(record(500, 0, 1L, Map.of("aa", 1)));
		List<ContainerRecord> near = index.within("minecraft:overworld", 0, 64, 0, 128);
		assertEquals(1, near.size());
		assertEquals(10, near.get(0).key().x());
	}

	@Test
	void withinExcludesOtherDimensions() {
		ContainerIndex index = new ContainerIndex();
		index.upsert(new ContainerRecord(new ContainerKey("minecraft:the_nether", 1, 64, 1),
			null, ContainerKind.CHEST, "Chest", 1L, 27, Map.of("aa", 1), Map.of()));
		assertTrue(index.within("minecraft:overworld", 0, 64, 0, 1024).isEmpty());
	}

	@Test
	void removeAtDropsTheRecordStandingAtThatPosition() {
		ContainerIndex index = new ContainerIndex();
		index.upsert(record(4, 7, 1L, Map.of("aa", 1)));

		ContainerKey broken = new ContainerKey("minecraft:overworld", 4, 64, 7);
		assertEquals(broken, index.removeAt(broken));
		assertEquals(0, index.size());
	}

	@Test
	void removeAtDropsTheWholeDoubleChestWhenItsOtherHalfIsBroken() {
		ContainerIndex index = new ContainerIndex();
		ContainerKey primary = new ContainerKey("minecraft:overworld", 10, 64, 20);
		ContainerKey secondary = new ContainerKey("minecraft:overworld", 11, 64, 20);
		index.upsert(new ContainerRecord(primary, secondary, ContainerKind.CHEST,
			"Large Chest", 1L, 54, Map.of("aa", 3), Map.of()));

		assertEquals(primary, index.removeAt(secondary));
		assertEquals(0, index.size());
	}

	@Test
	void removeAtLeavesTheIndexAloneWhenNothingIsRecordedThere() {
		ContainerIndex index = new ContainerIndex();
		index.upsert(record(4, 7, 1L, Map.of("aa", 1)));

		assertNull(index.removeAt(new ContainerKey("minecraft:overworld", 99, 64, 99)));
		assertNull(index.removeAt(null));
		assertEquals(1, index.size());
	}

	@Test
	void evictBeyondDropsOldestLastSeenFirst() {
		ContainerIndex index = new ContainerIndex();
		index.upsert(record(1, 0, 100L, Map.of("aa", 1)));
		index.upsert(record(2, 0, 300L, Map.of("aa", 1)));
		index.upsert(record(3, 0, 200L, Map.of("aa", 1)));
		index.evictBeyond(2);
		assertEquals(2, index.size());
		assertTrue(index.all().stream().noneMatch(r -> r.key().x() == 1));
	}

	@Test
	void poolDeduplicatesEqualIdentities() {
		ItemIdentityPool pool = new ItemIdentityPool();
		String first = pool.intern(identity("Radioactif IV"));
		String second = pool.intern(identity("Radioactif IV"));
		assertEquals(first, second);
		assertEquals(1, pool.size());
	}

	@Test
	void poolKeepsDistinctCustomItemsSeparate() {
		ItemIdentityPool pool = new ItemIdentityPool();
		pool.intern(identity("Radioactif IV"));
		pool.intern(identity("Radioactif III"));
		assertEquals(2, pool.size());
	}

	@Test
	void purgeOrphansDropsIdentitiesNoContainerReferences() {
		ItemIdentityPool pool = new ItemIdentityPool();
		String kept = pool.intern(identity("Gardé"));
		pool.intern(identity("Orphelin"));
		pool.purgeOrphans(List.of(record(0, 0, 1L, Map.of(kept, 1))));
		assertEquals(1, pool.size());
		assertNotNull(pool.get(kept));
	}

	@Test
	void clearEmptiesRecordsAndPool() {
		ContainerIndex index = new ContainerIndex();
		index.pool().intern(identity("X"));
		index.upsert(record(0, 0, 1L, Map.of("aa", 1)));
		index.clear();
		assertEquals(0, index.size());
		assertEquals(0, index.pool().size());
	}
}
