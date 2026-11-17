package fr.imero.chestradar.client.render;

import fr.imero.chestradar.ChestRadar;
import fr.imero.chestradar.client.Bearing;
import fr.imero.chestradar.client.RadarRoute;
import fr.imero.chestradar.client.RadarScope;
import fr.imero.chestradar.client.RadarState;
import fr.imero.chestradar.client.RadarTarget;
import fr.imero.chestradar.client.capture.ItemIdentityRenderer;
import fr.imero.chestradar.client.integration.JadeCompat;
import fr.imero.chestradar.client.screen.Theme;
import fr.imero.chestradar.client.screen.RadarTheme;
import fr.imero.chestradar.data.ContainerKey;
import fr.imero.chestradar.data.ContainerRecord;
import fr.imero.chestradar.data.Dimensions;
import fr.imero.chestradar.search.CompactCount;
import fr.imero.chestradar.search.ContainerContents;
import fr.imero.chestradar.search.Freshness;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RadarHud {
	private static final Identifier LOOK_AT_ID = Identifier.of(ChestRadar.MOD_ID, "look_at_preview");
	private static final Identifier COMPASS_ID = Identifier.of(ChestRadar.MOD_ID, "compass");
	private static final Identifier ROUTE_ID = Identifier.of(ChestRadar.MOD_ID, "route");

	private static final int MARGIN = 6;
	private static final int PADDING = 4;
	private static final int LINE = 11;
	private static final int PANEL_MIN_WIDTH = 120;
	private static final int PANEL_MAX_WIDTH = 260;

	private static ContainerKey memoKey;
	private static ContainerRecord memoRecord;

	private RadarHud() {
	}

	public static void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, LOOK_AT_ID,
			(context, tickCounter) -> guarded(() -> renderLookAt(context)));
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, COMPASS_ID,
			(context, tickCounter) -> guarded(() -> renderCompass(context)));
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, ROUTE_ID,
			(context, tickCounter) -> guarded(() -> renderRoute(context)));
	}

	private static void guarded(Runnable body) {
		try {
			body.run();
		} catch (Exception e) {
			ChestRadar.LOGGER.warn("[chestradar] rendu du HUD impossible", e);
		}
	}

	public static void invalidate() {
		memoKey = null;
		memoRecord = null;
	}

	private static void renderLookAt(DrawContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!RadarState.config().lookAtPreview || JadeCompat.handlesLookAtPreview()
			|| client == null || client.world == null
			|| client.player == null || client.currentScreen != null) {
			return;
		}
		ContainerRecord record = lookedAt(client);
		if (record == null) {
			return;
		}
		ContainerContents.Preview preview = ContainerContents.preview(record,
			RadarState.index().pool(), RadarState.config().lookAtPreviewRows);

		TextRenderer font = client.textRenderer;
		String title = record.title();
		String subtitle = subtitle(record).getString();

		int rows = preview.shown().size() + (preview.hiddenEntries() > 0 ? 1 : 0);
		int height = PADDING * 2 + LINE + LINE + Math.max(rows, 1) * LINE;
		int width = PANEL_MIN_WIDTH;
		width = Math.max(width, font.getWidth(title) + PADDING * 2);
		width = Math.max(width, font.getWidth(subtitle) + PADDING * 2);
		for (ContainerContents.Entry entry : preview.shown()) {
			width = Math.max(width, PADDING * 2 + Theme.ICON + 4
				+ font.getWidth(entry.identity().displayName()) + 8 + font.getWidth(countLabel(entry.count())));
		}
		width = Math.min(width, PANEL_MAX_WIDTH);

		int x = MARGIN;
		int y = MARGIN;
		panel(context, x, y, width, height);

		int textWidth = width - PADDING * 2;
		context.drawText(font, font.trimToWidth(title, textWidth), x + PADDING, y + PADDING,
			Theme.TEXT_PRIMARY, false);
		context.drawText(font, font.trimToWidth(subtitle, textWidth), x + PADDING, y + PADDING + LINE,
			RadarScope.isStale(record) ? Theme.TEXT_STALE : Theme.TEXT_SECONDARY, false);

		int rowY = y + PADDING + LINE * 2;
		for (ContainerContents.Entry entry : preview.shown()) {
			drawEntry(context, client, font, entry, x + PADDING, rowY, x + width - PADDING);
			rowY += LINE;
		}
		if (preview.hiddenEntries() > 0) {
			Text more = Text.translatable("chestradar.hud.more", preview.hiddenEntries(),
				CompactCount.format(preview.hiddenCount()));
			context.drawText(font, more, x + PADDING, rowY + 2, Theme.TEXT_SECONDARY, false);
		}
		if (preview.isEmpty()) {
			context.drawText(font, Text.translatable("chestradar.hud.empty_container"),
				x + PADDING, rowY, Theme.TEXT_SECONDARY, false);
		}
	}

	private static void drawEntry(DrawContext context, MinecraftClient client, TextRenderer font,
			ContainerContents.Entry entry, int x, int y, int right) {
		try {
			context.drawItem(ItemIdentityRenderer.toStack(entry.identity(), client), x, y - 4);
		} catch (Exception e) {
			context.fill(x + 3, y - 1, x + Theme.ICON - 3, y + 5, Theme.BORDER);
		}
		String count = countLabel(entry.count());
		int countWidth = font.getWidth(count);
		context.drawText(font, count, right - countWidth, y, Theme.ACCENT, false);
		context.drawText(font, font.trimToWidth(entry.identity().displayName(),
			right - countWidth - 8 - (x + Theme.ICON + 4)), x + Theme.ICON + 4, y,
			Theme.TEXT_PRIMARY, false);
	}

	private static String countLabel(int count) {
		return "x" + String.format(Locale.ROOT, "%,d", count);
	}

	private static Text subtitle(ContainerRecord record) {
		Text kind = Text.translatable("chestradar.kind." + record.kind().name());
		if (!RadarState.config().showLastSeen) {
			return kind;
		}
		return kind.copy().append(Text.literal("  ·  ")).append(ageText(record));
	}

	public static Text ageText(ContainerRecord record) {
		Freshness.Age age = Freshness.between(record.lastSeenEpochMillis(), System.currentTimeMillis());
		return Text.translatable(Freshness.translationKey(age.unit()), age.amount());
	}

	private static ContainerRecord lookedAt(MinecraftClient client) {
		HitResult target = client.crosshairTarget;
		if (!(target instanceof BlockHitResult block) || target.getType() != HitResult.Type.BLOCK) {
			return null;
		}
		BlockPos pos = block.getBlockPos();
		ContainerKey key = new ContainerKey(client.world.getRegistryKey().getValue().toString(),
			pos.getX(), pos.getY(), pos.getZ());
		if (!key.equals(memoKey)) {
			memoKey = key;
			memoRecord = RadarState.index().at(key);
		}
		return memoRecord;
	}

	private static void renderCompass(DrawContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!RadarState.config().compassEnabled || !RadarTarget.isActive()
			|| client == null || client.player == null || client.world == null
			|| client.currentScreen != null) {
			return;
		}
		ContainerKey key = RadarTarget.key();
		String dimensionId = client.world.getRegistryKey().getValue().toString();
		var pos = client.player.getEntityPos();
		int distance = (int) Dimensions.distance(key, dimensionId, pos.x, pos.y, pos.z);

		List<Segment> line = new ArrayList<>();
		line.add(new Segment(Bearing.toward(client, key) + "  ", RadarTheme.PATINA));
		if (!RadarTarget.label().isEmpty()) {
			line.add(new Segment(RadarTarget.label(), Theme.TEXT_PRIMARY));
			if (RadarTarget.quantity() > 0) {
				line.add(new Segment(" x" + CompactCount.format(RadarTarget.quantity()), Theme.ACCENT));
			}
			line.add(new Segment("  ·  ", Theme.TEXT_SECONDARY));
		}
		line.add(new Segment(distance + "m", RadarTheme.PATINA));
		if (!dimensionId.equals(key.dimensionId())) {
			line.add(new Segment("  ·  ", Theme.TEXT_SECONDARY));
			line.add(new Segment(Dimensions.shortName(key.dimensionId()), RadarTheme.PATINA));
		}

		TextRenderer font = client.textRenderer;
		int width = width(font, line) + PADDING * 2;
		int x = (context.getScaledWindowWidth() - width) / 2;
		int y = MARGIN;
		panel(context, x, y, width, LINE + PADDING * 2 - 2);
		draw(context, font, line, x + PADDING, y + PADDING);
	}

	private record Segment(String text, int color) {
	}

	private static int width(TextRenderer font, List<Segment> segments) {
		int total = 0;
		for (Segment segment : segments) {
			total += font.getWidth(segment.text());
		}
		return total;
	}

	private static void draw(DrawContext context, TextRenderer font, List<Segment> segments,
			int x, int y) {
		int cursor = x;
		for (Segment segment : segments) {
			context.drawText(font, segment.text(), cursor, y, segment.color(), false);
			cursor += font.getWidth(segment.text());
		}
	}

	private static void renderRoute(DrawContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!RadarState.config().routeEnabled || !RadarRoute.isActive()
			|| client == null || client.player == null || client.world == null
			|| client.currentScreen != null) {
			return;
		}
		var stop = RadarRoute.currentStop();
		if (stop == null) {
			return;
		}
		boolean compassShown = RadarState.config().compassEnabled && RadarTarget.isActive();

		String head = RadarRoute.currentNumber() + "/" + RadarRoute.size() + "  ·  " + stop.title();
		if (!compassShown) {
			String dimensionId = client.world.getRegistryKey().getValue().toString();
			var pos = client.player.getEntityPos();
			head = Bearing.toward(client, stop.container()) + "  "
				+ (int) Dimensions.distance(stop.container(), dimensionId, pos.x, pos.y, pos.z) + "m  ·  "
				+ head;
		}
		String takes = RadarRoute.takesLabel(stop);

		TextRenderer font = client.textRenderer;
		int width = Math.max(font.getWidth(head), font.getWidth(takes)) + PADDING * 2;
		int height = PADDING * 2 + LINE + (takes.isEmpty() ? 0 : LINE) - 2;
		int x = (context.getScaledWindowWidth() - width) / 2;
		int y = MARGIN + (compassShown ? LINE + PADDING * 2 : 0);

		panel(context, x, y, width, height);
		context.drawText(font, head, x + PADDING, y + PADDING, Theme.ACCENT, false);
		if (!takes.isEmpty()) {
			context.drawText(font, takes, x + PADDING, y + PADDING + LINE, Theme.TEXT_PRIMARY, false);
		}
	}

	private static void panel(DrawContext context, int x, int y, int width, int height) {
		fr.imero.chestradar.client.screen.Panel.frame(context, x, y, width, height);
	}
}
