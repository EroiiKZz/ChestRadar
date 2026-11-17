package fr.imero.chestradar.search;

import fr.imero.chestradar.data.ContainerIndex;
import fr.imero.chestradar.data.ContainerKey;
import fr.imero.chestradar.data.ContainerKind;
import fr.imero.chestradar.data.ContainerRecord;
import fr.imero.chestradar.data.ItemIdentities;
import fr.imero.chestradar.data.ItemIdentity;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class StorageCellTest {
	private static final Pattern TITLE = Pattern.compile(BulkQuantity.DEFAULT_TITLE_PATTERN);
	private static final Pattern LORE = Pattern.compile(BulkQuantity.DEFAULT_PATTERN);

	private static final ItemIdentity DIAMOND =
		ItemIdentities.of("minecraft:diamond", "{}", "Diamond", "", List.of());

	private static ItemIdentity cellItem(String componentsJson, String lore) {
		return ItemIdentities.of("minecraft:diamond", componentsJson, "Diamant", lore, List.of());
	}

	private static ContainerRecord chest(int x, Map<String, Integer> counts) {
		return new ContainerRecord(new ContainerKey("minecraft:overworld", x, 64, 0), null,
			ContainerKind.CHEST, "Chest", 1L, 27, counts, Map.of());
	}

	private static ContainerRecord cell(int x, String title, Map<String, Integer> counts) {
		return new ContainerRecord(new ContainerKey("minecraft:overworld", x, 64, 0), null,
			ContainerKind.BARREL, title, 1L, 5, counts, Map.of());
	}

	private static ContainerIndex indexOf(ContainerRecord... records) {
		ContainerIndex index = new ContainerIndex();
		for (ContainerRecord record : records) {
			index.upsert(record);
		}
		return index;
	}

	private static Map<String, Integer> counts(Object... pairs) {
		Map<String, Integer> map = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put((String) pairs[i], (Integer) pairs[i + 1]);
		}
		return map;
	}

	@Test
	void theTitleQuantityReplacesTheDecorativeSlotCount() {
		int quantity = BulkQuantity.parse(java.util.List.of("Cellule de Stockage 39974"), TITLE);
		assertEquals(39974, quantity);
		assertEquals(counts("xp_bottle", 39974),
			StorageCell.collapse(counts("xp_bottle", 4), quantity));
	}

	@Test
	void aContainerMixingTwoItemsIsNeverACell() {
		Map<String, Integer> mixed = counts("stone", 64, "dirt", 12);
		assertEquals(mixed, StorageCell.collapse(mixed, 39974));
	}

	@Test
	void noNumberInTheTitleLeavesTheCountsAlone() {
		assertEquals(0, BulkQuantity.parse(java.util.List.of("Large Chest"), TITLE));
		assertEquals(counts("stone", 64), StorageCell.collapse(counts("stone", 64), 0));
	}

	@Test
	void substitutionCanOnlyRaiseATotalNeverLowerIt() {
		assertEquals(counts("stone", 64), StorageCell.collapse(counts("stone", 64), 3));
		assertEquals(counts("stone", 64), StorageCell.collapse(counts("stone", 64), 64));
	}

	@Test
	void emptyCountsStayEmpty() {
		assertTrue(StorageCell.collapse(counts(), 39974).isEmpty());
	}

	@Test
	void titlePatternReadsTheTrailingNumberOnly() {
		assertEquals(39974, BulkQuantity.parse(java.util.List.of("Cellule de Stockage 39974"), TITLE));
		assertEquals(39974, BulkQuantity.parse(java.util.List.of("Cellule de Stockage 39 974"), TITLE));
		assertEquals(0, BulkQuantity.parse(java.util.List.of("Cellule 12 de imero"), TITLE));
	}

	@Test
	void serverChromeIsStrippedButFunctionalComponentsStay() {
		assertEquals("{}", StorageCell.plainComponents(
			"{\"minecraft:custom_name\":\"'Diamant'\",\"minecraft:lore\":[\"'Quantite: 39974'\"]}"));
		assertEquals("{\"minecraft:damage\":3}", StorageCell.plainComponents(
			"{\"minecraft:damage\":3,\"minecraft:custom_model_data\":{}}"));
	}

	@Test
	void aCellEntryFoldsIntoTheSameItemStoredInAChest() {
		ItemIdentity decorated = cellItem(
			"{\"minecraft:custom_name\":\"'Diamant'\",\"minecraft:lore\":[\"'Quantite: 39974'\"]}",
			"Quantite: 39974");
		ContainerIndex index = indexOf(chest(0, counts(DIAMOND.fingerprint(), 64)),
			cell(9, "Cellule de Stockage", counts(decorated.fingerprint(), 39974)));
		index.pool().intern(DIAMOND);
		index.pool().intern(decorated);

		assertEquals(1, StorageCell.merge(index, LORE, TITLE, Set.of()));
		assertNull(index.pool().get(decorated.fingerprint()));
		assertEquals(counts(DIAMOND.fingerprint(), 39974),
			index.at(new ContainerKey("minecraft:overworld", 9, 64, 0)).counts());
	}

	@Test
	void aCellNamedByItsTitleFoldsEvenWithoutBulkLore() {
		ItemIdentity decorated = cellItem("{\"minecraft:custom_name\":\"'Diamant'\"}", "");
		ContainerIndex index = indexOf(chest(0, counts(DIAMOND.fingerprint(), 64)),
			cell(9, "Cellule de Stockage 39974", counts(decorated.fingerprint(), 39974)));
		index.pool().intern(DIAMOND);
		index.pool().intern(decorated);

		assertEquals(1, StorageCell.merge(index, LORE, TITLE, Set.of()));
		assertEquals(counts(DIAMOND.fingerprint(), 39974),
			index.at(new ContainerKey("minecraft:overworld", 9, 64, 0)).counts());
	}

	@Test
	void anItemKnownOnlyFromCellsKeepsItsOwnEntry() {
		ItemIdentity decorated = cellItem("{\"minecraft:custom_name\":\"'Diamant'\"}", "");
		ContainerIndex index = indexOf(cell(9, "Cellule de Stockage 39974",
			counts(decorated.fingerprint(), 39974)));
		index.pool().intern(decorated);

		assertEquals(0, StorageCell.merge(index, LORE, TITLE, Set.of()));
		assertNotNull(index.pool().get(decorated.fingerprint()));
	}

	@Test
	void chestOnlyItemsAreNeverTouched() {
		ItemIdentity named = ItemIdentities.of("minecraft:diamond_sword",
			"{\"minecraft:custom_name\":\"'Excalibur'\"}", "Excalibur", "", List.of());
		ContainerIndex index = indexOf(chest(0, counts(named.fingerprint(), 1)));
		index.pool().intern(named);

		assertEquals(0, StorageCell.merge(index, LORE, TITLE, Set.of()));
		assertNotNull(index.pool().get(named.fingerprint()));
	}
}
