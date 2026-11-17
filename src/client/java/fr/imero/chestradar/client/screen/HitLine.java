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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.EnumMap;
import java.util.Map;

public final class HitLine {
	private HitLine() {
	}

	private static final int ICON_X = 2;
	private static final int TEXT_BASELINE = 5;
	private static final int OPEN_WIDTH = 46;
	private static final int OPEN_HEIGHT = 14;

	private static final Map<ContainerKind, ItemStack> KIND_ICONS = new EnumMap<>(ContainerKind.class);

	static {
		KIND_ICONS.put(ContainerKind.CHEST, new ItemStack(Items.CHEST));
		KIND_ICONS.put(ContainerKind.BARREL, new ItemStack(Items.BARREL));
		KIND_ICONS.put(ContainerKind.SHULKER_BOX, new ItemStack(Items.SHULKER_BOX));
		KIND_ICONS.put(ContainerKind.OTHER, new ItemStack(Items.CHEST));
	}

	public static void render(DrawContext context, MinecraftClient client, Hit hit,
			int x, int y, int width, int mouseX, int mouseY) {
		TextRenderer font = client.textRenderer;
		boolean hovered = contains(mouseX, mouseY, x, y, width);
		context.fill(x, y, x + width, y + RadarTheme.SUB_ROW_HEIGHT, hovered ? Theme.ROW_HOVER : RadarTheme.ROW_EXPANDED);
		if (hovered) {
			context.fill(x, y, x + 2, y + RadarTheme.SUB_ROW_HEIGHT, Theme.ACCENT);
		}

		context.drawItem(icon(hit.container().kind()), x + ICON_X, y + 1);

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
		int whereWidth = font.getWidth(where);
		context.drawText(font, where, right - whereWidth, y + TEXT_BASELINE,
			stale ? Theme.TEXT_STALE : RadarTheme.PATINA, false);
		right -= whereWidth + Theme.GUTTER;

		String count = "x" + hit.totalCount();
		int countWidth = font.getWidth(count);
		context.drawText(font, count, right - countWidth, y + TEXT_BASELINE,
			stale ? Theme.TEXT_STALE : Theme.ACCENT, false);
		right -= countWidth + Theme.GUTTER;

		if (RadarState.config().showLastSeen) {
			String age = ageLabel(hit.container());
			int ageWidth = font.getWidth(age);
			if (ageWidth < right - x - Theme.ICON * 3) {
				context.drawText(font, age, right - ageWidth, y + TEXT_BASELINE, detailColor, false);
				right -= ageWidth + Theme.GUTTER;
			}
		}

		String dimension = foreignDimension(client, hit.container().key());
		if (!dimension.isEmpty()) {
			int dimensionWidth = font.getWidth(dimension);
			if (dimensionWidth < right - x - Theme.ICON * 2) {
				context.drawText(font, dimension, right - dimensionWidth, y + TEXT_BASELINE,
					stale ? Theme.TEXT_STALE : RadarTheme.PATINA, false);
				right -= dimensionWidth + Theme.GUTTER;
			}
		}

		int titleX = x + ICON_X + Theme.ICON + 4;
		int titleWidth = Math.max(0, right - titleX);
		context.drawText(font, font.trimToWidth(hit.container().title(), titleWidth), titleX,
			y + TEXT_BASELINE, titleColor, false);
	}

	private static String ageLabel(fr.imero.chestradar.data.ContainerRecord record) {
		Freshness.Age age = Freshness.between(record.lastSeenEpochMillis(), System.currentTimeMillis());
		return Text.translatable(Freshness.translationKey(age.unit()), age.amount()).getString();
	}

	private static String foreignDimension(MinecraftClient client, ContainerKey key) {
		if (client.world == null) {
			return "";
		}
		String current = client.world.getRegistryKey().getValue().toString();
		return current.equals(key.dimensionId()) ? "" : Dimensions.shortName(key.dimensionId());
	}

	public static boolean contains(double mouseX, double mouseY, int x, int y, int width) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + RadarTheme.SUB_ROW_HEIGHT;
	}

	public static boolean isOnOpenButton(MinecraftClient client, Hit hit, double mouseX, double mouseY,
			int x, int y, int width) {
		if (!ChestOpener.canOpen(client, hit.container().key())) {
			return false;
		}
		int buttonX = x + width - OPEN_WIDTH;
		int buttonY = y + (RadarTheme.SUB_ROW_HEIGHT - OPEN_HEIGHT) / 2;
		return mouseX >= buttonX && mouseX < buttonX + OPEN_WIDTH
			&& mouseY >= buttonY && mouseY < buttonY + OPEN_HEIGHT;
	}

	private static void drawOpenButton(DrawContext context, TextRenderer font, int x, int y, boolean hovered) {
		context.fill(x, y, x + OPEN_WIDTH, y + OPEN_HEIGHT, hovered ? RadarTheme.OPEN_BUTTON_HOVER : RadarTheme.OPEN_BUTTON);
		int edge = hovered ? Theme.ACCENT : Theme.mix(Theme.ACCENT, Theme.PANEL, 0.55);
		context.fill(x, y, x + OPEN_WIDTH, y + 1, edge);
		context.fill(x, y + OPEN_HEIGHT - 1, x + OPEN_WIDTH, y + OPEN_HEIGHT, edge);
		context.fill(x, y, x + 1, y + OPEN_HEIGHT, edge);
		context.fill(x + OPEN_WIDTH - 1, y, x + OPEN_WIDTH, y + OPEN_HEIGHT, edge);

		Text label = Text.translatable("chestradar.screen.open");
		context.drawText(font, label, x + (OPEN_WIDTH - font.getWidth(label)) / 2, y + 3, Theme.ACCENT, false);
	}

	private static ItemStack icon(ContainerKind kind) {
		return KIND_ICONS.getOrDefault(kind, KIND_ICONS.get(ContainerKind.OTHER));
	}
}
