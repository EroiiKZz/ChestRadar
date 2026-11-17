package fr.imero.chestradar.data;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ItemIdentitiesTest {
	private static ItemIdentity id(String itemId, String components) {
		return ItemIdentities.of(itemId, components, "x", "", List.of());
	}

	@Test
	void identicalStacksProduceIdenticalFingerprints() {
		assertEquals(
			id("minecraft:stone", "{\"minecraft:custom_name\":\"'A'\"}").fingerprint(),
			id("minecraft:stone", "{\"minecraft:custom_name\":\"'A'\"}").fingerprint());
	}

	@Test
	void keyOrderDoesNotAffectFingerprint() {
		String a = "{\"minecraft:custom_name\":\"'A'\",\"minecraft:lore\":[\"'L'\"]}";
		String b = "{\"minecraft:lore\":[\"'L'\"],\"minecraft:custom_name\":\"'A'\"}";
		assertEquals(id("minecraft:paper", a).fingerprint(), id("minecraft:paper", b).fingerprint());
	}

	@Test
	void nestedObjectKeyOrderDoesNotAffectFingerprint() {
		String a = "{\"minecraft:custom_data\":{\"a\":1,\"b\":2}}";
		String b = "{\"minecraft:custom_data\":{\"b\":2,\"a\":1}}";
		assertEquals(id("minecraft:stone", a).fingerprint(), id("minecraft:stone", b).fingerprint());
	}

	@Test
	void differentArrayOrderProducesDifferentFingerprint() {
		String a = "{\"minecraft:lore\":[\"'L1'\",\"'L2'\"]}";
		String b = "{\"minecraft:lore\":[\"'L2'\",\"'L1'\"]}";
		assertNotEquals(id("minecraft:paper", a).fingerprint(), id("minecraft:paper", b).fingerprint(),
			"l'ordre du lore est signifiant : le trier fusionnerait des items différents");
	}

	@Test
	void differentCustomNamesProduceDifferentFingerprints() {
		assertNotEquals(
			id("minecraft:enchanted_book", "{\"minecraft:custom_name\":\"'Radioactif IV'\"}").fingerprint(),
			id("minecraft:enchanted_book", "{\"minecraft:custom_name\":\"'Radioactif III'\"}").fingerprint());
	}

	@Test
	void customItemDiffersFromPlainItemOfSameType() {
		assertNotEquals(
			id("minecraft:enchanted_book", "{\"minecraft:custom_name\":\"'Radioactif IV'\"}").fingerprint(),
			id("minecraft:enchanted_book", "{}").fingerprint());
	}

	@Test
	void damageAndRepairCostAreExcludedFromFingerprint() {
		String worn = "{\"minecraft:damage\":120,\"minecraft:repair_cost\":7,\"minecraft:custom_name\":\"'Pioche'\"}";
		String fresh = "{\"minecraft:custom_name\":\"'Pioche'\"}";
		assertEquals(id("minecraft:diamond_pickaxe", worn).fingerprint(),
			id("minecraft:diamond_pickaxe", fresh).fingerprint(),
			"l'usure ne doit pas créer une ligne par point de durabilité (spec § 11.7)");
	}

	@Test
	void unknownServerComponentStillAffectsFingerprint() {
		assertNotEquals(
			id("minecraft:stick", "{\"someplugin:item_tier\":\"legendary\"}").fingerprint(),
			id("minecraft:stick", "{}").fingerprint());
	}

	@Test
	void volatileKeysAreStrippedAtAnyDepthNotJustRoot() {
		String worn = "{\"minecraft:container\":[{\"item\":{\"components\":{\"minecraft:damage\":120}}}]}";
		String fresh = "{\"minecraft:container\":[{\"item\":{\"components\":{}}}]}";
		assertEquals(id("minecraft:shulker_box", worn).fingerprint(), id("minecraft:shulker_box", fresh).fingerprint());
	}

	@Test
	void extraVolatileKeysAreExcludedWhenConfigured() {
		String a = "{\"someplugin:uuid\":\"AAA\"}";
		String b = "{\"someplugin:uuid\":\"BBB\"}";
		assertEquals(
			ItemIdentities.fingerprint("minecraft:stick", a, Set.of("someplugin:uuid")),
			ItemIdentities.fingerprint("minecraft:stick", b, Set.of("someplugin:uuid")),
			"avec la clé en extension, deux exemplaires du même item custom doivent fusionner");
		assertNotEquals(
			ItemIdentities.fingerprint("minecraft:stick", a),
			ItemIdentities.fingerprint("minecraft:stick", b),
			"sans configuration, le comportement par défaut (liste noire des deux clés intégrées) est inchangé");
	}

	@Test
	void captureAndSearchSidesAgreeWhenExtraVolatileKeysAreConfigured() {
		Set<String> configured = Set.of("someplugin:uuid");
		String stored = "{\"minecraft:custom_name\":\"'Epee du Vide'\",\"someplugin:uuid\":\"AAA\"}";
		String held = "{\"minecraft:custom_name\":\"'Epee du Vide'\",\"someplugin:uuid\":\"BBB\"}";

		String captureSide = ItemIdentities.of("minecraft:iron_sword", stored, "Epee du Vide", "",
			List.of(), configured).fingerprint();
		String searchSide = ItemIdentities.of("minecraft:iron_sword", held, "Epee du Vide", "",
			List.of(), configured).fingerprint();
		assertEquals(captureSide, searchSide,
			"capture et recherche doivent passer les MEMES cles ignorees, sinon R ne retrouve jamais l'item indexe");

		String searchSideWithoutConfiguredKeys = ItemIdentities.of("minecraft:iron_sword", held,
			"Epee du Vide", "", List.of()).fingerprint();
		assertNotEquals(captureSide, searchSideWithoutConfiguredKeys,
			"c'est la divergence exacte que la correction empeche : ne pas transmettre les cles rend l'item introuvable");
	}

	@Test
	void differentItemIdsProduceDifferentFingerprints() {
		assertNotEquals(id("minecraft:stone", "{}").fingerprint(), id("minecraft:dirt", "{}").fingerprint());
	}

	@Test
	void enchantmentIdsOutsideVanillaArePreserved() {
		ItemIdentity identity = ItemIdentities.of(
			"minecraft:golden_hoe", "{}", "Faux", "",
			List.of("minecraft:knockback", "serverpack:radioactivity"));
		assertEquals(List.of("minecraft:knockback", "serverpack:radioactivity"), identity.enchantmentIds());
	}

	@Test
	void searchTextIsLowercasedAndStrippedOfFormattingCodes() {
		assertEquals("radioactif iv", ItemIdentities.normalizeSearch("§c§lRadioactif IV"));
	}

	@Test
	void searchTextStripsBungeeHexColourPrefix() {
		assertEquals("nom", ItemIdentities.normalizeSearch("§x§f§f§0§0§0§0Nom"));
	}

	@Test
	void searchTextLowercasingIsLocaleIndependent() {
		java.util.Locale original = java.util.Locale.getDefault();
		try {
			java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr"));
			assertEquals("radioactif iv", ItemIdentities.normalizeSearch("Radioactif IV"));
		} finally {
			java.util.Locale.setDefault(original);
		}
	}

	@Test
	void searchTextContainsNameAndEnchantmentIds() {
		ItemIdentity identity = ItemIdentities.of(
			"minecraft:golden_hoe", "{}", "§eFaux du Vide", "§7Inflige un recul",
			List.of("minecraft:knockback"));
		assertTrue(identity.searchText().contains("faux du vide"));
		assertTrue(identity.searchText().contains("knockback"));
		assertEquals("inflige un recul", identity.loreText());
	}

	@Test
	void malformedComponentJsonFallsBackToRawStringWithoutThrowing() {
		ItemIdentity identity = id("minecraft:stone", "{not json");
		assertNotNull(identity.fingerprint());
		assertEquals(16, identity.fingerprint().length());
	}

	@Test
	void fingerprintIsSixteenHexCharacters() {
		assertTrue(id("minecraft:stone", "{}").fingerprint().matches("[0-9a-f]{16}"));
	}
}
