package fr.imero.chestradar.client.screen;

import fr.imero.chestradar.client.ChestOpener;
import fr.imero.chestradar.client.ContainerFocus;
import fr.imero.chestradar.client.RadarActions;
import fr.imero.chestradar.search.AggregatedItem;
import fr.imero.chestradar.search.Hit;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;

import java.util.List;

public final class ContainerPopup {
	private static final int MAX_VISIBLE_ROWS = 6;
	private static final int PREFERRED_WIDTH = 250;
	private static final int HEADER_HEIGHT = 18;
	private static final int MARGIN = 4;

	private AggregatedItem item;
	private int x;
	private int y;
	private int width;
	private int height;
	private int visibleRows;
	private int scrollRow;
	private int footerHeight;

	public void open(AggregatedItem item, int anchorX, int anchorY,
			int boundsX, int boundsY, int boundsWidth, int boundsHeight) {
		this.item = item;
		this.scrollRow = 0;
		this.visibleRows = Math.min(MAX_VISIBLE_ROWS, item.hits().size());
		this.footerHeight = item.hits().size() > visibleRows ? 12 : MARGIN;
		this.width = Math.min(PREFERRED_WIDTH, boundsWidth);
		this.height = HEADER_HEIGHT + visibleRows * RadarTheme.SUB_ROW_HEIGHT + footerHeight;
		this.x = Math.clamp(anchorX, boundsX, boundsX + boundsWidth - width);
		this.y = Math.clamp(anchorY, boundsY, Math.max(boundsY, boundsY + boundsHeight - height));
	}

	public void close() {
		item = null;
	}

	public boolean isOpen() {
		return item != null;
	}

	public String fingerprint() {
		return item == null ? null : item.identity().fingerprint();
	}

	public boolean contains(double mouseX, double mouseY) {
		return isOpen() && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!contains(mouseX, mouseY)) {
			return false;
		}
		boolean right = button == InputUtil.GLFW_MOUSE_BUTTON_RIGHT;
		MinecraftClient client = MinecraftClient.getInstance();
		int rowY = y + HEADER_HEIGHT;
		for (Hit hit : visibleHits()) {
			if (HitLine.contains(mouseX, mouseY, x + MARGIN, rowY, rowWidth())) {
				fr.imero.chestradar.client.render.SlotHighlightRenderer.arm(item.identity(), false);
				if (!right && HitLine.isOnOpenButton(client, hit, mouseX, mouseY, x + MARGIN, rowY, rowWidth())) {
					ChestOpener.open(client, hit.container().key());
				} else if (right) {
					RadarActions.highlight(List.of(hit));
				} else {
					ContainerFocus.focus(client, List.of(hit), hit.container().key(),
						item.identity().displayName(), hit.totalCount());
				}
				return true;
			}
			rowY += RadarTheme.SUB_ROW_HEIGHT;
		}
		return true;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (!contains(mouseX, mouseY)) {
			return false;
		}
		scrollRow = Math.clamp(scrollRow - (int) Math.signum(amount), 0, maxScrollRow());
		return true;
	}

	public void render(DrawContext context, int mouseX, int mouseY) {
		if (!isOpen()) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		TextRenderer font = client.textRenderer;

		Panel.frame(context, x, y, width, height);

		String total = "x" + item.totalCount();
		int totalWidth = font.getWidth(total);
		context.drawText(font, total, x + width - MARGIN - totalWidth, y + 6, Theme.ACCENT, false);

		int nameWidth = Math.max(0, width - 2 * MARGIN - totalWidth - Theme.GUTTER);
		context.drawText(font, font.trimToWidth(item.identity().displayName(), nameWidth),
			x + MARGIN, y + 6, Theme.TEXT_PRIMARY, false);
		context.fill(x + MARGIN, y + HEADER_HEIGHT - 2, x + width - MARGIN, y + HEADER_HEIGHT - 1, Theme.BORDER);

		int rowY = y + HEADER_HEIGHT;
		for (Hit hit : visibleHits()) {
			HitLine.render(context, client, hit, x + MARGIN, rowY, rowWidth(), mouseX, mouseY);
			rowY += RadarTheme.SUB_ROW_HEIGHT;
		}

		if (maxScrollRow() > 0) {
			String more = (scrollRow + 1) + "-" + (scrollRow + visibleRows) + " / " + item.hits().size();
			context.drawText(font, more, x + width - MARGIN - font.getWidth(more),
				y + height - footerHeight + 2, Theme.TEXT_SECONDARY, false);
		}
	}

	private int rowWidth() {
		return width - 2 * MARGIN;
	}

	private int maxScrollRow() {
		return Math.max(0, item.hits().size() - visibleRows);
	}

	private List<Hit> visibleHits() {
		int from = Math.clamp(scrollRow, 0, maxScrollRow());
		return item.hits().subList(from, Math.min(item.hits().size(), from + visibleRows));
	}
}
