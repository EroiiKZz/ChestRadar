package fr.imero.chestradar.client.screen;

import fr.imero.chestradar.client.RadarPins;
import fr.imero.chestradar.search.AggregatedItem;
import fr.imero.chestradar.search.Pin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class PinGesture {
	private static final int BAR_HEIGHT = 3;

	private PinGesture() {
	}

	public static void apply(MinecraftClient client, AggregatedItem item) {
		if (client == null || item == null) {
			return;
		}
		boolean shift = InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
			|| InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
		if (!shift) {
			RadarPins.toggle(item);
			return;
		}
		if (!RadarPins.isPinned(item.identity().fingerprint()) && !RadarPins.toggle(item)) {
			return;
		}
		client.setScreen(new PinTargetScreen(client.currentScreen, item));
	}

	public static void drawProgress(DrawContext context, TextRenderer font, AggregatedItem item,
			int left, int y, int right) {
		Pin pin = RadarPins.find(item.identity().fingerprint());
		if (pin == null || !pin.hasTarget() || right - left < 20) {
			return;
		}
		double ratio = Math.clamp(item.totalCount() / (double) pin.target(), 0.0, 1.0);
		boolean done = item.totalCount() >= pin.target();

		String fraction = item.totalCount() + " / " + pin.target();
		int fractionWidth = font.getWidth(fraction);
		int barRight = right;
		if (fractionWidth + 24 < right - left) {
			context.drawText(font, fraction, right - fractionWidth, y - 4,
				done ? RadarTheme.PROGRESS_DONE : Theme.TEXT_SECONDARY, false);
			barRight = right - fractionWidth - 6;
		}

		context.fill(left, y, barRight, y + BAR_HEIGHT, RadarTheme.PROGRESS_TRACK);
		int filled = left + (int) ((barRight - left) * ratio);
		if (filled > left) {
			context.fill(left, y, filled, y + BAR_HEIGHT,
				done ? RadarTheme.PROGRESS_DONE : RadarTheme.PROGRESS_FILL);
		}
	}
}
