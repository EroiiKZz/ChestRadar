package fr.imero.chestradar.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IndexStorageTest {
	private static ContainerIndex sampleIndex() {
		ContainerIndex index = new ContainerIndex();
		ItemIdentity book = ItemIdentities.of("minecraft:enchanted_book",
			"{\"minecraft:custom_name\":\"'Radioactif IV'\"}", "Radioactif IV",
			"Inflige des degats", List.of("serverpack:radioactivity"));
		String fingerprint = index.pool().intern(book);
		index.upsert(new ContainerRecord(new ContainerKey("minecraft:overworld", 10, 64, 20),
			new ContainerKey("minecraft:overworld", 11, 64, 20), ContainerKind.CHEST,
			"Large Chest", 1234L, 54, Map.of(fingerprint, 7), Map.of()));
		return index;
	}

	@Test
	void roundTripPreservesContainersAndIdentities(@TempDir Path dir) {
		Path file = dir.resolve("world.json");
		IndexStorage.save(file, sampleIndex());
		ContainerIndex loaded = IndexStorage.load(file);

		assertEquals(1, loaded.size());
		ContainerRecord record = loaded.all().iterator().next();
		assertEquals(new ContainerKey("minecraft:overworld", 10, 64, 20), record.key());
		assertEquals(new ContainerKey("minecraft:overworld", 11, 64, 20), record.secondary());
		assertEquals(54, record.slotCount());
		assertEquals(1234L, record.lastSeenEpochMillis());
		assertEquals(7, record.counts().values().iterator().next());
	}

	@Test
	void roundTripPreservesNonVanillaEnchantmentIds(@TempDir Path dir) {
		Path file = dir.resolve("world.json");
		IndexStorage.save(file, sampleIndex());
		ItemIdentity loaded = IndexStorage.load(file).pool().all().iterator().next();
		assertEquals(List.of("serverpack:radioactivity"), loaded.enchantmentIds(),
			"un enchant override serveur ne doit jamais etre strippe (spec § 5.1)");
		assertEquals("Radioactif IV", loaded.displayName());
		assertTrue(loaded.searchText().contains("radioactif iv"));
		assertEquals("minecraft:enchanted_book", loaded.itemId());
		assertEquals("inflige des degats", loaded.loreText());
		assertEquals(ItemIdentities.fingerprint("minecraft:enchanted_book",
			"{\"minecraft:custom_name\":\"'Radioactif IV'\"}"), loaded.fingerprint());
	}

	@Test
	void roundTripPreservesRawComponentsJsonVerbatim(@TempDir Path dir) {
		Path file = dir.resolve("world.json");
		IndexStorage.save(file, sampleIndex());
		ItemIdentity loaded = IndexStorage.load(file).pool().all().iterator().next();
		assertEquals("{\"minecraft:custom_name\":\"'Radioactif IV'\"}", loaded.componentsJson());
	}

	@Test
	void missingFileYieldsEmptyIndex(@TempDir Path dir) {
		assertEquals(0, IndexStorage.load(dir.resolve("absent.json")).size());
	}

	@Test
	void corruptFileIsRenamedAndYieldsEmptyIndex(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("world.json");
		Files.writeString(file, "{ broken");
		ContainerIndex loaded = IndexStorage.load(file);
		assertEquals(0, loaded.size());
		assertTrue(Files.exists(dir.resolve("world.json.corrupt")));
	}

	@Test
	void unknownSchemaVersionYieldsEmptyIndexInsteadOfGarbage(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("world.json");
		Files.writeString(file, "{\"schemaVersion\":999,\"items\":[],\"containers\":[]}");
		assertEquals(0, IndexStorage.load(file).size());
	}

	@Test
	void roundTripPreservesNestedCounts(@TempDir Path dir) {
		Path file = dir.resolve("world.json");
		ContainerIndex index = new ContainerIndex();
		ItemIdentity plank = ItemIdentities.of("minecraft:oak_planks", "{}", "Planches", "", List.of());
		String fp = index.pool().intern(plank);
		index.upsert(new ContainerRecord(new ContainerKey("minecraft:overworld", 1, 2, 3), null,
			ContainerKind.CHEST, "Chest", 5L, 27, Map.of(fp, 3), Map.of(fp, 128)));

		IndexStorage.save(file, index);
		ContainerRecord loaded = IndexStorage.load(file).all().iterator().next();
		assertEquals(3, loaded.counts().get(fp));
		assertEquals(128, loaded.nestedCounts().get(fp));
	}

	@Test
	void nestedOnlyFingerprintSurvivesSaveInsteadOfBeingPurgedAsOrphan(@TempDir Path dir) {
		Path file = dir.resolve("world.json");
		ContainerIndex index = new ContainerIndex();
		ItemIdentity plank = ItemIdentities.of("minecraft:oak_planks", "{}", "Planches", "", List.of());
		String fp = index.pool().intern(plank);
		index.upsert(new ContainerRecord(new ContainerKey("minecraft:overworld", 1, 2, 3), null,
			ContainerKind.CHEST, "Chest", 5L, 27, Map.of(), Map.of(fp, 64)));

		IndexStorage.save(file, index);
		ContainerIndex loaded = IndexStorage.load(file);
		assertNotNull(loaded.pool().get(fp),
			"un fingerprint present uniquement en imbrique ne doit pas etre purge a la sauvegarde");
		assertEquals(64, loaded.all().iterator().next().nestedCounts().get(fp));
	}

	@Test
	void schemaVersionOneIsQuarantinedNotSilentlyIgnored(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("world.json");
		Files.writeString(file, "{\"schemaVersion\":1,\"items\":[],\"containers\":[]}");
		assertEquals(0, IndexStorage.load(file).size());
		assertTrue(Files.exists(dir.resolve("world.json.corrupt")),
			"un index d'une version anterieure doit etre mis de cote, pas ecrase ensuite");
	}

	@Test
	void missingNestedCountsInJsonLoadsAsEmpty(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("world.json");
		Files.writeString(file, "{\"schemaVersion\":2,\"items\":[],\"containers\":["
			+ "{\"dimension\":\"minecraft:overworld\",\"x\":1,\"y\":2,\"z\":3,"
			+ "\"kind\":\"CHEST\",\"title\":\"Chest\",\"lastSeen\":1,\"slots\":27,\"counts\":{}}]}");
		ContainerRecord loaded = IndexStorage.load(file).all().iterator().next();
		assertNotNull(loaded.nestedCounts());
		assertTrue(loaded.nestedCounts().isEmpty());
	}

	@Test
	void saveDropsOrphanIdentities(@TempDir Path dir) {
		Path file = dir.resolve("world.json");
		ContainerIndex index = sampleIndex();
		index.pool().intern(ItemIdentities.of("minecraft:stone", "{}", "Stone", "", List.of()));
		assertEquals(2, index.pool().size());
		IndexStorage.save(file, index);
		assertEquals(1, IndexStorage.load(file).pool().size());
	}

	@Test
	void saveCreatesMissingParentDirectories(@TempDir Path dir) {
		Path file = dir.resolve("nested").resolve("deep").resolve("world.json");
		IndexStorage.save(file, sampleIndex());
		assertTrue(java.nio.file.Files.isRegularFile(file));
	}

	@Test
	void saveReturnsFalseWhenWriteCannotSucceed(@TempDir Path dir) throws Exception {
		Path blocker = dir.resolve("blocker");
		Files.writeString(blocker, "not a directory");
		Path file = blocker.resolve("world.json");
		assertFalse(IndexStorage.save(file, sampleIndex()));
	}

	@Test
	void sanitizeReplacesCharactersIllegalInFilenames() {
		assertEquals("mc_example_com_25565", WorldKeys.sanitize("mc.Example.com:25565").replace('.', '_'));
		assertFalse(WorldKeys.sanitize("a/b\\c:d*e?f").matches(".*[/\\\\:*?].*"));
	}

	@Test
	void sanitizeIsStableAndBounded() {
		String long1 = WorldKeys.sanitize("x".repeat(300));
		assertTrue(long1.length() <= 64);
		assertEquals(long1, WorldKeys.sanitize("x".repeat(300)));
	}

	@Test
	void singleplayerAndServerKeysNeverCollide() {
		assertNotEquals(WorldKeys.forSingleplayer("Nouveau monde"), WorldKeys.forServer("Nouveau monde"));
	}

	@Test
	void caseOnlyDifferingSaveNamesAreDisambiguatedByHash() {
		assertNotEquals(WorldKeys.forSingleplayer("Base"), WorldKeys.forSingleplayer("base"));
	}

	@Test
	void differentIllegalCharactersAreDisambiguatedByHash() {
		assertNotEquals(WorldKeys.forServer("my:server"), WorldKeys.forServer("my/server"));
	}

	@Test
	void hashedKeyIsStableAcrossCalls() {
		assertEquals(WorldKeys.forServer("my.server.example.com:25565"),
			WorldKeys.forServer("my.server.example.com:25565"));
	}

	@Test
	void hashedKeyContainsNoFilesystemIllegalCharacters() {
		String key = WorldKeys.forServer("a/b\\c:d*e?f");
		assertFalse(key.matches(".*[/\\\\:*?].*"));
	}

	@Test
	void nullWorldNameFallsBackToUnknownInsteadOfLiteralNull() {
		assertFalse(WorldKeys.forSingleplayer(null).contains("null"));
		assertFalse(WorldKeys.forServer(null).contains("null"));
	}
}
