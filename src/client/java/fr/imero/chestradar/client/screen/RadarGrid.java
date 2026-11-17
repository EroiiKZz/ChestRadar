package fr.imero.chestradar.client.screen;

import fr.imero.chestradar.ChestRadar;
import fr.imero.chestradar.client.ContainerFocus;
import fr.imero.chestradar.client.RadarActions;
import fr.imero.chestradar.client.RadarPins;
import fr.imero.chestradar.client.capture.ItemIdentityRenderer;
import fr.imero.chestradar.search.AggregatedItem;
import fr.imero.chestradar.search.CompactCount;
import fr.imero.chestradar.search.Pin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RadarGrid implements RadarView {
	private static final int ICON = 16;

	private final int x;
	private final int y;
	private final int width;
	private final int height;

	private List<AggregatedItem> items = new ArrayList<>();
	private double scroll;

	private final ContainerPopup popup = new ContainerPopup();
	private String selected;

	private final Set<String> renderFailuresLogged = new HashSet<>();

	public RadarGrid(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	@Override
	public void setItems(List<AggregatedItem> items) {
		this.items = items;
		if (selected != null && indexOf(selected) < 0) {
			clearSelection();
		}
	}

	@Override
	public void resetScroll() {
		scroll = 0;
		clearSelection();
	}

	@Override
	public void select(String fingerprint) {
		int index = indexOf(fingerprint);
		if (index < 0) {
			return;
		}
		selected = fingerprint;
		AggregatedItem item = items.get(index);
		RadarActions.highlight(item.hits());
		fr.imero.chestradar.client.render.SlotHighlightRenderer.arm(item.identity(), false);
		int columns = columns();
		int step = step();
		int cellX = x + (index % columns) * step;
		int cellY = y - (int) scroll + (index / columns) * step;
		popup.open(item, cellX + step / 2, cellY + RadarTheme.CELL + 2, x, y, width, height);
	}

	@Override
	public boolean clearSelection() {
		if (selected == null && !popup.isOpen()) {
			return false;
		}
		selected = null;
		popup.close();
		return true;
	}

	private int indexOf(String fingerprint) {
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).identity().fingerprint().equals(fingerprint)) {
				return i;
			}
		}
		return -1;
	}

	private int step() {
		return RadarTheme.CELL + RadarTheme.CELL_GAP;
	}

	private int columns() {
		return Math.max(1, (width - Theme.SCROLLBAR_WIDTH - RadarTheme.CELL_GAP) / step());
	}

	private int rows() {
		return (int) Math.ceil(items.size() / (double) columns());
	}

	private int contentHeight() {
		return rows() * step();
	}

	private int maxScroll() {
		return Math.max(0, contentHeight() - height);
	}

	private boolean inBounds(double mouseX, double mouseY) {
		return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
	}

	private int indexAt(double mouseX, double mouseY) {
		if (!inBounds(mouseX, mouseY) || popup.contains(mouseX, mouseY)) {
			return -1;
		}
		int columns = columns();
		int step = step();
		int localX = (int) (mouseX - x);
		int localY = (int) (mouseY - y + scroll);
		int col = localX / step;
		int row = localY / step;
		if (col >= columns) {
			return -1;
		}
		if (localX - col * step >= RadarTheme.CELL || localY - row * step >= RadarTheme.CELL) {
			return -1;
		}
		int index = row * columns + col;
		return index >= 0 && index < items.size() ? index : -1;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (popup.mouseScrolled(mouseX, mouseY, amount)) {
			return true;
		}
		if (!inBounds(mouseX, mouseY)) {
			return false;
		}
		clearSelection();
		scroll = Math.clamp(scroll - amount * step(), 0, maxScroll());
		return true;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (popup.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		int index = indexAt(mouseX, mouseY);
		if (index < 0) {
			return inBounds(mouseX, mouseY) && clearSelection();
		}
		AggregatedItem item = items.get(index);
		if (button == InputUtil.GLFW_MOUSE_BUTTON_MIDDLE) {
			PinGesture.apply(MinecraftClient.getInstance(), item);
			return true;
		}
		if (button != InputUtil.GLFW_MOUSE_BUTTON_RIGHT) {
			if (item.nearestHit() != null) {
				ContainerFocus.focus(MinecraftClient.getInstance(), item.hits(),
					item.nearestHit().container().key(),
					item.identity().displayName(), item.nearestHit().totalCount());
			}
			return true;
		}
		String fingerprint = item.identity().fingerprint();
		if (fingerprint.equals(selected) && popup.isOpen()) {
			clearSelection();
			return true;
		}
		select(fingerprint);
		return true;
	}

	@Override
	public AggregatedItem hoveredItem(double mouseX, double mouseY) {
		int index = indexAt(mouseX, mouseY);
		return index < 0 ? null : items.get(index);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY) {
		MinecraftClient client = MinecraftClient.getInstance();
		TextRenderer font = client.textRenderer;

		scroll = Math.clamp(scroll, 0, maxScroll());

		context.enableScissor(x, y, x + width, y + height);
		int columns = columns();
		int step = step();

		for (int i = 0; i < items.size(); i++) {
			int col = i % columns;
			int row = i / columns;
			int cellX = x + col * step;
			int cellY = y - (int) scroll + row * step;
			if (cellY + RadarTheme.CELL < y || cellY > y + height) {
				continue;
			}
			renderCell(context, font, client, items.get(i), cellX, cellY, mouseX, mouseY);
		}

		context.disableScissor();
		Panel.scrollbar(context, x + width - Theme.SCROLLBAR_WIDTH, y, height, contentHeight(), scroll);
		popup.render(context, mouseX, mouseY);
	}

	private void renderCell(DrawContext context, TextRenderer font, MinecraftClient client,
			AggregatedItem item, int cellX, int cellY, int mouseX, int mouseY) {
		boolean hovered = mouseX >= cellX && mouseX < cellX + RadarTheme.CELL
			&& mouseY >= cellY && mouseY < cellY + RadarTheme.CELL;
		context.fill(cellX, cellY, cellX + RadarTheme.CELL, cellY + RadarTheme.CELL,
			hovered ? Theme.ROW_HOVER : RadarTheme.SLOT);
		if (item.identity().fingerprint().equals(selected)) {
			context.fill(cellX, cellY, cellX + RadarTheme.CELL, cellY + 1, Theme.ACCENT);
			context.fill(cellX, cellY + RadarTheme.CELL - 1, cellX + RadarTheme.CELL, cellY + RadarTheme.CELL, Theme.ACCENT);
			context.fill(cellX, cellY, cellX + 1, cellY + RadarTheme.CELL, Theme.ACCENT);
			context.fill(cellX + RadarTheme.CELL - 1, cellY, cellX + RadarTheme.CELL, cellY + RadarTheme.CELL, Theme.ACCENT);
		}

		try {
			ItemStack stack = ItemIdentityRenderer.toStack(item.identity(), client);
			int iconX = cellX + (RadarTheme.CELL - ICON) / 2;
			int iconY = cellY + (RadarTheme.CELL - ICON) / 2;
			context.drawItem(stack, iconX, iconY);
			context.drawStackOverlay(font, stack, iconX, iconY);
			drawCount(context, font, item.totalCount(), cellX, cellY);
			drawPin(context, item, cellX, cellY);
		} catch (Exception e) {
			logRenderFailureOnce(item.identity().fingerprint(), e);
			context.fill(cellX + 1, cellY + 1, cellX + RadarTheme.CELL - 1, cellY + RadarTheme.CELL - 1,
				Theme.mix(RadarTheme.SLOT, RadarTheme.MISSING, 0.35));
		}
	}

	private void drawCount(DrawContext context, TextRenderer font, int total, int cellX, int cellY) {
		String label = CompactCount.format(total);
		context.drawText(font, label, cellX + RadarTheme.CELL - 1 - font.getWidth(label),
			cellY + RadarTheme.CELL - 9, 0xFFFFFFFF, true);
	}

	private void drawPin(DrawContext context, AggregatedItem item, int cellX, int cellY) {
		Pin pin = RadarPins.find(item.identity().fingerprint());
		if (pin == null) {
			return;
		}
		context.fill(cellX + 1, cellY + 1, cellX + 4, cellY + 4, RadarTheme.PIN);
		if (!pin.hasTarget()) {
			return;
		}
		boolean done = item.totalCount() >= pin.target();
		int left = cellX + 2;
		int right = cellX + RadarTheme.CELL - 2;
		int barY = cellY + RadarTheme.CELL - 3;
		context.fill(left, barY, right, barY + 2, RadarTheme.PROGRESS_TRACK);
		int filled = left + (int) ((right - left)
			* Math.clamp(item.totalCount() / (double) pin.target(), 0.0, 1.0));
		if (filled > left) {
			context.fill(left, barY, filled, barY + 2, done ? RadarTheme.PROGRESS_DONE : RadarTheme.PROGRESS_FILL);
		}
	}

	private void logRenderFailureOnce(String fingerprint, Exception e) {
		if (renderFailuresLogged.add(fingerprint)) {
			ChestRadar.LOGGER.warn("[chestradar] rendu grille impossible pour l'item {} (un seul avertissement)",
				fingerprint, e);
		}
	}
}
