package fr.imero.chestradar.search;

import fr.imero.chestradar.config.SortMode;
import fr.imero.chestradar.data.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RadarQueryTest {
	private static final String DIM = "minecraft:overworld";

	private ContainerIndex index;
	private String bookFp;
	private String plainBookFp;
	private String hoeFp;

	private ContainerIndex build() {
		index = new ContainerIndex();
		ItemIdentity book = ItemIdentities.of("minecraft:enchanted_book",
			"{\"minecraft:custom_name\":\"'Radioactif IV'\"}", "Radioactif IV",
			"Contamine les cibles proches", List.of("serverpack:radioactivity"));
		ItemIdentity plainBook = ItemIdentities.of("minecraft:enchanted_book", "{}", "Livre enchante", "", List.of());
		ItemIdentity hoe = ItemIdentities.of("minecraft:golden_hoe", "{\"minecraft:enchantments\":{}}",
			"Faux du Vide", "", List.of("minecraft:knockback"));

		bookFp = index.pool().intern(book);
		plainBookFp = index.pool().intern(plainBook);
		hoeFp = index.pool().intern(hoe);

		index.upsert(chest(10, 0, Map.of(bookFp, 3, hoeFp, 1)));
		index.upsert(chest(40, 0, Map.of(bookFp, 5)));
		index.upsert(chest(300, 0, Map.of(bookFp, 99)));
		index.upsert(chest(20, 0, Map.of(plainBookFp, 2)));
		return index;
	}

	private static ContainerRecord chest(int x, int z, Map<String, Integer> counts) {
		return new ContainerRecord(new ContainerKey(DIM, x, 64, z), null,
			ContainerKind.CHEST, "Chest", 1L, 27, counts, Map.of());
	}

	@Test
	void findByFingerprintReturnsOnlyTheExactCustomItem() {
		build();
		List<Hit> hits = RadarQuery.findByFingerprint(index, DIM, 0, 64, 0, 128, bookFp);
		assertEquals(2, hits.size(), "le coffre a 300 blocs est hors rayon");
		assertTrue(hits.stream().allMatch(h -> h.count() > 0));
	}

	@Test
	void findByFingerprintDoesNotMatchPlainItemOfSameType() {
		build();
		List<Hit> hits = RadarQuery.findByFingerprint(index, DIM, 0, 64, 0, 128, bookFp);
		assertTrue(hits.stream().noneMatch(h -> h.container().key().x() == 20),
			"chercher un Radioactif IV ne doit pas remonter les livres enchantes generiques");
	}

	@Test
	void findByItemIdWidensToEveryVariantOfTheItem() {
		build();
		List<Hit> hits = RadarQuery.findByItemId(index, DIM, 0, 64, 0, 128, "minecraft:enchanted_book");
		assertEquals(3, hits.size());
	}

	@Test
	void findByItemIdsSumsSeveralAcceptedItemsIntoOneHitPerContainer() {
		build();
		List<Hit> hits = RadarQuery.findByItemIds(index, DIM, 0, 64, 0, 128,
			java.util.Set.of("minecraft:enchanted_book", "minecraft:golden_hoe"), false, RadarQuery.ANY);

		Hit nearest = hits.get(0);
		assertEquals(10, nearest.container().key().x());
		assertEquals(4, nearest.totalCount());
		assertEquals(3, hits.size(), "un seul hit par conteneur, quel que soit le nombre de candidats");
	}

	@Test
	void findByItemIdsWithNoCandidateFindsNothing() {
		build();
		assertTrue(RadarQuery.findByItemIds(index, DIM, 0, 64, 0, 128, java.util.Set.of(),
			false, RadarQuery.ANY).isEmpty());
	}

	@Test
	void nearestHitIsTheClosestContainerEvenIfHitsAreNotSorted() {
		ItemIdentity any = ItemIdentities.of("minecraft:stone", "{}", "Pierre", "", List.of());
		Hit far = new Hit(chest(90, 0, Map.of(any.fingerprint(), 1)), 90.0, 1, 0);
		Hit near = new Hit(chest(2, 0, Map.of(any.fingerprint(), 1)), 2.0, 1, 0);
		AggregatedItem item = new AggregatedItem(any, 2, List.of(far, near));
		assertEquals(near, item.nearestHit());
	}

	@Test
	void nearestHitIsNullWhenThereAreNoHits() {
		ItemIdentity any = ItemIdentities.of("minecraft:stone", "{}", "Pierre", "", List.of());
		assertNull(new AggregatedItem(any, 0, List.of()).nearestHit());
	}

	@Test
	void hitsAreSortedByAscendingDistance() {
		build();
		List<Hit> hits = RadarQuery.findByItemId(index, DIM, 0, 64, 0, 128, "minecraft:enchanted_book");
		for (int i = 1; i < hits.size(); i++) {
			assertTrue(hits.get(i - 1).distance() <= hits.get(i).distance());
		}
	}

	@Test
	void aggregateSumsQuantitiesAcrossContainers() {
		build();
		AggregatedItem book = RadarQuery.aggregate(index, DIM, 0, 64, 0, 128).stream()
			.filter(a -> a.identity().fingerprint().equals(bookFp)).findFirst().orElseThrow();
		assertEquals(8, book.totalCount(), "3 + 5, le coffre hors rayon est exclu");
		assertEquals(2, book.hits().size());
	}

	@Test
	void aggregateExcludesOtherDimensions() {
		build();
		assertTrue(RadarQuery.aggregate(index, "minecraft:the_nether", 0, 64, 0, 1024).isEmpty());
	}

	@Test
	void filterMatchesCustomName() {
		build();
		List<AggregatedItem> found = RadarQuery.filter(
			RadarQuery.aggregate(index, DIM, 0, 64, 0, 128), "radioactif", true);
		assertEquals(1, found.size());
		assertEquals(bookFp, found.get(0).identity().fingerprint());
	}

	@Test
	void filterMatchesEnchantmentNameIncludingServerOverride() {
		build();
		List<AggregatedItem> found = RadarQuery.filter(
			RadarQuery.aggregate(index, DIM, 0, 64, 0, 128), "knockback", true);
		assertEquals(1, found.size());
		assertEquals(hoeFp, found.get(0).identity().fingerprint());
	}

	@Test
	void filterMatchesLoreOnlyWhenLoreIsIncluded() {
		build();
		List<AggregatedItem> all = RadarQuery.aggregate(index, DIM, 0, 64, 0, 128);
		assertEquals(1, RadarQuery.filter(all, "contamine", true).size());
		assertEquals(0, RadarQuery.filter(all, "contamine", false).size());
	}

	@Test
	void filterIsCaseInsensitive() {
		build();
		assertEquals(1, RadarQuery.filter(
			RadarQuery.aggregate(index, DIM, 0, 64, 0, 128), "RADIOACTIF", true).size());
	}

	@Test
	void emptyFilterReturnsEverything() {
		build();
		List<AggregatedItem> all = RadarQuery.aggregate(index, DIM, 0, 64, 0, 128);
		assertEquals(all.size(), RadarQuery.filter(all, "   ", true).size());
	}

	@Test
	void sortByCountIsDescending() {
		build();
		List<AggregatedItem> sorted = RadarQuery.sort(
			RadarQuery.aggregate(index, DIM, 0, 64, 0, 128), SortMode.COUNT);
		for (int i = 1; i < sorted.size(); i++) {
			assertTrue(sorted.get(i - 1).totalCount() >= sorted.get(i).totalCount());
		}
	}

	@Test
	void sortByNameIsAlphabetical() {
		build();
		List<AggregatedItem> sorted = RadarQuery.sort(
			RadarQuery.aggregate(index, DIM, 0, 64, 0, 128), SortMode.NAME);
		assertEquals("Faux du Vide", sorted.get(0).identity().displayName());
	}

	@Test
	void sortByDistanceUsesNearestContainerOfEachItem() {
		build();
		List<AggregatedItem> sorted = RadarQuery.sort(
			RadarQuery.aggregate(index, DIM, 0, 64, 0, 128), SortMode.DISTANCE);
		for (int i = 1; i < sorted.size(); i++) {
			assertTrue(sorted.get(i - 1).nearestDistance() <= sorted.get(i).nearestDistance());
		}
	}

	@Test
	void identityMissingFromPoolIsSkippedRatherThanCrashing() {
		ContainerIndex broken = new ContainerIndex();
		broken.upsert(chest(1, 0, Map.of("ffffffffffffffff", 4)));
		assertTrue(RadarQuery.aggregate(broken, DIM, 0, 64, 0, 128).isEmpty());
	}

	@Test
	void aggregateSumsDirectAndNestedCounts() {
		build();
		ContainerIndex idx = new ContainerIndex();
		ItemIdentity plank = ItemIdentities.of("minecraft:oak_planks", "{}", "Planches", "", List.of());
		String fp = idx.pool().intern(plank);
		idx.upsert(new ContainerRecord(new ContainerKey(DIM, 5, 64, 0), null,
			ContainerKind.CHEST, "Chest", 1L, 27, Map.of(fp, 12), Map.of(fp, 64)));

		AggregatedItem item = RadarQuery.aggregate(idx, DIM, 0, 64, 0, 128).get(0);
		assertEquals(76, item.totalCount(), "12 en vrac + 64 en shulker");
		assertEquals(12, item.hits().get(0).count());
		assertEquals(64, item.hits().get(0).nestedCount());
	}

	@Test
	void itemPresentOnlyInsideAShulkerIsStillFound() {
		ContainerIndex idx = new ContainerIndex();
		ItemIdentity plank = ItemIdentities.of("minecraft:oak_planks", "{}", "Planches", "", List.of());
		String fp = idx.pool().intern(plank);
		idx.upsert(new ContainerRecord(new ContainerKey(DIM, 5, 64, 0), null,
			ContainerKind.CHEST, "Chest", 1L, 27, Map.of(), Map.of(fp, 64)));

		assertEquals(1, RadarQuery.findByFingerprint(idx, DIM, 0, 64, 0, 128, fp).size(),
			"un item uniquement imbrique doit rester trouvable");
		assertEquals(1, RadarQuery.aggregate(idx, DIM, 0, 64, 0, 128).size());
	}

	private static final String NETHER = "minecraft:the_nether";

	private ContainerIndex crossDimension() {
		ContainerIndex idx = new ContainerIndex();
		ItemIdentity iron = ItemIdentities.of("minecraft:iron_ingot", "{}", "Lingot de fer", "", List.of());
		String fp = idx.pool().intern(iron);
		idx.upsert(new ContainerRecord(new ContainerKey(DIM, 800, 64, 0), null,
			ContainerKind.CHEST, "Base", 1L, 27, Map.of(fp, 10), Map.of()));
		idx.upsert(new ContainerRecord(new ContainerKey(NETHER, 10, 64, 0), null,
			ContainerKind.BARREL, "Avant-poste", 1L, 27, Map.of(fp, 7), Map.of()));
		return idx;
	}

	@Test
	void otherDimensionsStayInvisibleUnlessAskedFor() {
		ContainerIndex idx = crossDimension();
		List<AggregatedItem> strict = RadarQuery.aggregate(idx, NETHER, 0, 64, 0, 256);
		assertEquals(7, strict.get(0).totalCount());
	}

	@Test
	void allDimensionsFoldsTheOtherWorldsInWithConvertedDistances() {
		ContainerIndex idx = crossDimension();
		List<AggregatedItem> wide = RadarQuery.aggregate(idx, NETHER, 0, 64, 0, 256, true, RadarQuery.ANY);
		assertEquals(1, wide.size());
		assertEquals(17, wide.get(0).totalCount(), "7 dans le Nether + 10 dans l'Overworld");
		List<Hit> hits = wide.get(0).hits();
		assertEquals(10.0, hits.get(0).distance(), 1e-9, "l'avant-poste du Nether, trie en premier");
		assertEquals(100.0, hits.get(1).distance(), 1e-9, "800 blocs d'Overworld valent 100 de Nether");
	}

	@Test
	void aContainerBeyondTheConvertedRadiusIsStillExcluded() {
		ContainerIndex idx = crossDimension();
		List<AggregatedItem> tight = RadarQuery.aggregate(idx, NETHER, 0, 64, 0, 99, true, RadarQuery.ANY);
		assertEquals(1, tight.size());
		assertEquals(7, tight.get(0).totalCount());
		assertEquals(1, tight.get(0).hits().size());
	}

	@Test
	void findByFingerprintAndFindByItemIdHonourAllDimensionsToo() {
		ContainerIndex idx = crossDimension();
		String fp = idx.all().iterator().next().allFingerprints().iterator().next();
		assertEquals(1, RadarQuery.findByFingerprint(idx, NETHER, 0, 64, 0, 256, fp).size());
		assertEquals(2, RadarQuery.findByFingerprint(idx, NETHER, 0, 64, 0, 256, fp, true, RadarQuery.ANY).size());
		assertEquals(2, RadarQuery.findByItemId(idx, NETHER, 0, 64, 0, 256, "minecraft:iron_ingot",
			true, RadarQuery.ANY).size());
	}

	@Test
	void theAcceptPredicateFiltersBeforeAggregationNotAfter() {
		ContainerIndex idx = crossDimension();
		List<AggregatedItem> onlyBarrels = RadarQuery.aggregate(idx, NETHER, 0, 64, 0, 256, true,
			record -> record.kind() == ContainerKind.BARREL);
		assertEquals(7, onlyBarrels.get(0).totalCount());
		assertEquals(1, onlyBarrels.get(0).hits().size());
	}

	@Test
	void anAcceptPredicateThatRejectsEverythingYieldsNothing() {
		ContainerIndex idx = crossDimension();
		assertTrue(RadarQuery.aggregate(idx, NETHER, 0, 64, 0, 256, true, record -> false).isEmpty());
	}

	@Test
	void pinnedItemsComeFirstWhateverTheSortAndKeepThatSortAmongThemselves() {
		build();
		List<AggregatedItem> all = RadarQuery.aggregate(index, DIM, 0, 64, 0, 128);
		List<AggregatedItem> sorted = RadarQuery.sort(all, SortMode.COUNT, java.util.Set.of(hoeFp));
		assertEquals(hoeFp, sorted.get(0).identity().fingerprint());
		for (int i = 2; i < sorted.size(); i++) {
			assertTrue(sorted.get(i - 1).totalCount() >= sorted.get(i).totalCount());
		}
	}

	@Test
	void twoPinnedItemsAreOrderedBetweenThemselvesByTheActiveSort() {
		build();
		List<AggregatedItem> all = RadarQuery.aggregate(index, DIM, 0, 64, 0, 128);
		List<AggregatedItem> sorted = RadarQuery.sort(all, SortMode.COUNT, java.util.Set.of(hoeFp, bookFp));
		assertEquals(bookFp, sorted.get(0).identity().fingerprint(), "8 exemplaires avant 1");
		assertEquals(hoeFp, sorted.get(1).identity().fingerprint());
	}

	@Test
	void anEmptyPinSetLeavesTheSortUntouched() {
		build();
		List<AggregatedItem> all = RadarQuery.aggregate(index, DIM, 0, 64, 0, 128);
		assertEquals(RadarQuery.sort(all, SortMode.NAME), RadarQuery.sort(all, SortMode.NAME, java.util.Set.of()));
	}

	@Test
	void structuredFilterIsWiredThroughRadarQuery() {
		build();
		List<AggregatedItem> all = RadarQuery.aggregate(index, DIM, 0, 64, 0, 128);
		SearchQuery.Context ctx = new SearchQuery.Context(true, System.currentTimeMillis());
		assertEquals(1, RadarQuery.filter(all, SearchQuery.parse("ench:knockback"), ctx).size());
		assertEquals(all.size(), RadarQuery.filter(all, SearchQuery.parse(""), ctx).size());
		assertTrue(RadarQuery.filter(all, SearchQuery.parse("radioactif -enchanted"), ctx).isEmpty());
	}
}
