package fr.imero.chestradar.client;

import fr.imero.chestradar.data.ContainerKey;
import fr.imero.chestradar.data.Dimensions;
import net.minecraft.client.Minecraft;

public final class RadarTarget {
	private static ContainerKey key;
	private static String label = "";
	private static int quantity;

	private RadarTarget() {
	}

	public static ContainerKey key() {
		return key;
	}

	public static String label() {
		return label;
	}

	public static int quantity() {
		return quantity;
	}

	public static boolean isActive() {
		return key != null;
	}

	public static void set(ContainerKey key, String label, int quantity) {
		RadarTarget.key = key;
		RadarTarget.label = label == null ? "" : label;
		RadarTarget.quantity = Math.max(0, quantity);
	}

	public static void clear() {
		key = null;
		label = "";
		quantity = 0;
	}

	public static void tick(Minecraft client) {
		if (key == null || client == null || client.player == null || client.level == null) {
			return;
		}
		String dimensionId = client.level.dimension().identifier().toString();
		if (!dimensionId.equals(key.dimensionId())) {
			return;
		}
		var pos = client.player.position();
		if (Dimensions.distance(key, dimensionId, pos.x, pos.y, pos.z)
			<= RadarState.config().compassArrivalDistance) {
			clear();
		}
	}
}
