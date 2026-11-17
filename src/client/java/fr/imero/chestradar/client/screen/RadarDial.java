package fr.imero.chestradar.client.screen;

import fr.imero.chestradar.client.Bearing;
import fr.imero.chestradar.data.ContainerKey;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.List;
import java.util.Set;

public final class RadarDial {
	private static final int SWEEP_PERIOD_MS = 3600;
	private static final double PING_ARC = 70.0;
	private static final int INNER_RADIUS = 3;
	private static final double DISTANCE_CURVE = 0.65;

	public record Contact(ContainerKey key, double distance) {
	}

	private RadarDial() {
	}

	public static void render(DrawContext context, MinecraftClient client, int x, int y,
			List<Contact> contacts, Set<ContainerKey> hot, double maxDistance) {
		int radius = (RadarTheme.DIAL_SIZE - 1) / 2;
		int cx = x + radius;
		int cy = y + radius;

		face(context, cx, cy, radius);
		ring(context, cx, cy, radius, 3, Theme.BORDER);
		ring(context, cx, cy, radiusFor(0.5, radius), 30, Theme.alpha(Theme.BORDER, 0x99));

		double sweep = System.currentTimeMillis() % SWEEP_PERIOD_MS / (double) SWEEP_PERIOD_MS * 360.0;
		sweepRay(context, cx, cy, radius, sweep);
		contacts(context, client, cx, cy, radius, contacts, hot, maxDistance, sweep);

		context.fill(cx, cy, cx + 1, cy + 1, Theme.TEXT_PRIMARY);
		context.fill(cx - 2, cy - radius, cx + 3, cy - radius + 1, Theme.ACCENT);
		context.fill(cx - 1, cy - radius + 1, cx + 2, cy - radius + 2, Theme.ACCENT);
		context.fill(cx, cy - radius + 2, cx + 1, cy - radius + 3, Theme.ACCENT);
	}

	private static void face(DrawContext context, int cx, int cy, int radius) {
		for (int dy = -radius; dy <= radius; dy++) {
			int half = (int) Math.sqrt(radius * radius - dy * dy);
			context.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, RadarTheme.DIAL_FACE);
		}
	}

	private static void ring(DrawContext context, int cx, int cy, int radius, int step, int color) {
		if (radius <= 0) {
			return;
		}
		for (int angle = 0; angle < 360; angle += step) {
			double rad = Math.toRadians(angle);
			int px = cx + (int) Math.round(Math.sin(rad) * radius);
			int py = cy - (int) Math.round(Math.cos(rad) * radius);
			context.fill(px, py, px + 1, py + 1, color);
		}
	}

	private static void sweepRay(DrawContext context, int cx, int cy, int radius, double sweep) {
		double rad = Math.toRadians(sweep);
		for (int i = 2; i <= radius; i++) {
			int px = cx + (int) Math.round(Math.sin(rad) * i);
			int py = cy - (int) Math.round(Math.cos(rad) * i);
			context.fill(px, py, px + 1, py + 1,
				Theme.alpha(RadarTheme.PATINA, 0x28 + (int) (0x70 * (i / (double) radius))));
		}
	}

	private static int radiusFor(double ratio, int radius) {
		int span = Math.max(1, radius - INNER_RADIUS - 1);
		return INNER_RADIUS
			+ (int) Math.round(span * Math.pow(Math.clamp(ratio, 0.0, 1.0), DISTANCE_CURVE));
	}

	private static void contacts(DrawContext context, MinecraftClient client, int cx, int cy,
			int radius, List<Contact> contacts, Set<ContainerKey> hot, double maxDistance,
			double sweep) {
		for (Contact contact : contacts) {
			double degrees = Bearing.relativeDegrees(client, contact.key());
			if (Double.isNaN(degrees)) {
				continue;
			}
			double ratio = maxDistance <= 0 ? 1.0
				: Math.clamp(contact.distance() / maxDistance, 0.0, 1.0);
			int r = radiusFor(ratio, radius);
			double rad = Math.toRadians(degrees);
			int px = cx + (int) Math.round(Math.sin(rad) * r);
			int py = cy - (int) Math.round(Math.cos(rad) * r);

			boolean lit = hot.contains(contact.key());
			int rest = lit ? Theme.ACCENT : Theme.alpha(RadarTheme.PATINA, 0x8C);
			double since = ((sweep - degrees) % 360 + 360) % 360;
			int color = since < PING_ARC
				? Theme.mix(rest, lit ? 0xFFFFF3D6 : RadarTheme.PATINA, 1 - since / PING_ARC)
				: rest;
			int size = lit ? 2 : 1;
			context.fill(px, py, px + size, py + size, color);
		}
	}
}
