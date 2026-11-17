package fr.imero.chestradar.client.screen;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public final class EmptyState {
	private static final int SLOT_SIZE = 26;
	private static final int GAP = 8;
	private static final int LINE = 12;

	private EmptyState() {
	}

	public static void render(DrawContext context, TextRenderer font, int x, int y, int width,
			int height, ItemStack icon, Text title, Text hint) {
		int blockHeight = SLOT_SIZE + GAP + LINE + (hint == null ? 0 : LINE);
		int top = y + Math.max(0, (height - blockHeight) / 2);
		int centerX = x + width / 2;

		int slotX = centerX - SLOT_SIZE / 2;
		context.fill(slotX, top, slotX + SLOT_SIZE, top + SLOT_SIZE, RadarTheme.SLOT);
		context.fill(slotX, top, slotX + SLOT_SIZE, top + 1, Theme.BORDER);
		context.fill(slotX, top + SLOT_SIZE - 1, slotX + SLOT_SIZE, top + SLOT_SIZE, Theme.BORDER);
		context.fill(slotX, top, slotX + 1, top + SLOT_SIZE, Theme.BORDER);
		context.fill(slotX + SLOT_SIZE - 1, top, slotX + SLOT_SIZE, top + SLOT_SIZE, Theme.BORDER);
		context.drawItem(icon, centerX - Theme.ICON / 2, top + (SLOT_SIZE - Theme.ICON) / 2);

		int textY = top + SLOT_SIZE + GAP;
		line(context, font, title, centerX, textY, width, Theme.TEXT_PRIMARY);
		if (hint != null) {
			line(context, font, hint, centerX, textY + LINE, width, Theme.TEXT_SECONDARY);
		}
	}

	private static void line(DrawContext context, TextRenderer font, Text text, int centerX, int y,
			int width, int color) {
		String shown = font.trimToWidth(text.getString(), Math.max(0, width - Theme.PADDING * 2));
		context.drawText(font, shown, centerX - font.getWidth(shown) / 2, y, color, false);
	}
}
