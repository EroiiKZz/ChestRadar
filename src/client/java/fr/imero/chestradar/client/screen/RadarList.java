package fr.imero.chestradar.client.screen;

import fr.imero.chestradar.ChestRadar;
import fr.imero.chestradar.client.ChestOpener;
import fr.imero.chestradar.client.ContainerFocus;
import fr.imero.chestradar.client.RadarActions;
import fr.imero.chestradar.client.RadarPins;
import fr.imero.chestradar.client.capture.ItemIdentityRenderer;
import fr.imero.chestradar.client.render.SlotHighlightRenderer;
import fr.imero.chestradar.search.AggregatedItem;
import fr.imero.chestradar.search.Hit;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RadarList implements RadarView {
	private static final int RAIL_X = 6;
	private static final int SUB_ROW_INDENT = 14;

	private final int x;
	private final int y;
	private final int width;
	private final int height;

	private List<AggregatedItem> items = new ArrayList<>();
	private final Set<String> expanded = new HashSet<>();
	private double scroll;

	private final Set<String> renderFailuresLogged = new HashSet<>();

	public RadarList(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	@Override
	public void setItems(List<AggregatedItem> items) {
		this.items = items;
		this.expanded.retainAll(items.stream().map(i -> i.identity().fingerprint()).collect(java.util.stream.Collectors.toSet()));
	}

	@Override
	public void select(String fingerprint) {
		for (AggregatedItem item : items) {
			if (item.identity().fingerprint().equals(fingerprint)) {
				expanded.add(fingerprint);
				RadarActions.highlight(item.hits());
				SlotHighlightRenderer.arm(item.identity(), false);
				return;
			}
		}
	}

	@Override
	public boolean clearSelection() {
		return false;
	}

	@Override
	public void resetScroll() {
		scroll = 0;
	}

	private int contentHeight() {
		int total = 0;
		for (AggregatedItem item : items) {
			total += RadarTheme.ROW_HEIGHT;
			if (expanded.contains(item.identity().fingerprint())) {
				total += item.hits().size() * RadarTheme.SUB_ROW_HEIGHT;
			}
		}
		return total;
	}

	private int maxScroll() {
		return Math.max(0, contentHeight() - height);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) {
			return false;
		}
		scroll = Math.clamp(scroll - amount * RadarTheme.ROW_HEIGHT, 0, maxScroll());
		return true;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) {
			return false;
		}
		boolean right = button == InputUtil.GLFW_MOUSE_BUTTON_RIGHT;
		boolean middle = button == InputUtil.GLFW_MOUSE_BUTTON_MIDDLE;
		MinecraftClient client = MinecraftClient.getInstance();
		int cursor = y - (int) scroll;
		for (AggregatedItem item : items) {
			String key = item.identity().fingerprint();
			if (mouseY >= cursor && mouseY < cursor + RadarTheme.ROW_HEIGHT) {
				if (middle) {
					PinGesture.apply(client, item);
				} else if (right) {
					if (!expanded.remove(key)) {
						expanded.add(key);
						RadarActions.highlight(item.hits());
						SlotHighlightRenderer.arm(item.identity(), false);
					}
				} else if (item.nearestHit() != null) {
					SlotHighlightRenderer.arm(item.identity(), false);
					ContainerFocus.focus(client, item.hits(), item.nearestHit().container().key(),
						item.identity().displayName(), item.nearestHit().totalCount());
				}
				return true;
			}
			cursor += RadarTheme.ROW_HEIGHT;

			if (!expanded.contains(key)) {
				continue;
			}
			for (Hit hit : item.hits()) {
				if (HitLine.contains(mouseX, mouseY, subRowX(), cursor, subRowWidth())) {
					SlotHighlightRenderer.arm(item.identity(), false);
					clickHit(client, hit, mouseX, mouseY, cursor, right);
					return true;
				}
				cursor += RadarTheme.SUB_ROW_HEIGHT;
			}
		}
		return false;
	}

	private void clickHit(MinecraftClient client, Hit hit, double mouseX, double mouseY, int rowY, boolean right) {
		if (!right && HitLine.isOnOpenButton(client, hit, mouseX, mouseY, subRowX(), rowY, subRowWidth())) {
			ChestOpener.open(client, hit.container().key());
			return;
		}
		if (right) {
			RadarActions.highlight(List.of(hit));
			return;
		}
		ContainerFocus.focus(client, List.of(hit), hit.container().key(),
			hit.container().title(), hit.totalCount());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY) {
		MinecraftClient client = MinecraftClient.getInstance();

		scroll = Math.clamp(scroll, 0, maxScroll());

		context.enableScissor(x, y, x + width, y + height);
		int cursor = y - (int) scroll;

		for (AggregatedItem item : items) {
			boolean isExpanded = expanded.contains(item.identity().fingerprint());
			if (cursor + RadarTheme.ROW_HEIGHT >= y && cursor <= y + height) {
				boolean hovered = mouseX >= x && mouseX <= x + width
					&& mouseY >= cursor && mouseY < cursor + RadarTheme.ROW_HEIGHT;
				renderRow(context, item, cursor, hovered, isExpanded, client);
			}
			cursor += RadarTheme.ROW_HEIGHT;

			if (isExpanded) {
				int railTop = cursor;
				for (Hit hit : item.hits()) {
					if (cursor + RadarTheme.SUB_ROW_HEIGHT >= y && cursor <= y + height) {
						HitLine.render(context, client, hit, subRowX(), cursor, subRowWidth(),
							mouseX, mouseY);
					}
					cursor += RadarTheme.SUB_ROW_HEIGHT;
				}
				drawRail(context, railTop, item.hits().size());
			}
		}

		context.disableScissor();
		Panel.scrollbar(context, x + width - Theme.SCROLLBAR_WIDTH, y, height, contentHeight(), scroll);
	}

	private void drawRail(DrawContext context, int top, int rows) {
		if (rows <= 0) {
			return;
		}
		int railX = x + RAIL_X;
		int lastTick = top + (rows - 1) * RadarTheme.SUB_ROW_HEIGHT + RadarTheme.SUB_ROW_HEIGHT / 2;
		context.fill(railX, top, railX + 1, lastTick + 1, RadarTheme.RAIL);
		for (int i = 0; i < rows; i++) {
			int tickY = top + i * RadarTheme.SUB_ROW_HEIGHT + RadarTheme.SUB_ROW_HEIGHT / 2;
			context.fill(railX, tickY, x + SUB_ROW_INDENT - 2, tickY + 1, RadarTheme.RAIL);
		}
	}

	private void renderRow(DrawContext context, AggregatedItem item, int rowY, boolean hovered,
			boolean isExpanded, MinecraftClient client) {
		var font = client.textRenderer;

		if (hovered) {
			context.fill(x, rowY, x + width, rowY + RadarTheme.ROW_HEIGHT, Theme.ROW_HOVER);
			context.fill(x, rowY, x + 2, rowY + RadarTheme.ROW_HEIGHT, Theme.ACCENT);
		}

		if (isExpanded) {
			context.fill(x + RAIL_X, rowY + 12, x + RAIL_X + 1, rowY + RadarTheme.ROW_HEIGHT, RadarTheme.RAIL);
		}

		context.drawText(font, isExpanded ? "▾" : "▸", x + 4, rowY + 6,
			isExpanded ? Theme.ACCENT : Theme.TEXT_SECONDARY, false);
		if (RadarPins.isPinned(item.identity().fingerprint())) {
			context.drawText(font, "★", x + 4, rowY + 1, RadarTheme.PIN, false);
		}

		String total = "x" + item.totalCount();
		int nameX = x + 16 + Theme.ICON + Theme.GUTTER;
		int nameWidth = Math.max(0,
			x + width - font.getWidth(total) - Theme.GUTTER * 2 - Theme.SCROLLBAR_WIDTH - nameX);

		try {
			ItemStack stack = ItemIdentityRenderer.toStack(item.identity(), client);
			context.drawItem(stack, x + 16, rowY + 2);
			context.drawText(font, font.trimToWidth(stack.getName().getString(), nameWidth),
				nameX, rowY + 6, Theme.TEXT_PRIMARY, false);
		} catch (Exception e) {
			logRenderFailureOnce(item.identity().fingerprint(), e);
			context.drawText(font, font.trimToWidth(item.identity().displayName(), nameWidth),
				nameX, rowY + 6, Theme.TEXT_PRIMARY, false);
		}

		context.drawText(font, total, x + width - font.getWidth(total) - Theme.GUTTER - Theme.SCROLLBAR_WIDTH,
			rowY + 6, Theme.ACCENT, false);

		PinGesture.drawProgress(context, font, item, x + 16 + Theme.ICON + Theme.GUTTER,
			rowY + RadarTheme.ROW_HEIGHT - 4,
			x + width - font.getWidth(total) - Theme.GUTTER * 2 - Theme.SCROLLBAR_WIDTH);
	}

	private void logRenderFailureOnce(String fingerprint, Exception e) {
		if (renderFailuresLogged.add(fingerprint)) {
			ChestRadar.LOGGER.warn("[chestradar] rendu impossible pour l'item {} (un seul avertissement)",
				fingerprint, e);
		}
	}

	private int subRowWidth() {
		return width - Theme.SCROLLBAR_WIDTH - SUB_ROW_INDENT;
	}

	private int subRowX() {
		return x + SUB_ROW_INDENT;
	}

	@Override
	public AggregatedItem hoveredItem(double mouseX, double mouseY) {
		if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) {
			return null;
		}
		int cursor = y - (int) scroll;
		for (AggregatedItem item : items) {
			if (mouseY >= cursor && mouseY < cursor + RadarTheme.ROW_HEIGHT) {
				return item;
			}
			cursor += RadarTheme.ROW_HEIGHT;
			if (expanded.contains(item.identity().fingerprint())) {
				cursor += item.hits().size() * RadarTheme.SUB_ROW_HEIGHT;
			}
		}
		return null;
	}
}
