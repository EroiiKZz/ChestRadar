package fr.imero.chestradar.search;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PinsTest {
	private static Pin pin(String fingerprint) {
		return new Pin(fingerprint, "minecraft:iron_ingot", "Lingot de fer", 0);
	}

	@Test
	void toggleAddsThenRemoves() {
		List<Pin> pins = Pins.toggle(List.of(), pin("aaa"));
		assertTrue(Pins.isPinned(pins, "aaa"));
		pins = Pins.toggle(pins, pin("aaa"));
		assertFalse(Pins.isPinned(pins, "aaa"));
	}

	@Test
	void toggleNeverMutatesTheListItWasGiven() {
		List<Pin> original = new ArrayList<>(List.of(pin("aaa")));
		List<Pin> updated = Pins.toggle(original, pin("bbb"));
		assertEquals(1, original.size());
		assertEquals(2, updated.size());
	}

	@Test
	void toggleStopsAtTheCapWithoutEvictingAnything() {
		List<Pin> pins = new ArrayList<>();
		for (int i = 0; i < Pins.MAX; i++) {
			pins = Pins.toggle(pins, pin("fp" + i));
		}
		assertEquals(Pins.MAX, pins.size());
		List<Pin> refused = Pins.toggle(pins, pin("un-de-trop"));
		assertEquals(Pins.MAX, refused.size());
		assertFalse(Pins.isPinned(refused, "un-de-trop"));
		assertTrue(Pins.isPinned(refused, "fp0"));
	}

	@Test
	void withTargetOnlyTouchesTheMatchingPin() {
		List<Pin> pins = List.of(pin("aaa"), pin("bbb"));
		List<Pin> updated = Pins.withTarget(pins, "bbb", 2000);
		assertEquals(0, Pins.find(updated, "aaa").target());
		assertEquals(2000, Pins.find(updated, "bbb").target());
		assertTrue(Pins.find(updated, "bbb").hasTarget());
	}

	@Test
	void withTargetOnAnUnpinnedFingerprintIsANoOp() {
		List<Pin> pins = List.of(pin("aaa"));
		assertEquals(1, Pins.withTarget(pins, "inconnu", 10).size());
		assertFalse(Pins.isPinned(Pins.withTarget(pins, "inconnu", 10), "inconnu"));
	}

	@Test
	void aNegativeTargetBecomesNoTarget() {
		List<Pin> pins = Pins.withTarget(List.of(pin("aaa")), "aaa", -5);
		assertEquals(0, Pins.find(pins, "aaa").target());
		assertFalse(Pins.find(pins, "aaa").hasTarget());
	}

	@Test
	void sanitizeSurvivesAHandEditedFile() {
		List<Pin> dirty = Arrays.asList(
			null,
			new Pin(null, "x", "x", 1),
			new Pin("  ", "x", "x", 1),
			new Pin("aaa", null, null, -3),
			new Pin("aaa", "minecraft:stone", "Pierre", 99),
			new Pin("bbb", "minecraft:stone", "Pierre", 5));

		List<Pin> clean = Pins.sanitize(dirty);
		assertEquals(2, clean.size());
		assertEquals(0, Pins.find(clean, "aaa").target());
		assertEquals("", Pins.find(clean, "aaa").itemId());
		assertEquals("", Pins.find(clean, "aaa").displayName());
		assertEquals(5, Pins.find(clean, "bbb").target());
	}

	@Test
	void sanitizeCapsTheListLength() {
		List<Pin> tooMany = new ArrayList<>();
		for (int i = 0; i < Pins.MAX * 2; i++) {
			tooMany.add(pin("fp" + i));
		}
		assertEquals(Pins.MAX, Pins.sanitize(tooMany).size());
	}

	@Test
	void nullListsAreTreatedAsEmptyEverywhere() {
		assertNull(Pins.find(null, "aaa"));
		assertFalse(Pins.isPinned(null, "aaa"));
		assertTrue(Pins.fingerprints(null).isEmpty());
		assertTrue(Pins.sanitize(null).isEmpty());
		assertTrue(Pins.withTarget(null, "aaa", 1).isEmpty());
		assertEquals(1, Pins.toggle(null, pin("aaa")).size());
	}
}
