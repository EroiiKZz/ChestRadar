package fr.imero.chestradar.search;

import fr.imero.chestradar.config.ChestRadarConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class BulkQuantityTest {
	private static final Pattern DEFAULT = Pattern.compile(BulkQuantity.DEFAULT_PATTERN);

	@Test
	void readsThePlainFrenchAndEnglishForms() {
		assertEquals(39974, BulkQuantity.parse(List.of("Quantité: 39974"), DEFAULT));
		assertEquals(39974, BulkQuantity.parse(List.of("Quantite : 39974"), DEFAULT));
		assertEquals(39974, BulkQuantity.parse(List.of("Quantity: 39974"), DEFAULT));
		assertEquals(39974, BulkQuantity.parse(List.of("Stock = 39974"), DEFAULT));
		assertEquals(39974, BulkQuantity.parse(List.of("Total 39974"), DEFAULT));
	}

	@Test
	void readsGroupedThousandsWithoutTruncating() {
		assertEquals(39974, BulkQuantity.parse(List.of("Quantité: 39 974"), DEFAULT));
		assertEquals(39974, BulkQuantity.parse(List.of("Quantité: 39,974"), DEFAULT));
		assertEquals(39974, BulkQuantity.parse(List.of("Quantité: 39.974"), DEFAULT));
		assertEquals(1_000_000, BulkQuantity.parse(List.of("Quantité: 1 000 000"), DEFAULT));
	}

	@Test
	void stopsBeforeASecondNumberOnTheSameLine() {
		assertEquals(39974, BulkQuantity.parse(List.of("Quantité: 39974 / 100000"), DEFAULT));
		assertEquals(39974, BulkQuantity.parse(List.of("Quantité: 39974/100000"), DEFAULT));
	}

	@Test
	void toleratesLegacyColourCodesBetweenKeywordAndNumber() {
		assertEquals(39974, BulkQuantity.parse(List.of("§7Quantité: §e39974"), DEFAULT));
	}

	@Test
	void scansEveryLineAndTakesTheFirstMatch() {
		assertEquals(39974, BulkQuantity.parse(
			List.of("Cellule de Stockage", "Propriétaire: imero", "Quantité: 39974"), DEFAULT));
	}

	@Test
	void returnsZeroWhenNothingLooksLikeAQuantity() {
		assertEquals(0, BulkQuantity.parse(List.of("Épée légendaire", "Dégâts: 12"), DEFAULT));
		assertEquals(0, BulkQuantity.parse(List.of(), DEFAULT));
		assertEquals(0, BulkQuantity.parse(List.of("Quantité: zéro"), DEFAULT));
	}

	@Test
	void nullPatternMeansTheFeatureIsOff() {
		assertEquals(0, BulkQuantity.parse(List.of("Quantité: 39974"), null));
	}

	@Test
	void saturatesInsteadOfOverflowingToNegative() {
		assertEquals(Integer.MAX_VALUE,
			BulkQuantity.parse(List.of("Quantité: 99999999999999999999999"), DEFAULT));
	}

	@Test
	void patternForReturnsNullWhenDisabledAndCompilesWhenEnabled() {
		ChestRadarConfig c = new ChestRadarConfig();
		assertTrue(c.indexBulkQuantity, "actif par defaut : c'est la raison d'etre du reglage");
		assertNotNull(BulkQuantity.patternFor(c));

		c.indexBulkQuantity = false;
		assertNull(BulkQuantity.patternFor(c));
	}

	@Test
	void anInvalidCustomPatternFallsBackToTheDefaultRatherThanDisablingTheFeature() {
		ChestRadarConfig c = new ChestRadarConfig();
		c.bulkQuantityLorePattern = "([unclosed";
		Pattern p = BulkQuantity.patternFor(c);
		assertNotNull(p);
		assertEquals(39974, BulkQuantity.parse(List.of("Quantité: 39974"), p));
	}

	@Test
	void aCustomPatternIsHonouredAndReplacesTheDefault() {
		ChestRadarConfig c = new ChestRadarConfig();
		c.bulkQuantityLorePattern = "^>> (\\d+) unites$";
		Pattern p = BulkQuantity.patternFor(c);
		assertEquals(512, BulkQuantity.parse(List.of(">> 512 unites"), p));
		assertEquals(0, BulkQuantity.parse(List.of("Quantité: 39974"), p),
			"le motif personnalise remplace le defaut, il ne s'y ajoute pas");
	}
}
