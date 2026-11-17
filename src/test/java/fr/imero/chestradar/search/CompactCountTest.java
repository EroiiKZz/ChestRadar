package fr.imero.chestradar.search;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompactCountTest {
	@Test
	void countsThatFitTheCellStayExact() {
		assertEquals("0", CompactCount.format(0));
		assertEquals("1", CompactCount.format(1));
		assertEquals("999", CompactCount.format(999));
		assertEquals("1500", CompactCount.format(1500));
		assertEquals("9999", CompactCount.format(9999));
	}

	@Test
	void fiveDigitsAndAboveAreAbbreviated() {
		assertEquals("10k", CompactCount.format(10_000));
		assertEquals("10k", CompactCount.format(10_240));
		assertEquals("99k", CompactCount.format(99_999));
		assertEquals("999k", CompactCount.format(999_999));
		assertEquals("1M", CompactCount.format(1_000_000));
		assertEquals("1.5M", CompactCount.format(1_500_000));
		assertEquals("2.1B", CompactCount.format(Integer.MAX_VALUE));
	}

	@Test
	void truncatesRatherThanRoundingUp() {
		assertEquals("999k", CompactCount.format(999_999));
		assertEquals("1.9M", CompactCount.format(1_999_999));
	}

	@Test
	void noValueEverExceedsFourCharacters() {
		int[] boundaries = {0, 999, 1000, 9999, 10_000, 99_999, 100_000, 999_999, 1_000_000,
			9_999_999, 10_000_000, 999_999_999, 1_000_000_000, Integer.MAX_VALUE};
		for (int value : boundaries) {
			String rendered = CompactCount.format(value);
			assertTrue(rendered.length() <= 4,
				"« " + rendered + " » (" + value + ") depasse 4 caracteres");
		}
	}
}
