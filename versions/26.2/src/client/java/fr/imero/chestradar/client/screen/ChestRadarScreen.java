package fr.imero.chestradar.client.screen;

import fr.imero.chestradar.ChestRadar;
import fr.imero.chestradar.client.ChestRadarKeybinds;
import fr.imero.chestradar.client.ClientWorldKey;
import fr.imero.chestradar.client.PinRoute;
import fr.imero.chestradar.client.RadarActions;
import fr.imero.chestradar.client.RadarRoute;
import fr.imero.chestradar.client.RadarScope;
import fr.imero.chestradar.client.RadarState;
import fr.imero.chestradar.client.capture.IdentityRules;
import fr.imero.chestradar.client.capture.ItemIdentityFactory;
import fr.imero.chestradar.client.capture.ItemIdentityRenderer;
import fr.imero.chestradar.client.render.ChestHighlightRenderer;
import fr.imero.chestradar.config.SortMode;
import fr.imero.chestradar.config.ViewMode;
import fr.imero.chestradar.data.ContainerKey;
import fr.imero.chestradar.data.ItemIdentity;
import fr.imero.chestradar.search.AggregatedItem;
import fr.imero.chestradar.search.Hit;
import fr.imero.chestradar.search.Pin;
import fr.imero.chestradar.search.RadarQuery;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ChestRadarScreen extends Screen {

	private EditBox searchField;
	private RadarView view;
	private List<AggregatedItem> all = new ArrayList<>();
	private boolean tooltipFailureLogged;
	private String pendingSearchText;
	private int pendingSearchCursor;
	private boolean suppressNextChar;
	private boolean pinnedOnly;
	private boolean pinRowAtInit;

	private final long openedAt = System.currentTimeMillis();

	private int containerCount;

	private EmptyReason emptyReason;

	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int segmentsX;
	private int segmentsRight;
	private int controlTop;
	private int stackShift;
	private final List<int[]> pinChips = new ArrayList<>();
	private final List<String> pinChipKeys = new ArrayList<>();

	private enum EmptyReason {
		NOTHING_INDEXED(Items.CHEST, "chestradar.screen.empty", "chestradar.screen.empty_hint"),
		NO_MATCH(Items.SPYGLASS, "chestradar.screen.no_match", "chestradar.screen.no_match_hint"),
		NO_PINS(Items.NETHER_STAR, "chestradar.screen.no_pins", "chestradar.screen.no_pins_hint");

		private final net.minecraft.world.item.Item icon;
		private final String titleKey;
		private final String hintKey;

		EmptyReason(net.minecraft.world.item.Item icon, String titleKey, String hintKey) {
			this.icon = icon;
			this.titleKey = titleKey;
			this.hintKey = hintKey;
		}
	}

	public ChestRadarScreen() {
		super(Component.translatable("chestradar.screen.title"));
	}

	@Override
	protected void setInitialFocus() {
		if (searchField != null) {
			setInitialFocus(searchField);
		}
	}

	@Override
	protected void init() {
		RadarTheme.sync();
		panelWidth = Math.clamp((long) (width * RadarState.config().panelWidthFraction), Math.min(360,
			width - Theme.SPACE_5 * 2), Math.max(360, width - Theme.SPACE_5 * 2));
		panelHeight = Math.clamp((long) (height * RadarTheme.PANEL_SIZE_FRACTION), Math.min(220, height - Theme.SPACE_5 * 2),
			Math.max(220, height - Theme.SPACE_5 * 2));
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;

		int segmentsWidth = Theme.SPACE_4 * 2 * SortMode.values().length + Shape.SEGMENT_PAD * 2;
		for (SortMode mode : SortMode.values()) {
			segmentsWidth += font.width(sortLabel(mode));
		}
		int controlsWidth = segmentsWidth + buttonWidth(Component.translatable("chestradar.screen.settings"))
			+ buttonWidth(viewLabel()) + (RadarState.config().routeEnabled ? buttonWidth(routeLabel()) : 0);
		int titleRoom = panelWidth - Theme.PADDING * 2 - 2 - Type.X2.width(font, title) - Theme.SPACE_4;
		stackShift = controlsWidth > titleRoom ? Theme.LINE_2 + Theme.SPACE_3 : 0;
		controlTop = panelY + 10 + stackShift;
		int right = panelX + panelWidth - Theme.PADDING;
		int controlY = controlTop;
		right = headerButton(right, controlY, Component.translatable("chestradar.screen.settings"),
			b -> minecraft.setScreenAndShow(new ConfigScreen(this)));
		if (RadarState.config().routeEnabled) {
			right = headerButton(right, controlY, routeLabel(), b -> toggleRoute());
		}
		right = headerButton(right, controlY, viewLabel(), b -> {
			RadarState.config().viewMode = RadarState.config().viewMode.next();
			RadarState.config().save(ClientWorldKey.configFile());
			reinit();
		});
		segmentsRight = right;
		segmentsX = right - segmentsWidth;

		searchField = new FlatField(font, panelX + Theme.PADDING, searchTop(), panelWidth - Theme.PADDING * 2, Theme.CONTROL_LARGE,
			Component.translatable("chestradar.screen.search"));
		searchField.setHint(Component.translatable("chestradar.screen.search")
			.withStyle(s -> s.withColor(Theme.rgb(Theme.TEXT_SECONDARY))));
		searchField.setTooltip(Tooltip.create(Component.translatable("chestradar.screen.search_tip")));
		searchField.setResponder(text -> refresh());
		addRenderableWidget(searchField);
		setInitialFocus(searchField);
		if (pendingSearchText != null) {
			searchField.setValue(pendingSearchText);
			searchField.moveCursorTo(pendingSearchCursor, false);
			pendingSearchText = null;
		}

		pinRowAtInit = pinRowShown();
		view = RadarState.config().viewMode == ViewMode.GRID
			? new RadarGrid(columnLeft(), gridTop(), columnRight() - columnLeft(), gridBottom() - gridTop())
			: new RadarList(columnLeft(), gridTop(), columnRight() - columnLeft(), gridBottom() - gridTop());
		reload();
	}

	private int headerButton(int right, int y, Component label, java.util.function.Consumer<FlatButton> action) {
		int buttonWidth = font.width(label) + 20;
		addRenderableWidget(new FlatButton(right - buttonWidth, y, buttonWidth, Theme.CONTROL, label, action));
		return right - buttonWidth - Theme.SPACE_2;
	}

	private int buttonWidth(Component label) {
		return font.width(label) + 20 + Theme.SPACE_2;
	}

	private int searchTop() {
		return panelY + 36 + stackShift;
	}

	private int headerRuleY() {
		return searchTop() + Theme.CONTROL_LARGE + 10;
	}

	private int bodyTop() {
		return headerRuleY() + 11;
	}

	private int bodyBottom() {
		return panelY + panelHeight - Theme.PADDING;
	}

	private int columnLeft() {
		return panelX + Theme.PADDING;
	}

	private int columnRight() {
		return panelX + panelWidth - Theme.PADDING;
	}

	private boolean pinRowShown() {
		return RadarState.config().pinsEnabled && !RadarState.config().pins.isEmpty();
	}

	private int gridTop() {
		return bodyTop() + (pinRowShown() ? (Theme.LINE + Theme.SPACE_2) + 8 : 0);
	}

	private int footerY() {
		return bodyBottom() - 8;
	}

	private int gridBottom() {
		return footerY() - 8;
	}

	private Component sortLabel(SortMode mode) {
		return Component.translatable("chestradar.screen.sort." + mode.name());
	}

	private Component viewLabel() {
		return Component.translatable("chestradar.screen.view." + RadarState.config().viewMode.name());
	}

	private Component routeLabel() {
		return Component.translatable(RadarRoute.isActive()
			? "chestradar.screen.route_stop" : "chestradar.screen.route");
	}

	private void toggleRoute() {
		if (RadarRoute.isActive()) {
			RadarRoute.cancel(minecraft);
			reinit();
			return;
		}
		if (minecraft != null) {
			minecraft.setScreenAndShow(null);
		}
		PinRoute.start(minecraft);
	}

	private void reinit() {
		if (searchField != null) {
			pendingSearchText = searchField.getValue();
			pendingSearchCursor = searchField.getCursorPosition();
		}
		AggregatedItem keep = view == null ? null : view.selectedItem();
		clearWidgets();
		init();
		if (keep != null) {
			view.select(keep.identity().fingerprint());
		}
	}

	private void reload() {
		if (minecraft == null || minecraft.player == null || minecraft.level == null) {
			return;
		}
		var pos = minecraft.player.position();
		String dimensionId = minecraft.level.dimension().identifier().toString();
		all = RadarQuery.aggregate(RadarState.index(), dimensionId, pos.x, pos.y, pos.z,
			RadarState.config().searchRadius, RadarScope.allDimensions(), RadarScope.accept());
		Set<ContainerKey> containers = new HashSet<>();
		for (AggregatedItem item : all) {
			for (Hit hit : item.hits()) {
				containers.add(hit.container().key());
			}
		}
		containerCount = containers.size();
		refresh();
	}

	private void refresh() {
		List<AggregatedItem> filtered = RadarQuery.filter(all,
			RadarScope.parse(searchField == null ? "" : searchField.getValue()),
			RadarScope.context());
		Set<String> pinned = RadarScope.pinned();
		boolean matchedBeforePins = !filtered.isEmpty();
		if (pinnedOnly) {
			filtered = filtered.stream()
				.filter(item -> pinned.contains(item.identity().fingerprint()))
				.collect(Collectors.toList());
		}
		List<AggregatedItem> shown = RadarQuery.sort(filtered, RadarState.config().sortMode, pinned);
		emptyReason = shown.isEmpty() ? emptyReason(matchedBeforePins) : null;
		view.setItems(shown);
		view.resetScroll();
	}

	private void setSort(SortMode mode) {
		if (RadarState.config().sortMode == mode) {
			return;
		}
		RadarState.config().sortMode = mode;
		RadarState.config().save(ClientWorldKey.configFile());
		refresh();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		RadarTheme.sync();
		if (pinRowShown() != pinRowAtInit) {
			reinit();
		}
		Panel.backdrop(context, width, height);
		Shape.window(context, panelX, panelY, panelX + panelWidth, panelY + panelHeight);

		AggregatedItem hovered = view.hoveredItem(mouseX, mouseY);
		drawHeader(context, mouseX, mouseY);
		context.fill(panelX + 1, headerRuleY(), panelX + panelWidth - 1, headerRuleY() + 1, Theme.HAIRLINE);
		if (pinRowShown()) {
			drawPins(context, mouseX, mouseY);
		}
		drawFooter(context);

		super.extractRenderState(context, mouseX, mouseY, delta);
		drawSearchKeys(context);

		view.render(context, mouseX, mouseY);
		if (emptyReason != null) {
			EmptyState.render(context, font, columnLeft(), gridTop(), columnRight() - columnLeft(),
				gridBottom() - gridTop(), new ItemStack(emptyReason.icon),
				Component.translatable(emptyReason.titleKey), Component.translatable(emptyReason.hintKey));
		}
		if (hovered != null && minecraft != null) {
			renderTooltip(context, hovered, mouseX, mouseY);
		}
	}

	private void drawHeader(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		int titleX = panelX + Theme.PADDING + 2;
		int titleY = panelY + 12;
		int titleWidth = drawTitle(context, titleX, titleY);
		int statX = titleX + titleWidth + 10;
		int room = (stackShift > 0 ? panelX + panelWidth - Theme.PADDING : segmentsX - 10) - statX;
		Component stat = stat(true);
		if (font.width(stat) > room) {
			stat = stat(false);
		}
		if (font.width(stat) <= room) {
			context.text(font, stat, statX, titleY + 5, Theme.TEXT_SECONDARY, false);
		}

		SortMode[] modes = SortMode.values();
		int top = controlTop;
		int hot = mouseY >= top && mouseY < top + Theme.CONTROL ? segmentAt(mouseX) : -1;
		Shape.well(context, segmentsX, top, segmentsRight, top + Theme.CONTROL);
		int left = segmentsX + Shape.SEGMENT_PAD;
		for (int i = 0; i < modes.length; i++) {
			Component label = sortLabel(modes[i]);
			int segmentWidth = font.width(label) + Theme.SPACE_4 * 2;
			boolean on = RadarState.config().sortMode == modes[i];
			Shape.chip(context, left, top + Shape.SEGMENT_PAD, left + segmentWidth, top + Theme.CONTROL - Shape.SEGMENT_PAD, on,
				hot == i);
			context.text(font, label, left + Theme.SPACE_4, top + (Theme.CONTROL - 8) / 2,
				on ? Theme.ON_ACCENT : Theme.TEXT_PRIMARY, false);
			left += segmentWidth;
		}
	}

	private int segmentAt(double mouseX) {
		int left = segmentsX + Shape.SEGMENT_PAD;
		SortMode[] modes = SortMode.values();
		for (int i = 0; i < modes.length; i++) {
			int segmentWidth = font.width(sortLabel(modes[i])) + Theme.SPACE_4 * 2;
			if (mouseX >= left && mouseX < left + segmentWidth) {
				return i;
			}
			left += segmentWidth;
		}
		return -1;
	}

	private int drawTitle(GuiGraphicsExtractor context, int x, int y) {
		Type.X2.draw(context, font, title, x, y, Theme.TEXT_PRIMARY);
		return Type.X2.width(font, title);
	}

	private Component stat(boolean full) {
		String dot = "  ·  ";
		StringBuilder text = new StringBuilder()
			.append(RadarDetails.number(all.size())).append(' ')
			.append(Component.translatable("chestradar.screen.stat_items").getString());
		if (full) {
			text.append(dot).append(RadarDetails.number(containerCount)).append(' ')
				.append(Component.translatable("chestradar.screen.stat_containers").getString());
		}
		text.append(dot).append(Component.translatable("chestradar.screen.stat_radius",
			RadarDetails.number((int) RadarState.config().searchRadius)).getString());
		return Component.literal(text.toString());
	}

	private void drawSearchKeys(GuiGraphicsExtractor context) {
		Component keys = Component.translatable("chestradar.screen.search_tip");
		int keysWidth = font.width(keys);
		int fieldRight = panelX + panelWidth - Theme.PADDING - 8;
		int typed = font.width(searchField.getValue().isEmpty()
			? Component.translatable("chestradar.screen.search") : Component.literal(searchField.getValue()));
		if (panelX + Theme.PADDING + 8 + typed + 24 + keysWidth > fieldRight) {
			return;
		}
		context.text(font, keys, fieldRight - keysWidth, searchTop() + (Theme.CONTROL_LARGE - 8) / 2 + 1, Theme.TEXT_SECONDARY,
			false);
	}

	private void drawPins(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		pinChips.clear();
		pinChipKeys.clear();
		int y = bodyTop();
		int x = columnLeft();
		Component label = Component.translatable("chestradar.screen.pins");
		boolean labelHot = mouseX >= x && mouseX < x + font.width(label) && mouseY >= y && mouseY < y + (Theme.LINE + Theme.SPACE_2);
		context.text(font, label, x, y + 3, pinnedOnly ? Theme.ACCENT : labelHot ? Theme.TEXT_PRIMARY
			: Theme.mix(Theme.TEXT_PRIMARY, Theme.TEXT_SECONDARY, 0.3), false);
		x += font.width(label) + 8;
		int limit = columnRight();
		for (Pin pin : RadarState.config().pins) {
			String text = pin.displayName() + (pin.hasTarget()
				? "  ·  " + Component.translatable("chestradar.screen.pin_target", RadarDetails.number(pin.target()))
					.getString() : "");
			Component chip = Component.literal(text);
			int chipWidth = font.width(chip) + 14;
			if (x + chipWidth > limit) {
				break;
			}
			boolean hot = mouseX >= x && mouseX < x + chipWidth && mouseY >= y && mouseY < y + (Theme.LINE + Theme.SPACE_2);
			Shape.pill(context, x, y, x + chipWidth, y + (Theme.LINE + Theme.SPACE_2), hot ? Theme.mix(Theme.TILE, Theme.TEXT_PRIMARY, 0.08)
				: Theme.TILE);
			context.text(font, chip, x + 7, y + 3, pin.hasTarget() ? Theme.ACCENT
				: Theme.mix(Theme.TEXT_PRIMARY, Theme.TEXT_SECONDARY, 0.3), false);
			pinChips.add(new int[] {x, x + chipWidth});
			pinChipKeys.add(pin.fingerprint());
			x += chipWidth + 6;
		}
	}

	private void drawFooter(GuiGraphicsExtractor context) {
		Component hint = Component.translatable("chestradar.screen.click_hint");
		if (font.width(hint) <= columnRight() - columnLeft()) {
			context.text(font, hint, columnLeft(), footerY(), Theme.TEXT_SECONDARY, false);
		}
	}

	private EmptyReason emptyReason(boolean matchedBeforePins) {
		if (pinnedOnly && matchedBeforePins) {
			return EmptyReason.NO_PINS;
		}
		return all.isEmpty() ? EmptyReason.NOTHING_INDEXED : EmptyReason.NO_MATCH;
	}

	private void renderTooltip(GuiGraphicsExtractor context, AggregatedItem hovered, int mouseX, int mouseY) {
		try {
			List<Component> lines = new ArrayList<>(getTooltipFromItem(minecraft,
				ItemIdentityRenderer.toStack(hovered.identity(), minecraft)));
			lines.add(Component.translatable("chestradar.screen.total", RadarDetails.number(hovered.totalCount()))
				.withStyle(ChatFormatting.GRAY));
			context.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
		} catch (Exception e) {
			if (!tooltipFailureLogged) {
				tooltipFailureLogged = true;
				ChestRadar.LOGGER.warn("[chestradar] tooltip impossible pour {} (un seul avertissement)",
					hovered.identity().fingerprint(), e);
			}
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
		int button = click.button();
		double mouseX = click.x();
		double mouseY = click.y();
		if (button == InputConstants.MOUSE_BUTTON_LEFT && mouseY >= controlTop && mouseY < controlTop + Theme.CONTROL) {
			int segment = segmentAt(mouseX);
			if (segment >= 0) {
				setSort(SortMode.values()[segment]);
				return true;
			}
		}
		if (button == InputConstants.MOUSE_BUTTON_LEFT && pinRowShown() && mouseY >= bodyTop()
			&& mouseY < bodyTop() + (Theme.LINE + Theme.SPACE_2) && clickPins(mouseX)) {
			return true;
		}
		if (button != InputConstants.MOUSE_BUTTON_LEFT && button != InputConstants.MOUSE_BUTTON_RIGHT
			&& button != InputConstants.MOUSE_BUTTON_MIDDLE) {
			return super.mouseClicked(click, doubleClick);
		}
		if (view.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		return super.mouseClicked(click, doubleClick);
	}

	private boolean clickPins(double mouseX) {
		int labelRight = columnLeft() + font.width(Component.translatable("chestradar.screen.pins"));
		if (mouseX >= columnLeft() && mouseX < labelRight) {
			pinnedOnly = !pinnedOnly;
			refresh();
			return true;
		}
		for (int i = 0; i < pinChips.size(); i++) {
			int[] span = pinChips.get(i);
			if (mouseX >= span[0] && mouseX < span[1]) {
				view.select(pinChipKeys.get(i));
				return true;
			}
		}
		return false;
	}

	private void selectItem(String fingerprint, String searchText) {
		if (searchText != null && searchField != null) {
			searchField.setValue(searchText);
		}
		view.select(fingerprint);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (view.mouseScrolled(mouseX, mouseY, vertical)) {
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		if (input.key() == GLFW.GLFW_KEY_ESCAPE && view.clearSelection()) {
			return true;
		}
		if (super.keyPressed(input)) {
			return true;
		}
		if (ChestRadarKeybinds.SEARCH.matches(input) && searchByHeldItem()) {
			suppressNextChar = true;
			return true;
		}
		return false;
	}

	@Override
	public boolean charTyped(CharacterEvent input) {
		if (suppressNextChar) {
			suppressNextChar = false;
			return true;
		}
		return super.charTyped(input);
	}

	private boolean searchByHeldItem() {
		if (minecraft == null || minecraft.player == null) {
			return false;
		}
		ItemStack held = minecraft.player.getMainHandItem();
		if (held.isEmpty()) {
			return false;
		}
		var ops = ItemIdentityFactory.opsFor(minecraft);
		if (ops == null) {
			return false;
		}
		ItemIdentity identity = ItemIdentityFactory.from(held, ops,
			IdentityRules.from(RadarState.config()));
		selectItem(identity.fingerprint(), identity.displayName());
		return true;
	}

	@Override
	public void removed() {
		if (!RadarActions.lastHits().isEmpty()
			&& (ChestHighlightRenderer.isLive() || ChestHighlightRenderer.armedSince(openedAt))) {
			ChestHighlightRenderer.onSearch();
		}
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
