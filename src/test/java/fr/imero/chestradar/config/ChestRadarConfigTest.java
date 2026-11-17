package fr.imero.chestradar.config;

import fr.imero.chestradar.search.BulkQuantity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ChestRadarConfigTest {
	@Test
	void defaultsMatchSpec() {
		ChestRadarConfig c = new ChestRadarConfig();
		assertEquals(128.0, c.searchRadius);
		assertTrue(c.indexBarrels);
		assertTrue(c.indexShulkerBoxes);
		assertTrue(c.highlightEnabled);
		assertEquals(12, c.highlightSeconds);
		assertEquals(5000, c.maxIndexedContainers);
		assertEquals(SortMode.NAME, c.sortMode);
		assertEquals(8, c.chatResultLimit);
		assertTrue(c.searchIncludesLore);
		assertTrue(c.autoRefreshIndex);
		assertTrue(c.jadeIntegration);
	}

	@Test
	void upkeepAndJadeSwitchesSurviveARoundTrip(@TempDir Path dir) {
		Path f = dir.resolve("chestradar.json");
		ChestRadarConfig c = new ChestRadarConfig();
		c.autoRefreshIndex = false;
		c.jadeIntegration = false;
		c.save(f);
		ChestRadarConfig r = ChestRadarConfig.load(f);
		assertFalse(r.autoRefreshIndex, "l'interrupteur maitre doit tenir jusqu'a la prochaine session");
		assertFalse(r.jadeIntegration);
	}

	@Test
	void anIndexFileWrittenBeforeTheseSwitchesExistedKeepsTheOldBehaviour(@TempDir Path dir)
			throws Exception {
		Path f = dir.resolve("chestradar.json");
		Files.writeString(f, "{\"staleAfterDays\":30}");
		ChestRadarConfig c = ChestRadarConfig.load(f);
		assertEquals(30, c.staleAfterDays);
		assertTrue(c.autoRefreshIndex);
		assertTrue(c.jadeIntegration);
	}

	@Test
	void clampPullsOutOfRangeValuesBackIntoBounds() {
		ChestRadarConfig c = new ChestRadarConfig();
		c.searchRadius = 99999;
		c.highlightSeconds = -5;
		c.maxIndexedContainers = 1;
		c.chatResultLimit = 900;
		c.clamp();
		assertEquals(1024.0, c.searchRadius);
		assertEquals(0, c.highlightSeconds);
		assertEquals(100, c.maxIndexedContainers);
		assertEquals(50, c.chatResultLimit);
	}

	@Test
	void missingFieldFallsBackToDefaultWithoutLosingOthers(@TempDir Path dir) throws Exception {
		Path f = dir.resolve("chestradar.json");
		Files.writeString(f, "{\"searchRadius\":42.0}");
		ChestRadarConfig c = ChestRadarConfig.load(f);
		assertEquals(42.0, c.searchRadius);
		assertEquals(8, c.chatResultLimit, "un champ absent doit garder son défaut");
		assertEquals(SortMode.NAME, c.sortMode);
	}

	@Test
	void unreadableFileYieldsDefaultsInsteadOfThrowing(@TempDir Path dir) throws Exception {
		Path f = dir.resolve("chestradar.json");
		Files.writeString(f, "{ this is not json");
		ChestRadarConfig c = ChestRadarConfig.load(f);
		assertEquals(128.0, c.searchRadius);
	}

	@Test
	void saveThenLoadRoundTrips(@TempDir Path dir) {
		Path f = dir.resolve("chestradar.json");
		ChestRadarConfig c = new ChestRadarConfig();
		c.searchRadius = 64.0;
		c.sortMode = SortMode.DISTANCE;
		c.searchIncludesLore = false;
		c.save(f);
		ChestRadarConfig r = ChestRadarConfig.load(f);
		assertEquals(64.0, r.searchRadius);
		assertEquals(SortMode.DISTANCE, r.sortMode);
		assertFalse(r.searchIncludesLore);
	}

	@Test
	void sortModeCyclesThroughAllThreeAndWrapsAround() {
		assertEquals(SortMode.COUNT, SortMode.NAME.next());
		assertEquals(SortMode.DISTANCE, SortMode.COUNT.next());
		assertEquals(SortMode.NAME, SortMode.DISTANCE.next());
	}

	@Test
	void ignoredComponentKeysDefaultsToEmpty() {
		ChestRadarConfig c = new ChestRadarConfig();
		assertTrue(c.ignoredComponentKeys.isEmpty());
	}

	@Test
	void nullIgnoredComponentKeysSurvivesClampAsEmptyList() {
		ChestRadarConfig c = new ChestRadarConfig();
		c.ignoredComponentKeys = null;
		c.clamp();
		assertNotNull(c.ignoredComponentKeys);
		assertTrue(c.ignoredComponentKeys.isEmpty());
	}

	@Test
	void viewModeAndNestedContentsDefaults() {
		ChestRadarConfig c = new ChestRadarConfig();
		assertEquals(ViewMode.LIST, c.viewMode);
		assertTrue(c.indexNestedContents);
	}

	@Test
	void sortModeSurvivesSaveThenLoad(@TempDir Path dir) {
		Path f = dir.resolve("chestradar.json");
		ChestRadarConfig c = new ChestRadarConfig();
		c.sortMode = SortMode.DISTANCE;
		c.save(f);
		assertEquals(SortMode.DISTANCE, ChestRadarConfig.load(f).sortMode);
	}

	@Test
	void legacyDefaultSortKeyStillLoadsIntoSortMode(@TempDir Path dir) throws Exception {
		Path f = dir.resolve("chestradar.json");
		Files.writeString(f, "{\"defaultSort\":\"DISTANCE\"}");
		assertEquals(SortMode.DISTANCE, ChestRadarConfig.load(f).sortMode);
	}

	@Test
	void faceContainerOnLocateDefaultsOnAndSurvivesSaveThenLoad(@TempDir Path dir) {
		assertTrue(new ChestRadarConfig().faceContainerOnLocate);
		Path f = dir.resolve("chestradar.json");
		ChestRadarConfig c = new ChestRadarConfig();
		c.faceContainerOnLocate = false;
		c.save(f);
		assertFalse(ChestRadarConfig.load(f).faceContainerOnLocate);
	}

	@Test
	void hopperGuiAndBulkQuantityDefaultsOnAndRoundTrip(@TempDir Path dir) {
		ChestRadarConfig defaults = new ChestRadarConfig();
		assertTrue(defaults.indexHopperGuiContainers);
		assertTrue(defaults.indexBulkQuantity);
		assertEquals(BulkQuantity.DEFAULT_PATTERN, defaults.bulkQuantityLorePattern);

		Path f = dir.resolve("chestradar.json");
		ChestRadarConfig c = new ChestRadarConfig();
		c.indexHopperGuiContainers = false;
		c.bulkQuantityLorePattern = "^>> (\\d+)$";
		c.save(f);
		ChestRadarConfig r = ChestRadarConfig.load(f);
		assertFalse(r.indexHopperGuiContainers);
		assertEquals("^>> (\\d+)$", r.bulkQuantityLorePattern);
	}

	@Test
	void legacyBulkQuantityFlagStillLoads(@TempDir Path dir) throws Exception {
		Path f = dir.resolve("chestradar.json");
		Files.writeString(f, "{\"indexBulkQuantityFromLore\":false}");
		assertFalse(ChestRadarConfig.load(f).indexBulkQuantity);
	}

	@Test
	void blankBulkPatternFallsBackToTheDefaultInsteadOfMatchingEverything(@TempDir Path dir) throws Exception {
		Path f = dir.resolve("chestradar.json");
		Files.writeString(f, "{\"bulkQuantityLorePattern\":\"  \"}");
		assertEquals(BulkQuantity.DEFAULT_PATTERN, ChestRadarConfig.load(f).bulkQuantityLorePattern);
	}

	@Test
	void nullSortModeSurvivesClampAsName() {
		ChestRadarConfig c = new ChestRadarConfig();
		c.sortMode = null;
		c.clamp();
		assertEquals(SortMode.NAME, c.sortMode);
	}

	@Test
	void nullViewModeSurvivesClampAsList() {
		ChestRadarConfig c = new ChestRadarConfig();
		c.viewMode = null;
		c.clamp();
		assertEquals(ViewMode.LIST, c.viewMode);
	}

	@Test
	void panelWidthFractionDefaultsToThePreviousHardcodedValue() {
		assertEquals(0.85, new ChestRadarConfig().panelWidthFraction);
	}

	@Test
	void panelWidthFractionIsClampedToItsBounds() {
		ChestRadarConfig c = new ChestRadarConfig();
		c.panelWidthFraction = 5.0;
		c.clamp();
		assertEquals(ChestRadarConfig.PANEL_WIDTH_FRACTION_MAX, c.panelWidthFraction);
		c.panelWidthFraction = 0.01;
		c.clamp();
		assertEquals(ChestRadarConfig.PANEL_WIDTH_FRACTION_MIN, c.panelWidthFraction);
	}

	@Test
	void nanPanelWidthFractionFallsBackToDefaultInsteadOfSurvivingClamp() {
		ChestRadarConfig c = new ChestRadarConfig();
		c.panelWidthFraction = Double.NaN;
		c.clamp();
		assertEquals(0.85, c.panelWidthFraction);
	}

	@Test
	void slotHighlightDefaultsOnWithTenSecondsAndClampsToBounds() {
		ChestRadarConfig c = new ChestRadarConfig();
		assertTrue(c.slotHighlightEnabled);
		assertEquals(10, c.slotHighlightSeconds);
		c.slotHighlightSeconds = 0;
		c.clamp();
		assertEquals(1, c.slotHighlightSeconds);
		c.slotHighlightSeconds = 500;
		c.clamp();
		assertEquals(60, c.slotHighlightSeconds);
	}

	@Test
	void slotHighlightSurvivesSaveThenLoad(@TempDir Path dir) {
		Path f = dir.resolve("chestradar.json");
		ChestRadarConfig c = new ChestRadarConfig();
		c.slotHighlightEnabled = false;
		c.slotHighlightSeconds = 25;
		c.save(f);
		ChestRadarConfig r = ChestRadarConfig.load(f);
		assertFalse(r.slotHighlightEnabled);
		assertEquals(25, r.slotHighlightSeconds);
	}

	@Test
	void viewModeCyclesBetweenListAndGrid() {
		assertEquals(ViewMode.GRID, ViewMode.LIST.next());
		assertEquals(ViewMode.LIST, ViewMode.GRID.next());
	}
}
