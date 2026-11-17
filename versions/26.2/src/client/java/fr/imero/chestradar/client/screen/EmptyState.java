package fr.imero.chestradar.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

public final class EmptyState {
	private static final int SLOT_SIZE = 26;

	private EmptyState() {
	}

	public static void render(GuiGraphicsExtractor context, Font font, int x, int y, int width,
			int height, ItemStack icon, Component title, Component hint) {
		int blockHeight = SLOT_SIZE + Theme.SPACE_4 + (Theme.LINE + Theme.SPACE_1) + (hint == null ? 0 : (Theme.LINE + Theme.SPACE_1));
		int top = y + Math.max(0, (height - blockHeight) / 2);
		int centerX = x + width / 2;

		int slotX = centerX - SLOT_SIZE / 2;
		Shape.tile(context, slotX, top, slotX + SLOT_SIZE, top + SLOT_SIZE);
		context.item(icon, centerX - Theme.ICON / 2, top + (SLOT_SIZE - Theme.ICON) / 2);

		int textY = top + SLOT_SIZE + Theme.SPACE_4;
		line(context, font, title, centerX, textY, width, Theme.TEXT_PRIMARY);
		if (hint != null) {
			line(context, font, hint, centerX, textY + (Theme.LINE + Theme.SPACE_1), width, Theme.TEXT_SECONDARY);
		}
	}

	private static void line(GuiGraphicsExtractor context, Font font, Component text, int centerX, int y,
			int width, int color) {
		Component shown = Component.literal(Fit.text(font, text.getString(), Math.max(0, width - Theme.PADDING * 2)));
		context.text(font, shown, centerX - font.width(shown) / 2, y, color, false);
	}
}
