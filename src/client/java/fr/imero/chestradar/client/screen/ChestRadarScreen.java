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
import fr.imero.chestradar.client.capture.NestedContents;
import fr.imero.chestradar.client.render.ChestHighlightRenderer;
import fr.imero.chestradar.config.ViewMode;
import fr.imero.chestradar.data.ContainerKey;
import fr.imero.chestradar.data.ItemIdentity;
import fr.imero.chestradar.search.AggregatedItem;
import fr.imero.chestradar.search.Hit;
import fr.imero.chestradar.search.RadarQuery;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ChestRadarScreen extends Screen {
	private TextFieldWidget searchField;
	private RadarView view;
	private List<AggregatedItem> all = new ArrayList<>();
	private boolean tooltipFailureLogged;
	private String pendingSearchText;
	private int pendingSearchCursor;
	private boolean suppressNextChar;
	private boolean pinnedOnly;

	private final long openedAt = System.currentTimeMillis();

	private List<RadarDial.Contact> contacts = List.of();
	private int containerCount;

	private EmptyReason emptyReason;

	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;

	private static final int MAX_DIAL_CONTACTS = 64;

	private enum EmptyReason {
		NOTHING_INDEXED(Items.CHEST, "chestradar.screen.empty", "chestradar.screen.empty_hint"),
		NO_MATCH(Items.SPYGLASS, "chestradar.screen.no_match", "chestradar.screen.no_match_hint"),
		NO_PINS(Items.NETHER_STAR, "chestradar.screen.no_pins", "chestradar.screen.no_pins_hint");

		private final net.minecraft.item.Item icon;
		private final String titleKey;
		private final String hintKey;

		EmptyReason(net.minecraft.item.Item icon, String titleKey, String hintKey) {
			this.icon = icon;
			this.titleKey = titleKey;
			this.hintKey = hintKey;
		}
	}

	public ChestRadarScreen() {
		super(Text.translatable("chestradar.screen.title"));
	}

	@Override
	protected void init() {
		panelWidth = Math.max(RadarTheme.PANEL_MIN_WIDTH,
			Math.min(RadarTheme.PANEL_MAX_WIDTH, (int) (width * RadarState.config().panelWidthFraction)));
		panelHeight = Math.max(RadarTheme.PANEL_MIN_HEIGHT,
			Math.min(RadarTheme.PANEL_MAX_HEIGHT, (int) (height * RadarTheme.PANEL_SIZE_FRACTION)));
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;

		int inner = panelX + Theme.PADDING;
		int innerWidth = panelWidth - Theme.PADDING * 2;
		int toolbarY = toolbarTop();
		int footerY = footerTop();

		int searchFieldY = toolbarY + (Theme.TOOLBAR_HEIGHT - Theme.SEARCH_FIELD_HEIGHT) / 2;
		int searchFieldWidth = innerWidth - RadarTheme.SORT_BUTTON_WIDTH - Theme.GUTTER
			- (RadarState.config().pinsEnabled ? RadarTheme.PIN_BUTTON_WIDTH + Theme.GUTTER : 0);
		searchField = new TextFieldWidget(textRenderer, inner, searchFieldY, searchFieldWidth,
			Theme.SEARCH_FIELD_HEIGHT, Text.translatable("chestradar.screen.search"));
		searchField.setDrawsBackground(false);
		searchField.setPlaceholder(Text.translatable("chestradar.screen.search")
			.styled(s -> s.withColor(Theme.rgb(Theme.TEXT_SECONDARY))));
		searchField.setTooltip(Tooltip.of(Text.translatable("chestradar.screen.search_tip")));
		searchField.setChangedListener(text -> refresh());
		addDrawableChild(searchField);
		setInitialFocus(searchField);

		if (pendingSearchText != null) {
			searchField.setText(pendingSearchText);
			searchField.setCursor(pendingSearchCursor, false);
			pendingSearchText = null;
		}

		int toolbarButtonY = toolbarY + (Theme.TOOLBAR_HEIGHT - Theme.BUTTON_HEIGHT) / 2;
		if (RadarState.config().pinsEnabled) {
			addDrawableChild(new FlatButton(
				inner + innerWidth - RadarTheme.SORT_BUTTON_WIDTH - Theme.GUTTER - RadarTheme.PIN_BUTTON_WIDTH,
				toolbarButtonY, RadarTheme.PIN_BUTTON_WIDTH, Theme.BUTTON_HEIGHT, pinFilterLabel(), b -> {
					pinnedOnly = !pinnedOnly;
					b.setMessage(pinFilterLabel());
					refresh();
				}));
		}
		addDrawableChild(new FlatButton(inner + innerWidth - RadarTheme.SORT_BUTTON_WIDTH, toolbarButtonY,
			RadarTheme.SORT_BUTTON_WIDTH, Theme.BUTTON_HEIGHT, sortLabel(), b -> {
			RadarState.config().sortMode = RadarState.config().sortMode.next();
			RadarState.config().save(ClientWorldKey.configFile());
			b.setMessage(sortLabel());
			refresh();
		}));

		int footerButtonY = footerY + (Theme.FOOTER_HEIGHT - Theme.BUTTON_HEIGHT) / 2;
		addDrawableChild(new FlatButton(inner, footerButtonY, Theme.FOOTER_BUTTON_WIDTH,
			Theme.BUTTON_HEIGHT, Text.translatable("chestradar.screen.settings"),
			b -> client.setScreen(new ConfigScreen(this))));

		if (RadarState.config().routeEnabled) {
			addDrawableChild(new FlatButton(inner + Theme.FOOTER_BUTTON_WIDTH + Theme.GUTTER,
				footerButtonY, Theme.FOOTER_BUTTON_WIDTH, Theme.BUTTON_HEIGHT, routeLabel(),
				b -> toggleRoute()));
		}

		addDrawableChild(new FlatButton(inner + innerWidth - Theme.FOOTER_BUTTON_WIDTH, footerButtonY,
			Theme.FOOTER_BUTTON_WIDTH, Theme.BUTTON_HEIGHT, viewLabel(), b -> {
				RadarState.config().viewMode = RadarState.config().viewMode.next();
				RadarState.config().save(ClientWorldKey.configFile());
				reinit();
			}));

		view = RadarState.config().viewMode == ViewMode.GRID
			? new RadarGrid(inner, listTop(), innerWidth, listHeight())
			: new RadarList(inner, listTop(), innerWidth, listHeight());
		reload();
	}

	private int headerSeparatorY() {
		return panelY + Theme.HEADER_HEIGHT;
	}

	private int toolbarTop() {
		return headerSeparatorY() + 1 + Theme.SECTION_GAP;
	}

	private int toolbarSeparatorY() {
		return toolbarTop() + Theme.TOOLBAR_HEIGHT;
	}

	private int listTop() {
		return toolbarSeparatorY() + 1 + Theme.SECTION_GAP;
	}

	private int footerTop() {
		return panelY + panelHeight - Theme.FOOTER_HEIGHT;
	}

	private int footerSeparatorY() {
		return footerTop() - Theme.SECTION_GAP - 1;
	}

	private int listHeight() {
		return footerSeparatorY() - listTop();
	}

	private Text sortLabel() {
		return Text.translatable("chestradar.screen.sort." + RadarState.config().sortMode.name());
	}

	private Text viewLabel() {
		return Text.translatable("chestradar.screen.view." + RadarState.config().viewMode.name());
	}

	private Text pinFilterLabel() {
		return Text.literal(pinnedOnly ? "★" : "☆");
	}

	private Text routeLabel() {
		return Text.translatable(RadarRoute.isActive()
			? "chestradar.screen.route_stop" : "chestradar.screen.route");
	}

	private void toggleRoute() {
		if (RadarRoute.isActive()) {
			RadarRoute.cancel(client);
			reinit();
			return;
		}
		if (client != null) {
			client.setScreen(null);
		}
		PinRoute.start(client);
	}

	private void reinit() {
		if (searchField != null) {
			pendingSearchText = searchField.getText();
			pendingSearchCursor = searchField.getCursor();
		}
		clearChildren();
		init();
	}

	private void reload() {
		if (client == null || client.player == null || client.world == null) {
			return;
		}
		var pos = client.player.getEntityPos();
		String dimensionId = client.world.getRegistryKey().getValue().toString();
		all = RadarQuery.aggregate(RadarState.index(), dimensionId, pos.x, pos.y, pos.z,
			RadarState.config().searchRadius, RadarScope.allDimensions(), RadarScope.accept());
		buildContacts();
		refresh();
	}

	private void buildContacts() {
		Map<ContainerKey, Double> nearest = new HashMap<>();
		for (AggregatedItem item : all) {
			for (Hit hit : item.hits()) {
				nearest.merge(hit.container().key(), hit.distance(), Math::min);
			}
		}
		containerCount = nearest.size();
		contacts = nearest.entrySet().stream()
			.sorted(Map.Entry.comparingByValue())
			.limit(MAX_DIAL_CONTACTS)
			.map(entry -> new RadarDial.Contact(entry.getKey(), entry.getValue()))
			.toList();
	}

	private void refresh() {
		List<AggregatedItem> filtered = RadarQuery.filter(all,
			RadarScope.parse(searchField == null ? "" : searchField.getText()),
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

	private void drawMasthead(DrawContext context, AggregatedItem hovered) {
		RadarDial.render(context, client, panelX + Theme.PADDING,
			panelY + (Theme.HEADER_HEIGHT - RadarTheme.DIAL_SIZE) / 2, contacts, litContacts(hovered),
			RadarState.config().searchRadius);
		Panel.masthead(context, textRenderer, title, headerStat(), panelX, panelY, panelWidth,
			RadarTheme.DIAL_SIZE);
	}

	private Set<ContainerKey> litContacts(AggregatedItem hovered) {
		return hovered == null ? Set.of()
			: hovered.hits().stream().map(hit -> hit.container().key()).collect(Collectors.toSet());
	}

	private Text headerStat() {
		Text full = stat(true);
		int room = panelWidth - Theme.PADDING * 2 - RadarTheme.DIAL_SIZE - Theme.GUTTER * 2
			- Wordmark.width(textRenderer, title.getString(), Wordmark.TITLE_TRACKING);
		return textRenderer.getWidth(full) <= room ? full : stat(false);
	}

	private Text stat(boolean full) {
		MutableText text = Text.empty()
			.append(number(all.size(), Theme.ACCENT))
			.append(Text.literal(" "))
			.append(Text.translatable("chestradar.screen.stat_items"));
		if (full) {
			text.append(separator())
				.append(number(containerCount, RadarTheme.PATINA))
				.append(Text.literal(" "))
				.append(Text.translatable("chestradar.screen.stat_containers"));
		}
		return text.append(separator())
			.append(number((int) RadarState.config().searchRadius, RadarTheme.PATINA))
			.append(Text.literal(" m"));
	}

	private static Text separator() {
		return Text.literal("  ·  ");
	}

	private static Text number(int value, int color) {
		return Text.literal(String.format(Locale.ROOT, "%,d", value))
			.styled(style -> style.withColor(Theme.rgb(color)));
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		Panel.backdrop(context, width, height);
		Panel.frame(context, panelX, panelY, panelWidth, panelHeight);

		AggregatedItem hovered = view.hoveredItem(mouseX, mouseY);
		drawMasthead(context, hovered);

		drawSectionSeparator(context, headerSeparatorY());
		drawSectionSeparator(context, toolbarSeparatorY());
		drawSectionSeparator(context, footerSeparatorY());
		drawClickHint(context);

		super.render(context, mouseX, mouseY, delta);

		int underlineY = searchField.getY() + searchField.getHeight() + 2;
		context.fill(searchField.getX(), underlineY, searchField.getX() + searchField.getWidth(),
			underlineY + 1, searchField.isFocused() ? Theme.ACCENT : Theme.BORDER);

		view.render(context, mouseX, mouseY);
		if (emptyReason != null) {
			EmptyState.render(context, textRenderer, panelX + Theme.PADDING, listTop(),
				panelWidth - Theme.PADDING * 2, listHeight(), new ItemStack(emptyReason.icon),
				Text.translatable(emptyReason.titleKey), Text.translatable(emptyReason.hintKey));
		}

		if (hovered != null && client != null) {
			renderTooltip(context, hovered, mouseX, mouseY);
		}
	}

	private EmptyReason emptyReason(boolean matchedBeforePins) {
		if (pinnedOnly && matchedBeforePins) {
			return EmptyReason.NO_PINS;
		}
		return all.isEmpty() ? EmptyReason.NOTHING_INDEXED : EmptyReason.NO_MATCH;
	}

	private void drawSectionSeparator(DrawContext context, int y) {
		Panel.separator(context, panelX, y, panelWidth);
	}

	private void drawClickHint(DrawContext context) {
		int gapStart = panelX + Theme.PADDING + Theme.FOOTER_BUTTON_WIDTH + Theme.GUTTER
			+ (RadarState.config().routeEnabled ? Theme.FOOTER_BUTTON_WIDTH + Theme.GUTTER : 0);
		int gapEnd = panelX + panelWidth - Theme.PADDING - Theme.FOOTER_BUTTON_WIDTH - Theme.GUTTER;
		Text hint = Text.translatable("chestradar.screen.click_hint");
		int hintWidth = textRenderer.getWidth(hint);
		if (hintWidth > gapEnd - gapStart) {
			return;
		}
		context.drawText(textRenderer, hint, (gapStart + gapEnd - hintWidth) / 2,
			footerTop() + (Theme.FOOTER_HEIGHT - 8) / 2, Theme.TEXT_SECONDARY, false);
	}

	private void renderTooltip(DrawContext context, AggregatedItem hovered, int mouseX, int mouseY) {
		try {
			List<Text> lines = new ArrayList<>(getTooltipFromItem(client,
				ItemIdentityRenderer.toStack(hovered.identity(), client)));
			lines.add(Text.translatable("chestradar.screen.total",
					String.format(Locale.ROOT, "%,d", hovered.totalCount()))
				.formatted(Formatting.GRAY));
			context.drawTooltip(textRenderer, lines, mouseX, mouseY);
		} catch (Exception e) {
			if (!tooltipFailureLogged) {
				tooltipFailureLogged = true;
				ChestRadar.LOGGER.warn("[chestradar] tooltip impossible pour {} (un seul avertissement)",
					hovered.identity().fingerprint(), e);
			}
		}
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubleClick) {
		int button = click.button();
		if (button != InputUtil.GLFW_MOUSE_BUTTON_LEFT && button != InputUtil.GLFW_MOUSE_BUTTON_RIGHT
			&& button != InputUtil.GLFW_MOUSE_BUTTON_MIDDLE) {
			return super.mouseClicked(click, doubleClick);
		}
		if (view.mouseClicked(click.x(), click.y(), button)) {
			return true;
		}
		return super.mouseClicked(click, doubleClick);
	}

	private void selectItem(String fingerprint, String searchText) {
		if (searchText != null && searchField != null) {
			searchField.setText(searchText);
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
	public boolean keyPressed(KeyInput input) {
		if (input.key() == GLFW.GLFW_KEY_ESCAPE && view.clearSelection()) {
			return true;
		}
		if (super.keyPressed(input)) {
			return true;
		}
		if (ChestRadarKeybinds.SEARCH.matchesKey(input) && searchByHeldItem()) {
			suppressNextChar = true;
			return true;
		}
		return false;
	}

	@Override
	public boolean charTyped(CharInput input) {
		if (suppressNextChar) {
			suppressNextChar = false;
			return true;
		}
		return super.charTyped(input);
	}

	private boolean searchByHeldItem() {
		if (client == null || client.player == null) {
			return false;
		}
		ItemStack held = client.player.getMainHandStack();
		if (held.isEmpty()) {
			return false;
		}
		var ops = ItemIdentityFactory.opsFor(client);
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
	public boolean shouldPause() {
		return false;
	}
}
