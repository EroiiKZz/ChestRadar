package fr.imero.chestradar.client.screen;

import fr.imero.chestradar.client.Bearing;
import fr.imero.chestradar.client.ChestOpener;
import fr.imero.chestradar.client.RadarScope;
import fr.imero.chestradar.client.RadarState;
import fr.imero.chestradar.data.ContainerKey;
import fr.imero.chestradar.data.ContainerKind;
import fr.imero.chestradar.data.Dimensions;
import fr.imero.chestradar.search.Freshness;
import fr.imero.chestradar.search.Hit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.Map;

public final class HitLine {
	private HitLine() {
	}

	private static final int ICON_X = 2;
	private static final int TEXT_BASELINE = 5;
	private static final int OPEN_WIDTH = 46;
	private static final int OPEN_HEIGHT = 14;
	private static final int MARKER_INSET = 3;

	private static final Map<ContainerKind, ItemStack> KIND_ICONS = new EnumMap<>(ContainerKind.class);

	static {
		KIND_ICONS.put(ContainerKind.CHEST, new ItemStack(Items.CHEST));
		KIND_ICONS.put(ContainerKind.BARREL, new ItemStack(Items.BARREL));
		KIND_ICONS.put(ContainerKind.SHULKER_BOX, new ItemStack(Items.SHULKER_BOX));
		KIND_ICONS.put(ContainerKind.OTHER, new ItemStack(Items.CHEST));
	}

	public static void render(GuiGraphicsExtractor context, Minecraft client, Hit hit,
			int x, int y, int width, int mouseX, int mouseY) {
		Font font = client.font;
		boolean hovered = contains(mouseX, mouseY, x, y, width);
		context.fill(x, y, x + width, y + RadarTheme.SUB_ROW_HEIGHT, RadarTheme.ROW_EXPANDED);
		if (hovered) {
			int inset = Shape.rounded() ? MARKER_INSET : 0;
			Shape.fill(context, x, y, x + width, y + RadarTheme.SUB_ROW_HEIGHT, Theme.ROW_HOVER);
			Shape.select(context, x, y, x + width, y + RadarTheme.SUB_ROW_HEIGHT, x + inset);
		}

		context.item(icon(hit.container().kind()), x + ICON_X, y + 1);

		boolean stale = RadarScope.isStale(hit.container());
		int detailColor = stale ? Theme.TEXT_STALE : Theme.TEXT_SECONDARY;
		int titleColor = stale ? Theme.TEXT_STALE : Theme.TEXT_PRIMARY;

		int right = x + width;
		if (ChestOpener.canOpen(client, hit.container().key())) {
			int buttonX = right - OPEN_WIDTH;
			int buttonY = y + (RadarTheme.SUB_ROW_HEIGHT - OPEN_HEIGHT) / 2;
			drawOpenButton(context, font, buttonX, buttonY,
				isOnOpenButton(client, hit, mouseX, mouseY, x, y, width));
			right = buttonX - Theme.GUTTER;
		}

		String where = (int) hit.distance() + "m " + Bearing.toward(client, hit.container().key());
		int whereWidth = font.width(where);
		context.text(font, where, right - whereWidth, y + TEXT_BASELINE,
			stale ? Theme.TEXT_STALE : RadarTheme.PATINA, false);
		right -= whereWidth + Theme.GUTTER;

		String count = "x" + hit.totalCount();
		int countWidth = font.width(count);
		context.text(font, count, right - countWidth, y + TEXT_BASELINE,
			stale ? Theme.TEXT_STALE : Theme.ACCENT, false);
		right -= countWidth + Theme.GUTTER;

		if (RadarState.config().showLastSeen) {
			String age = ageLabel(hit.container());
			int ageWidth = font.width(age);
			if (ageWidth < right - x - Theme.ICON * 3) {
				context.text(font, age, right - ageWidth, y + TEXT_BASELINE, detailColor, false);
				right -= ageWidth + Theme.GUTTER;
			}
		}

		String dimension = foreignDimension(client, hit.container().key());
		if (!dimension.isEmpty()) {
			int dimensionWidth = font.width(dimension);
			if (dimensionWidth < right - x - Theme.ICON * 2) {
				context.text(font, dimension, right - dimensionWidth, y + TEXT_BASELINE,
					stale ? Theme.TEXT_STALE : RadarTheme.PATINA, false);
				right -= dimensionWidth + Theme.GUTTER;
			}
		}

		int titleX = x + ICON_X + Theme.ICON + 4;
		int titleWidth = Math.max(0, right - titleX);
		context.text(font, Fit.text(font, hit.container().title(), titleWidth), titleX,
			y + TEXT_BASELINE, titleColor, false);
	}

	private static String ageLabel(fr.imero.chestradar.data.ContainerRecord record) {
		Freshness.Age age = Freshness.between(record.lastSeenEpochMillis(), System.currentTimeMillis());
		return Component.translatable(Freshness.translationKey(age.unit()), age.amount()).getString();
	}

	private static String foreignDimension(Minecraft client, ContainerKey key) {
		if (client.level == null) {
			return "";
		}
		String current = client.level.dimension().identifier().toString();
		return current.equals(key.dimensionId()) ? "" : Dimensions.shortName(key.dimensionId());
	}

	public static boolean contains(double mouseX, double mouseY, int x, int y, int width) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + RadarTheme.SUB_ROW_HEIGHT;
	}

	public static boolean isOnOpenButton(Minecraft client, Hit hit, double mouseX, double mouseY,
			int x, int y, int width) {
		if (!ChestOpener.canOpen(client, hit.container().key())) {
			return false;
		}
		int buttonX = x + width - OPEN_WIDTH;
		int buttonY = y + (RadarTheme.SUB_ROW_HEIGHT - OPEN_HEIGHT) / 2;
		return mouseX >= buttonX && mouseX < buttonX + OPEN_WIDTH
			&& mouseY >= buttonY && mouseY < buttonY + OPEN_HEIGHT;
	}

	private static void drawOpenButton(GuiGraphicsExtractor context, Font font, int x, int y, boolean hovered) {
		Shape.fill(context, x, y, x + OPEN_WIDTH, y + OPEN_HEIGHT, hovered ? Theme.SELECTED : Theme.SURFACE_HOVER);
		int edge = hovered ? Theme.ACCENT : Theme.mix(Theme.ACCENT, Theme.PANEL, 0.55);
		Shape.ring(context, x, y, x + OPEN_WIDTH, y + OPEN_HEIGHT, edge);

		Component label = Component.translatable("chestradar.screen.open");
		context.text(font, label, x + (OPEN_WIDTH - font.width(label)) / 2, y + 3, Theme.ACCENT, false);
	}

	private static ItemStack icon(ContainerKind kind) {
		return KIND_ICONS.getOrDefault(kind, KIND_ICONS.get(ContainerKind.OTHER));
	}
}
