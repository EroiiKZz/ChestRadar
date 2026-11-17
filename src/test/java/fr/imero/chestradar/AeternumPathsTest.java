package fr.imero.chestradar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AeternumPathsTest {
	@Test
	void legacyFileMovesIntoTheSharedFolder(@TempDir Path dir) throws Exception {
		Path legacy = dir.resolve("chestradar.json");
		Files.writeString(legacy, "{\"radius\":48}");
		Path target = dir.resolve("aeternum").resolve("chestradar.json");

		assertEquals(target, AeternumPaths.relocate(legacy, target));
		assertFalse(Files.exists(legacy));
		assertEquals("{\"radius\":48}", Files.readString(target));
	}

	@Test
	void legacyFolderMovesWithEveryFileInside(@TempDir Path dir) throws Exception {
		Path legacy = dir.resolve("config").resolve("chestradar");
		Files.createDirectories(legacy);
		Files.writeString(legacy.resolve("sp_world.json"), "a");
		Files.writeString(legacy.resolve("mp_server.json"), "b");
		Path target = dir.resolve("aeternum").resolve("chestradar");

		assertEquals(target, AeternumPaths.relocate(legacy, target));
		assertFalse(Files.exists(legacy));
		assertEquals("a", Files.readString(target.resolve("sp_world.json")));
		assertEquals("b", Files.readString(target.resolve("mp_server.json")));
	}

	@Test
	void existingTargetWinsAndLegacyIsLeftUntouched(@TempDir Path dir) throws Exception {
		Path legacy = dir.resolve("aeternum.json");
		Files.writeString(legacy, "old");
		Path target = dir.resolve("aeternum").resolve("theme.json");
		Files.createDirectories(target.getParent());
		Files.writeString(target, "new");

		assertEquals(target, AeternumPaths.relocate(legacy, target));
		assertEquals("old", Files.readString(legacy));
		assertEquals("new", Files.readString(target));
	}

	@Test
	void nothingToMoveReturnsTheTargetWithoutCreatingIt(@TempDir Path dir) {
		Path target = dir.resolve("aeternum").resolve("vitrine.json");

		assertEquals(target, AeternumPaths.relocate(dir.resolve("vitrine.json"), target));
		assertFalse(Files.exists(target.getParent()));
	}
}
