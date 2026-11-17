package fr.imero.chestradar.client.screen;

import fr.imero.chestradar.ChestRadar;
import fr.imero.chestradar.client.ContainerFocus;
import fr.imero.chestradar.client.RadarActions;
import fr.imero.chestradar.client.RadarPins;
import fr.imero.chestradar.client.capture.ItemIdentityRenderer;
import fr.imero.chestradar.client.render.SlotHighlightRenderer;
import fr.imero.chestradar.search.AggregatedItem;
import fr.imero.chestradar.search.CompactCount;
import fr.imero.chestradar.search.Pin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RadarGrid implements RadarView {
	private static final int ICON = 16;
	private static final int MIN_CELL = 30;
	private static final int CELL_HEIGHT = 28;
	private static final int RAIL = Theme.SCROLLBAR_WIDTH + 3;

	private final int x;
	private final int y;
	private final int width;
	private final int height;

	private List<AggregatedItem> items = new ArrayList<>();
	private double scroll;
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
			selected = null;
		}
	}

	@Override
	public void resetScroll() {
		scroll = 0;
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
		SlotHighlightRenderer.arm(item.identity(), false);
		int top = (index / columns()) * step();
		if (top < scroll || top + CELL_HEIGHT > scroll + height) {
			scroll = Math.clamp(top - (height - CELL_HEIGHT) / 2.0, 0, maxScroll());
		}
	}

	@Override
	public AggregatedItem selectedItem() {
		int index = selected == null ? -1 : indexOf(selected);
		return index < 0 ? null : items.get(index);
	}

	@Override
	public boolean clearSelection() {
		if (selected == null) {
			return false;
		}
		selected = null;
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
		return CELL_HEIGHT + Theme.SPACE_2;
	}

	private int columns() {
		return Math.max(1, (width - RAIL + Theme.SPACE_2) / (MIN_CELL + Theme.SPACE_2));
	}

	private int cellWidth() {
		int columns = columns();
		return (width - RAIL - (columns - 1) * Theme.SPACE_2) / columns;
	}

	private int cellLeft(int column) {
		return x + column * (cellWidth() + Theme.SPACE_2);
	}

	private int rows() {
		return (int) Math.ceil(items.size() / (double) columns());
	}

	private int contentHeight() {
		return Math.max(0, rows() * step() - Theme.SPACE_2);
	}

	private int maxScroll() {
		return Math.max(0, contentHeight() - height);
	}

	private boolean inBounds(double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	private int indexAt(double mouseX, double mouseY) {
		if (!inBounds(mouseX, mouseY)) {
			return -1;
		}
		int cell = cellWidth() + Theme.SPACE_2;
		int localX = (int) (mouseX - x);
		int localY = (int) (mouseY - y + scroll);
		int col = localX / cell;
		int row = localY / step();
		if (col >= columns() || localX - col * cell >= cellWidth() || localY - row * step() >= CELL_HEIGHT) {
			return -1;
		}
		int index = row * columns() + col;
		return index >= 0 && index < items.size() ? index : -1;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (!inBounds(mouseX, mouseY)) {
			return false;
		}
		scroll = Math.clamp(scroll - amount * step(), 0, maxScroll());
		return true;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int index = indexAt(mouseX, mouseY);
		if (index < 0) {
			return false;
		}
		AggregatedItem item = items.get(index);
		if (button == InputConstants.MOUSE_BUTTON_MIDDLE) {
			PinGesture.apply(Minecraft.getInstance(), item);
			return true;
		}
		if (button != InputConstants.MOUSE_BUTTON_RIGHT) {
			if (item.nearestHit() != null) {
				ContainerFocus.focus(Minecraft.getInstance(), item.hits(),
					item.nearestHit().container().key(),
					item.identity().displayName(), item.nearestHit().totalCount());
			}
			return true;
		}
		String fingerprint = item.identity().fingerprint();
		if (fingerprint.equals(selected)) {
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
	public void render(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		Minecraft client = Minecraft.getInstance();
		Font font = client.font;
		scroll = Math.clamp(scroll, 0, maxScroll());
		int columns = columns();
		int cellWidth = cellWidth();

		context.enableScissor(x, y, x + width, y + height);
		for (int i = 0; i < items.size(); i++) {
			int cellX = cellLeft(i % columns);
			int cellY = y - (int) scroll + (i / columns) * step();
			if (cellY + CELL_HEIGHT < y || cellY > y + height) {
				continue;
			}
			renderCell(context, font, client, items.get(i), cellX, cellY, cellWidth, mouseX, mouseY);
		}
		context.disableScissor();
		Panel.scrollbar(context, x + width - Theme.SCROLLBAR_WIDTH, y, height, contentHeight(), scroll);
	}

	private void renderCell(GuiGraphicsExtractor context, Font font, Minecraft client, AggregatedItem item,
			int cellX, int cellY, int cellWidth, int mouseX, int mouseY) {
		int right = cellX + cellWidth;
		int bottom = cellY + CELL_HEIGHT;
		boolean hovered = mouseX >= cellX && mouseX < right && mouseY >= cellY && mouseY < bottom;
		Shape.fill(context, cellX, cellY, right, bottom, Theme.TILE_RADIUS,
			hovered ? Theme.mix(Theme.TILE, Theme.TEXT_PRIMARY, 0.07) : Theme.TILE);
		if (item.identity().fingerprint().equals(selected)) {
			Shape.stroke(context, cellX, cellY, right, bottom, Theme.TILE_RADIUS, 1, Theme.ACCENT);
		}
		try {
			ItemStack stack = ItemIdentityRenderer.toStack(item.identity(), client);
			int iconX = cellX + (cellWidth - ICON) / 2;
			int iconY = cellY + (CELL_HEIGHT - ICON) / 2 - 1;
			context.item(stack, iconX, iconY);
			String label = CompactCount.format(item.totalCount());
			context.text(font, label, right - 3 - font.width(label), bottom - 10, 0xFFFFFFFF, true);
			drawPin(context, item, cellX, cellY, right, bottom);
		} catch (Exception e) {
			logRenderFailureOnce(item.identity().fingerprint(), e);
			Shape.fill(context, cellX + 1, cellY + 1, right - 1, bottom - 1, Shape.inner(1),
				Theme.mix(Theme.TILE, Theme.ALERT, 0.35));
		}
	}

	private void drawPin(GuiGraphicsExtractor context, AggregatedItem item, int cellX, int cellY, int right,
			int bottom) {
		Pin pin = RadarPins.find(item.identity().fingerprint());
		if (pin == null) {
			return;
		}
		Shape.pill(context, cellX + 3, cellY + 3, cellX + 7, cellY + 7, Theme.ACCENT);
		if (!pin.hasTarget()) {
			return;
		}
		int left = cellX + 3;
		int end = right - 3;
		int barY = bottom - 3;
		Shape.pill(context, left, barY, end, barY + 2, RadarTheme.PROGRESS_TRACK);
		int filled = left + (int) ((end - left) * Math.clamp(item.totalCount() / (double) pin.target(), 0.0, 1.0));
		if (filled > left) {
			Shape.pill(context, left, barY, filled, barY + 2,
				item.totalCount() >= pin.target() ? RadarTheme.PROGRESS_DONE : Theme.GOOD);
		}
	}

	private void logRenderFailureOnce(String fingerprint, Exception e) {
		if (renderFailuresLogged.add(fingerprint)) {
			ChestRadar.LOGGER.warn("[chestradar] rendu grille impossible pour l'item {} (un seul avertissement)",
				fingerprint, e);
		}
	}
}
