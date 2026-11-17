package fr.imero.chestradar.client;

import fr.imero.chestradar.data.ContainerKey;
import fr.imero.chestradar.data.Dimensions;
import net.minecraft.client.MinecraftClient;

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

	public static void tick(MinecraftClient client) {
		if (key == null || client == null || client.player == null || client.world == null) {
			return;
		}
		String dimensionId = client.world.getRegistryKey().getValue().toString();
		if (!dimensionId.equals(key.dimensionId())) {
			return;
		}
		var pos = client.player.getEntityPos();
		if (Dimensions.distance(key, dimensionId, pos.x, pos.y, pos.z)
			<= RadarState.config().compassArrivalDistance) {
			clear();
		}
	}
}
