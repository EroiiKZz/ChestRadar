package fr.imero.chestradar.search;

import fr.imero.chestradar.data.ContainerKey;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CollectionRouteTest {
	private static final String OVERWORLD = "minecraft:overworld";

	private static ContainerKey key(int x, int z) {
		return new ContainerKey(OVERWORLD, x, 64, z);
	}

	private static CollectionRoute.Source source(ContainerKey key, String title, String needKey, int available) {
		return new CollectionRoute.Source(key, title, needKey, available);
	}

	@Test
	void visitsTheNearestContainerFirstAndTakesWhatIsNeeded() {
		CollectionRoute.Route route = CollectionRoute.plan(
			List.of(new CollectionRoute.Need("fer", "Lingot de fer", 30)),
			List.of(source(key(50, 0), "Loin", "fer", 64),
				source(key(5, 0), "Proche", "fer", 64)),
			OVERWORLD, 0, 64, 0, 8);

		assertEquals(1, route.size());
		assertEquals("Proche", route.stops().get(0).title());
		assertEquals(30, route.stops().get(0).totalItems());
		assertTrue(route.unmet().isEmpty());
	}

	@Test
	void oneStopServesEveryNeedThatContainerHolds() {
		ContainerKey chest = key(10, 0);
		CollectionRoute.Route route = CollectionRoute.plan(
			List.of(new CollectionRoute.Need("fer", "Fer", 10),
				new CollectionRoute.Need("charbon", "Charbon", 5)),
			List.of(source(chest, "Coffre mixte", "fer", 64),
				source(chest, "Coffre mixte", "charbon", 64)),
			OVERWORLD, 0, 64, 0, 8);

		assertEquals(1, route.size(), "un seul conteneur, une seule étape");
		List<CollectionRoute.Take> takes = route.stops().get(0).takes();
		assertEquals(2, takes.size());
		assertEquals("fer", takes.get(0).needKey());
		assertEquals(10, takes.get(0).quantity());
		assertEquals(5, takes.get(1).quantity());
	}

	@Test
	void aNeedSpreadOverTwoContainersProducesTwoStops() {
		CollectionRoute.Route route = CollectionRoute.plan(
			List.of(new CollectionRoute.Need("fer", "Fer", 100)),
			List.of(source(key(5, 0), "A", "fer", 64), source(key(20, 0), "B", "fer", 64)),
			OVERWORLD, 0, 64, 0, 8);

		assertEquals(2, route.size());
		assertEquals(64, route.stops().get(0).totalItems());
		assertEquals(36, route.stops().get(1).totalItems(), "la seconde étape ne prend que le reliquat");
		assertTrue(route.unmet().isEmpty());
	}

	@Test
	void theSecondStopIsChosenFromTheFirstStopNotFromThePlayer() {
		CollectionRoute.Route route = CollectionRoute.plan(
			List.of(new CollectionRoute.Need("fer", "Fer", 3)),
			List.of(source(key(100, 0), "B", "fer", 1),
				source(key(101, 0), "D", "fer", 1),
				source(key(10, 0), "A", "fer", 1)),
			OVERWORLD, 0, 64, 0, 8);

		assertEquals(List.of("A", "B", "D"),
			route.stops().stream().map(CollectionRoute.Stop::title).toList());
	}

	@Test
	void containersInAnotherDimensionAreSkippedAndCounted() {
		CollectionRoute.Route route = CollectionRoute.plan(
			List.of(new CollectionRoute.Need("fer", "Fer", 10)),
			List.of(source(new ContainerKey("minecraft:the_nether", 5, 64, 0), "Nether", "fer", 64),
				source(key(40, 0), "Overworld", "fer", 4)),
			OVERWORLD, 0, 64, 0, 8);

		assertEquals(1, route.size());
		assertEquals("Overworld", route.stops().get(0).title());
		assertEquals(1, route.skippedSources());
		assertEquals(6, route.unmet().get(0).quantity());
	}

	@Test
	void unreachableRemainderIsReportedRatherThanDropped() {
		CollectionRoute.Route route = CollectionRoute.plan(
			List.of(new CollectionRoute.Need("fer", "Fer", 100),
				new CollectionRoute.Need("diamant", "Diamant", 5)),
			List.of(source(key(5, 0), "A", "fer", 10)),
			OVERWORLD, 0, 64, 0, 8);

		assertEquals(1, route.size());
		assertEquals(2, route.unmet().size());
		assertEquals(90, route.unmet().get(0).quantity());
		assertEquals("diamant", route.unmet().get(1).key());
	}

	@Test
	void stopCountIsCappedAndTheRestBecomesUnmet() {
		CollectionRoute.Route route = CollectionRoute.plan(
			List.of(new CollectionRoute.Need("fer", "Fer", 10)),
			List.of(source(key(1, 0), "A", "fer", 1), source(key(2, 0), "B", "fer", 1),
				source(key(3, 0), "C", "fer", 1), source(key(4, 0), "D", "fer", 1)),
			OVERWORLD, 0, 64, 0, 2);

		assertEquals(2, route.size());
		assertEquals(8, route.unmet().get(0).quantity());
	}

	@Test
	void aContainerIsNeverVisitedTwice() {
		ContainerKey chest = key(5, 0);
		CollectionRoute.Route route = CollectionRoute.plan(
			List.of(new CollectionRoute.Need("fer", "Fer", 100)),
			List.of(source(chest, "A", "fer", 10)),
			OVERWORLD, 0, 64, 0, 8);

		assertEquals(1, route.size());
		assertEquals(10, route.stops().get(0).totalItems());
		assertEquals(90, route.unmet().get(0).quantity());
	}

	@Test
	void needsWithNothingToDoProduceAnEmptyRoute() {
		CollectionRoute.Route route = CollectionRoute.plan(
			List.of(new CollectionRoute.Need("fer", "Fer", 0)),
			List.of(source(key(5, 0), "A", "fer", 64)),
			OVERWORLD, 0, 64, 0, 8);

		assertTrue(route.isEmpty());
		assertTrue(route.unmet().isEmpty());
	}
}
