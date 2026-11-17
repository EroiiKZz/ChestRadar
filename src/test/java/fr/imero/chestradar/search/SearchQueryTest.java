package fr.imero.chestradar.search;

import fr.imero.chestradar.data.ContainerKey;
import fr.imero.chestradar.data.ContainerKind;
import fr.imero.chestradar.data.ContainerRecord;
import fr.imero.chestradar.data.Dimensions;
import fr.imero.chestradar.data.ItemIdentities;
import fr.imero.chestradar.data.ItemIdentity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SearchQueryTest {
	private static final long NOW = 1_700_000_000_000L;
	private static final SearchQuery.Context CTX = new SearchQuery.Context(true, NOW);
	private static final SearchQuery.Context NO_LORE = new SearchQuery.Context(false, NOW);

	private static AggregatedItem item(ItemIdentity identity, int total, List<Hit> hits) {
		return new AggregatedItem(identity, total, hits);
	}

	private static Hit hit(String dimension, ContainerKind kind, String title, double distance, long ageDays) {
		ContainerRecord record = new ContainerRecord(new ContainerKey(dimension, 0, 64, 0), null,
			kind, title, NOW - ageDays * Freshness.DAY_MS, 27, Map.of(), Map.of());
		return new Hit(record, distance, 1, 0);
	}

	private static AggregatedItem sword() {
		ItemIdentity identity = ItemIdentities.of("minecraft:diamond_sword", "{}", "Épée du Vide",
			"Vendue par le marchand", List.of("minecraft:sharpness", "serverpack:radioactivity"));
		return item(identity, 3, List.of(
			hit(Dimensions.OVERWORLD, ContainerKind.CHEST, "Base principale", 12.0, 1)));
	}

	private static AggregatedItem stone() {
		ItemIdentity identity = ItemIdentities.of("minecraft:stone", "{}", "Pierre", "", List.of());
		return item(identity, 4096, List.of(
			hit(Dimensions.NETHER, ContainerKind.SHULKER_BOX, "Réserve", 400.0, 40)));
	}

	@Test
	void freeTextTermsAreAndedTogether() {
		assertTrue(SearchQuery.parse("épée vide").matches(sword(), CTX));
		assertFalse(SearchQuery.parse("épée pioche").matches(sword(), CTX));
	}

	@Test
	void aLeadingDashExcludes() {
		assertFalse(SearchQuery.parse("-épée").matches(sword(), CTX));
		assertTrue(SearchQuery.parse("-pioche").matches(sword(), CTX));
		assertTrue(SearchQuery.parse("épée -pioche").matches(sword(), CTX));
	}

	@Test
	void quotesKeepAPhraseTogether() {
		assertTrue(SearchQuery.parse("\"épée du vide\"").matches(sword(), CTX));
		assertFalse(SearchQuery.parse("\"vide du épée\"").matches(sword(), CTX));
	}

	@Test
	void anUnclosedQuoteClosesAtTheEndOfTheInput() {
		assertTrue(SearchQuery.parse("\"épée du vide").matches(sword(), CTX));
	}

	@Test
	void loreIsSearchedByFreeTextOnlyWhenTheOptionIsOn() {
		assertTrue(SearchQuery.parse("marchand").matches(sword(), CTX));
		assertFalse(SearchQuery.parse("marchand").matches(sword(), NO_LORE));
		assertTrue(SearchQuery.parse("lore:marchand").matches(sword(), NO_LORE));
	}

	@Test
	void enchantmentPrefixMatchesTheIdentifier() {
		assertTrue(SearchQuery.parse("ench:sharpness").matches(sword(), CTX));
		assertTrue(SearchQuery.parse("ench:radioactiv").matches(sword(), CTX));
		assertFalse(SearchQuery.parse("ench:knockback").matches(sword(), CTX));
		assertFalse(SearchQuery.parse("ench:sharpness").matches(stone(), CTX));
	}

	@Test
	void idPrefixMatchesTheItemIdentifier() {
		assertTrue(SearchQuery.parse("id:diamond_sword").matches(sword(), CTX));
		assertFalse(SearchQuery.parse("id:stone").matches(sword(), CTX));
	}

	@Test
	void containerPrefixesQuantifyOverHits() {
		assertTrue(SearchQuery.parse("in:base").matches(sword(), CTX));
		assertTrue(SearchQuery.parse("kind:coffre").matches(sword(), CTX));
		assertTrue(SearchQuery.parse("kind:chest").matches(sword(), CTX));
		assertTrue(SearchQuery.parse("dim:overworld").matches(sword(), CTX));
		assertTrue(SearchQuery.parse("kind:shulker").matches(stone(), CTX));
		assertTrue(SearchQuery.parse("dim:nether").matches(stone(), CTX));
		assertFalse(SearchQuery.parse("dim:nether").matches(sword(), CTX));
	}

	@Test
	void negatingAContainerPrefixMeansNoHitMatches() {
		assertTrue(SearchQuery.parse("-kind:shulker").matches(sword(), CTX));
		assertFalse(SearchQuery.parse("-kind:shulker").matches(stone(), CTX));
	}

	@Test
	void quantityComparisonsUseTheTotal() {
		assertTrue(SearchQuery.parse(">64").matches(stone(), CTX));
		assertFalse(SearchQuery.parse(">64").matches(sword(), CTX));
		assertTrue(SearchQuery.parse("<10").matches(sword(), CTX));
		assertFalse(SearchQuery.parse("<10").matches(stone(), CTX));
		assertTrue(SearchQuery.parse(">1 <10").matches(sword(), CTX));
	}

	@Test
	void distanceAndAgeFilterOnContainers() {
		assertTrue(SearchQuery.parse("m:32").matches(sword(), CTX));
		assertFalse(SearchQuery.parse("m:32").matches(stone(), CTX));
		assertTrue(SearchQuery.parse("d:7").matches(sword(), CTX));
		assertFalse(SearchQuery.parse("d:7").matches(stone(), CTX));
	}

	@Test
	void aCommaIsAcceptedAsADecimalSeparator() {
		assertTrue(SearchQuery.parse("m:12,5").matches(sword(), CTX));
		assertFalse(SearchQuery.parse("m:11,5").matches(sword(), CTX));
	}

	@Test
	void anUnknownPrefixStaysFreeText() {
		assertTrue(SearchQuery.parse("minecraft:diamond_sword").matches(sword(), CTX));
		assertFalse(SearchQuery.parse("minecraft:stone").matches(sword(), CTX));
	}

	@Test
	void anOperatorWithoutAValueIsIgnoredRatherThanMatchingNothing() {
		assertTrue(SearchQuery.parse("ench:").isEmpty());
		assertTrue(SearchQuery.parse("ench:").matches(sword(), CTX));
		assertTrue(SearchQuery.parse("épée ench:").matches(sword(), CTX));
		assertTrue(SearchQuery.parse(">").isEmpty());
		assertTrue(SearchQuery.parse("m:abc").isEmpty());
	}

	@Test
	void aLoneDashIsLiteralTextAndNotAnEmptyNegation() {
		SearchQuery dash = SearchQuery.parse("-");
		assertFalse(dash.isEmpty());
		assertFalse(dash.matches(sword(), CTX));

		ItemIdentity hyphen = ItemIdentities.of("minecraft:paper", "{}", "Bon d'achat -50%", "", List.of());
		assertTrue(dash.matches(item(hyphen, 1, List.of()), CTX));
	}

	@Test
	void emptyAndBlankQueriesMatchEverything() {
		assertTrue(SearchQuery.parse("").isEmpty());
		assertTrue(SearchQuery.parse("   ").isEmpty());
		assertTrue(SearchQuery.parse(null).isEmpty());
		assertTrue(SearchQuery.parse("").matches(stone(), CTX));
	}

	@Test
	void plainModeTreatsOperatorsAsLiteralText() {
		ItemIdentity identity = ItemIdentities.of("minecraft:paper", "{}", "Ticket id:42", "", List.of());
		AggregatedItem ticket = item(identity, 1, List.of());
		assertTrue(SearchQuery.plain("id:42").matches(ticket, CTX));
		assertFalse(SearchQuery.parse("id:42").matches(ticket, CTX));
	}

	@Test
	void plainModeDoesNotSplitOnSpaces() {
		assertTrue(SearchQuery.plain("épée du vide").matches(sword(), CTX));
		assertFalse(SearchQuery.plain("épée vide").matches(sword(), CTX));
	}
}
