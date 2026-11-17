package fr.imero.chestradar.data;

public final class Dimensions {
	public static final String OVERWORLD = "minecraft:overworld";
	public static final String NETHER = "minecraft:the_nether";
	public static final String END = "minecraft:the_end";

	private Dimensions() {
	}

	public static double horizontalScale(String from, String to) {
		if (from == null || to == null || from.equals(to)) {
			return 1.0;
		}
		if (NETHER.equals(from) && OVERWORLD.equals(to)) {
			return 8.0;
		}
		if (OVERWORLD.equals(from) && NETHER.equals(to)) {
			return 0.125;
		}
		return 1.0;
	}

	public static double distance(ContainerKey key, String viewerDimension, double px, double py, double pz) {
		double scale = horizontalScale(key.dimensionId(), viewerDimension);
		if (scale == 1.0) {
			return key.distanceTo(px, py, pz);
		}
		double dx = key.x() * scale - px;
		double dz = key.z() * scale - pz;
		double dy = key.y() - py;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	public static String shortName(String dimensionId) {
		if (dimensionId == null || dimensionId.isBlank()) {
			return "?";
		}
		String path = dimensionId.substring(dimensionId.indexOf(':') + 1);
		return path.startsWith("the_") ? path.substring(4) : path;
	}

	public static boolean matches(String dimensionId, String needle) {
		if (needle == null || needle.isBlank()) {
			return true;
		}
		String lower = needle.toLowerCase(java.util.Locale.ROOT);
		return dimensionId != null
			&& (dimensionId.toLowerCase(java.util.Locale.ROOT).contains(lower)
				|| shortName(dimensionId).toLowerCase(java.util.Locale.ROOT).contains(lower));
	}
}
