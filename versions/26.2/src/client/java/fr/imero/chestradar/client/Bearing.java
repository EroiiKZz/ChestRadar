package fr.imero.chestradar.client;

import fr.imero.chestradar.data.ContainerKey;
import fr.imero.chestradar.data.Dimensions;
import net.minecraft.client.Minecraft;

public final class Bearing {
	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

	private static final double DEAD_ZONE_SQUARED = 4.0;

	private Bearing() {
	}

	private static Offset offset(Minecraft client, ContainerKey key) {
		if (client == null || client.player == null || client.level == null || key == null) {
			return null;
		}
		var pos = client.player.position();
		String viewerDimension = client.level.dimension().identifier().toString();
		double scale = Dimensions.horizontalScale(key.dimensionId(), viewerDimension);
		return new Offset(key.x() * scale + 0.5 - pos.x, key.y() + 0.5 - pos.y,
			key.z() * scale + 0.5 - pos.z);
	}

	private record Offset(double dx, double dy, double dz) {
		double horizontalSquared() {
			return dx * dx + dz * dz;
		}
	}

	public static double relativeDegrees(Minecraft client, ContainerKey key) {
		Offset offset = offset(client, key);
		return offset == null ? Double.NaN : degrees(offset, client.player.getYRot());
	}

	private static double degrees(Offset offset, float yaw) {
		return Math.toDegrees(Math.atan2(-offset.dx(), offset.dz())) - yaw;
	}

	public static String toward(Minecraft client, ContainerKey key) {
		Offset offset = offset(client, key);
		if (offset == null) {
			return "";
		}
		String vertical = offset.dy() >= 3 ? "▴" : offset.dy() <= -3 ? "▾" : "";
		if (offset.horizontalSquared() < DEAD_ZONE_SQUARED) {
			return "•" + vertical;
		}
		int index = (int) Math.round(degrees(offset, client.player.getYRot()) / 45.0);
		index = ((index % 8) + 8) % 8;
		return ARROWS[index] + vertical;
	}
}
