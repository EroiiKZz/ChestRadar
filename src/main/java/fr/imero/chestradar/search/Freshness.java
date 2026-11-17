package fr.imero.chestradar.search;

public final class Freshness {
	public static final long MINUTE_MS = 60_000L;
	public static final long HOUR_MS = 3_600_000L;
	public static final long DAY_MS = 86_400_000L;

	public enum Unit { NOW, MINUTES, HOURS, DAYS }

	public record Age(long amount, Unit unit) {
	}

	private Freshness() {
	}

	public static Age of(long ageMillis) {
		if (ageMillis < MINUTE_MS) {
			return new Age(0, Unit.NOW);
		}
		if (ageMillis < HOUR_MS) {
			return new Age(ageMillis / MINUTE_MS, Unit.MINUTES);
		}
		if (ageMillis < DAY_MS) {
			return new Age(ageMillis / HOUR_MS, Unit.HOURS);
		}
		return new Age(ageMillis / DAY_MS, Unit.DAYS);
	}

	public static Age between(long lastSeenEpochMillis, long nowEpochMillis) {
		return of(nowEpochMillis - lastSeenEpochMillis);
	}

	public static boolean isStale(long lastSeenEpochMillis, long nowEpochMillis, int staleAfterDays) {
		if (staleAfterDays <= 0) {
			return false;
		}
		return nowEpochMillis - lastSeenEpochMillis >= staleAfterDays * DAY_MS;
	}

	public static String translationKey(Unit unit) {
		return switch (unit) {
			case NOW -> "chestradar.age.now";
			case MINUTES -> "chestradar.age.minutes";
			case HOURS -> "chestradar.age.hours";
			case DAYS -> "chestradar.age.days";
		};
	}
}
