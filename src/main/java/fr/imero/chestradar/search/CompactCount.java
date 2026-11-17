package fr.imero.chestradar.search;

public final class CompactCount {
	private static final String[] SUFFIXES = {"k", "M", "B"};

	private CompactCount() {
	}

	public static String format(int count) {
		if (count < 10_000) {
			return Integer.toString(count);
		}
		long divisor = 1L;
		int unit = -1;
		while (count / divisor >= 1000L && unit < SUFFIXES.length - 1) {
			divisor *= 1000L;
			unit++;
		}
		long whole = count / divisor;
		if (whole >= 10L) {
			return whole + SUFFIXES[unit];
		}
		long tenths = (count % divisor) * 10L / divisor;
		return tenths == 0L ? whole + SUFFIXES[unit] : whole + "." + tenths + SUFFIXES[unit];
	}
}
