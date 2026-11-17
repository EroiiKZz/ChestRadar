package fr.imero.chestradar.client.render;

import fr.imero.chestradar.ChestRadar;
import fr.imero.chestradar.client.Bearing;
import fr.imero.chestradar.client.RadarRoute;
import fr.imero.chestradar.client.RadarScope;
import fr.imero.chestradar.client.RadarState;
import fr.imero.chestradar.client.RadarTarget;
import fr.imero.chestradar.client.capture.ItemIdentityRenderer;
import fr.imero.chestradar.client.integration.JadeCompat;
import fr.imero.chestradar.client.screen.Fit;
import fr.imero.chestradar.client.screen.Shape;
import fr.imero.chestradar.client.screen.Theme;
import fr.imero.chestradar.data.ContainerKey;
import fr.imero.chestradar.data.ContainerRecord;
import fr.imero.chestradar.data.Dimensions;
import fr.imero.chestradar.search.CompactCount;
import fr.imero.chestradar.search.ContainerContents;
import fr.imero.chestradar.search.Freshness;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RadarHud {
	private static final Identifier LOOK_AT_ID = Identifier.fromNamespaceAndPath(ChestRadar.MOD_ID, "look_at_preview");
	private static final Identifier COMPASS_ID = Identifier.fromNamespaceAndPath(ChestRadar.MOD_ID, "compass");
	private static final Identifier ROUTE_ID = Identifier.fromNamespaceAndPath(ChestRadar.MOD_ID, "route");

	private static final int PANEL_MIN_WIDTH = 150;
	private static final int PANEL_MAX_WIDTH = 260;
	private static final int ROW_ICON = 10;
	private static final int ROUTE_LIFT = 60;

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

	private static void renderLookAt(GuiGraphicsExtractor context) {
		Minecraft client = Minecraft.getInstance();
		if (!RadarState.config().lookAtPreview || JadeCompat.handlesLookAtPreview()
			|| client == null || client.level == null
			|| client.player == null || client.gui.screen() != null) {
			return;
		}
		ContainerRecord record = lookedAt(client);
		if (record == null) {
			return;
		}
		ContainerContents.Preview preview = ContainerContents.preview(record,
			RadarState.index().pool(), RadarState.config().lookAtPreviewRows);

		Font font = client.font;
		String kind = Component.translatable("chestradar.kind." + record.kind().name()).getString();
		String name = record.title();
		Component age = RadarState.config().showLastSeen ? ageText(record) : Component.empty();
		int ageWidth = font.width(age);

		int rows = preview.shown().size() + (preview.hiddenEntries() > 0 || preview.isEmpty() ? 1 : 0);
		int width = PANEL_MIN_WIDTH;
		width = Math.max(width, font.width(name) + ageWidth + Theme.CARD_PAD * 2 + 10);
		for (ContainerContents.Entry entry : preview.shown()) {
			width = Math.max(width, Theme.CARD_PAD * 2 + ROW_ICON + 5 + font.width(entry.identity().displayName()) + 10
				+ font.width(countLabel(entry.count())));
		}
		width = Math.min(width, PANEL_MAX_WIDTH);
		int height = Theme.CARD_PAD + 12 + 5 + rows * (Theme.LINE + Theme.SPACE_1) + Theme.CARD_PAD - 2;

		int x = context.guiWidth() / 2 + 15;
		int y = Math.max(Theme.SPACE_3, context.guiHeight() / 2 - 35);
		Shape.card(context, x, y, x + width, y + height);

		String head = name.isBlank() ? kind : name;
		context.text(font, Fit.text(font, head, width - Theme.CARD_PAD * 2 - ageWidth - 8),
			x + Theme.CARD_PAD, y + Theme.CARD_PAD, Theme.TEXT_PRIMARY, false);
		context.text(font, age, x + width - Theme.CARD_PAD - ageWidth, y + Theme.CARD_PAD + 1,
			RadarScope.isStale(record) ? Theme.TEXT_STALE : Theme.TEXT_SECONDARY, false);
		int ruleY = y + Theme.CARD_PAD + 12;
		context.fill(x + Theme.CARD_PAD, ruleY, x + width - Theme.CARD_PAD, ruleY + 1, Theme.HAIRLINE);

		int rowY = ruleY + 5;
		for (ContainerContents.Entry entry : preview.shown()) {
			drawEntry(context, client, font, entry, x + Theme.CARD_PAD, rowY, x + width - Theme.CARD_PAD);
			rowY += (Theme.LINE + Theme.SPACE_1);
		}
		if (preview.hiddenEntries() > 0) {
			context.text(font, Component.translatable("chestradar.hud.more", preview.hiddenEntries(),
				CompactCount.format(preview.hiddenCount())), x + Theme.CARD_PAD, rowY + 2, Theme.TEXT_SECONDARY, false);
		}
		if (preview.isEmpty()) {
			context.text(font, Component.translatable("chestradar.hud.empty_container"),
				x + Theme.CARD_PAD, rowY + 2, Theme.TEXT_SECONDARY, false);
		}
	}

	private static void drawEntry(GuiGraphicsExtractor context, Minecraft client, Font font,
			ContainerContents.Entry entry, int x, int y, int right) {
		try {
			context.pose().pushMatrix();
			context.pose().translate(x, y);
			context.pose().scale(ROW_ICON / 16.0f, ROW_ICON / 16.0f);
			context.item(ItemIdentityRenderer.toStack(entry.identity(), client), 0, 0);
			context.pose().popMatrix();
		} catch (Exception e) {
			context.fill(x + 2, y + 3, x + ROW_ICON - 2, y + ROW_ICON - 3, Theme.BORDER);
		}
		Component count = Component.literal(countLabel(entry.count()));
		int countWidth = font.width(count);
		int textY = y + (ROW_ICON - 8) / 2 + 1;
		context.text(font, count, right - countWidth, textY, Theme.TEXT_PRIMARY, false);
		context.text(font, Fit.text(font, entry.identity().displayName(),
			right - countWidth - 8 - (x + ROW_ICON + 5)), x + ROW_ICON + 5, textY, Theme.TEXT_PRIMARY, false);
	}

	private static String countLabel(int count) {
		return String.format(Locale.FRANCE, "%,d", count).replace('\u202f', ' ').replace('\u00a0', ' ');
	}

	public static Component ageText(ContainerRecord record) {
		Freshness.Age age = Freshness.between(record.lastSeenEpochMillis(), System.currentTimeMillis());
		return Component.translatable(Freshness.translationKey(age.unit()), age.amount());
	}

	private static ContainerRecord lookedAt(Minecraft client) {
		HitResult target = client.hitResult;
		if (!(target instanceof BlockHitResult block) || target.getType() != HitResult.Type.BLOCK) {
			return null;
		}
		BlockPos pos = block.getBlockPos();
		ContainerKey key = new ContainerKey(client.level.dimension().identifier().toString(),
			pos.getX(), pos.getY(), pos.getZ());
		if (!key.equals(memoKey)) {
			memoKey = key;
			memoRecord = RadarState.index().at(key);
		}
		return memoRecord;
	}

	private static void renderCompass(GuiGraphicsExtractor context) {
		Minecraft client = Minecraft.getInstance();
		if (!RadarState.config().compassEnabled || !RadarTarget.isActive()
			|| client == null || client.player == null || client.level == null
			|| client.gui.screen() != null) {
			return;
		}
		ContainerKey key = RadarTarget.key();
		String dimensionId = client.level.dimension().identifier().toString();
		var pos = client.player.position();
		int distance = (int) Dimensions.distance(key, dimensionId, pos.x, pos.y, pos.z);

		List<Segment> line = new ArrayList<>();
		line.add(new Segment(Bearing.toward(client, key) + "  ", Theme.ACCENT));
		if (!RadarTarget.label().isEmpty()) {
			line.add(new Segment(RadarTarget.label(), Theme.TEXT_PRIMARY));
			if (RadarTarget.quantity() > 0) {
				line.add(new Segment(" \u00d7" + CompactCount.format(RadarTarget.quantity()), Theme.TEXT_PRIMARY));
			}
			line.add(new Segment("  \u00b7  ", Theme.TEXT_SECONDARY));
		}
		line.add(new Segment(distance + " " + Component.translatable("chestradar.unit.blocks").getString(),
			Theme.TEXT_SECONDARY));
		if (!dimensionId.equals(key.dimensionId())) {
			line.add(new Segment("  \u00b7  ", Theme.TEXT_SECONDARY));
			line.add(new Segment(Dimensions.shortName(key.dimensionId()), Theme.TEXT_SECONDARY));
		}

		Font font = client.font;
		int width = width(font, line) + 20;
		int x = (context.guiWidth() - width) / 2;
		int y = Theme.SPACE_3;
		pill(context, x, y, x + width, y + 16);
		draw(context, font, line, x + 10, y + 4);
	}

	private record Segment(String text, int color) {
	}

	private static int width(Font font, List<Segment> segments) {
		int total = 0;
		for (Segment segment : segments) {
			total += font.width(segment.text());
		}
		return total;
	}

	private static void draw(GuiGraphicsExtractor context, Font font, List<Segment> segments,
			int x, int y) {
		int cursor = x;
		for (Segment segment : segments) {
			context.text(font, segment.text(), cursor, y, segment.color(), false);
			cursor += font.width(segment.text());
		}
	}

	private static void renderRoute(GuiGraphicsExtractor context) {
		Minecraft client = Minecraft.getInstance();
		if (!RadarState.config().routeEnabled || !RadarRoute.isActive()
			|| client == null || client.player == null || client.level == null
			|| client.gui.screen() != null) {
			return;
		}
		var stop = RadarRoute.currentStop();
		if (stop == null) {
			return;
		}
		Font font = client.font;
		String dimensionId = client.level.dimension().identifier().toString();
		var pos = client.player.position();
		Component step = Component.translatable("chestradar.route.step", RadarRoute.currentNumber(),
			RadarRoute.size());
		Component far = Component.literal((int) Dimensions.distance(stop.container(), dimensionId, pos.x, pos.y, pos.z) + " "
			+ Component.translatable("chestradar.unit.blocks").getString());
		String takes = RadarRoute.takesLabel(stop);
		String body = takes.isEmpty() ? stop.title() : stop.title() + " : " + takes;

		int stepWidth = font.width(step) + 12;
		int farWidth = font.width(far);
		int width = Math.min(context.guiWidth() - Theme.SPACE_3 * 2,
			Math.max(300, 8 + stepWidth + 7 + font.width(body) + 10 + farWidth + 8));
		int height = 22;
		int x = (context.guiWidth() - width) / 2;
		int y = context.guiHeight() - ROUTE_LIFT - height;
		Shape.card(context, x, y, x + width, y + height);
		Shape.pill(context, x + 6, y + 5, x + 6 + stepWidth, y + 17, Theme.ACCENT);
		context.text(font, step, x + 12, y + 7, Theme.ON_ACCENT, false);
		context.text(font, far, x + width - 8 - farWidth, y + 7, Theme.TEXT_SECONDARY, false);
		int bodyX = x + 6 + stepWidth + 7;
		context.text(font, Fit.text(font, body, x + width - 18 - farWidth - bodyX), bodyX, y + 7,
			Theme.TEXT_PRIMARY, false);
	}

	private static void pill(GuiGraphicsExtractor context, int x0, int y0, int x1, int y1) {
		if (Theme.DEPTH == fr.imero.chestradar.theme.AeternumTheme.Depth.STAGED) {
			Shape.shadow(context, x0, y0, x1, y1);
		}
		Shape.pill(context, x0, y0, x1, y1, Theme.CARD);
	}
}
