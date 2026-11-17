package fr.imero.chestradar.client.screen;

import fr.imero.chestradar.client.RadarPins;
import fr.imero.chestradar.search.AggregatedItem;
import fr.imero.chestradar.search.Pin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public final class PinGesture {
	private static final int BAR_HEIGHT = 3;

	private PinGesture() {
	}

	public static void apply(Minecraft client, AggregatedItem item) {
		if (client == null || item == null) {
			return;
		}
		boolean shift = InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
			|| InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
		if (!shift) {
			RadarPins.toggle(item);
			return;
		}
		if (!RadarPins.isPinned(item.identity().fingerprint()) && !RadarPins.toggle(item)) {
			return;
		}
		client.setScreenAndShow(new PinTargetScreen(client.gui.screen(), item));
	}

	public static void drawProgress(GuiGraphicsExtractor context, Font font, AggregatedItem item,
			int left, int y, int right) {
		Pin pin = RadarPins.find(item.identity().fingerprint());
		if (pin == null || !pin.hasTarget() || right - left < 20) {
			return;
		}
		double ratio = Math.clamp(item.totalCount() / (double) pin.target(), 0.0, 1.0);
		boolean done = item.totalCount() >= pin.target();

		String fraction = item.totalCount() + " / " + pin.target();
		int fractionWidth = font.width(fraction);
		int barRight = right;
		if (fractionWidth + 24 < right - left) {
			context.text(font, fraction, right - fractionWidth, y - 4,
				done ? RadarTheme.PROGRESS_DONE : Theme.TEXT_SECONDARY, false);
			barRight = right - fractionWidth - 6;
		}

		Shape.pill(context, left, y, barRight, y + BAR_HEIGHT, RadarTheme.PROGRESS_TRACK);
		int filled = left + (int) ((barRight - left) * ratio);
		if (filled > left) {
			Shape.pill(context, left, y, filled, y + BAR_HEIGHT,
				done ? RadarTheme.PROGRESS_DONE : Theme.GOOD);
		}
	}
}
