package fr.imero.chestradar.client.screen;

import fr.imero.chestradar.client.ClientWorldKey;
import fr.imero.chestradar.client.InventoryTotals;
import fr.imero.chestradar.client.RadarActions;
import fr.imero.chestradar.client.RadarState;
import fr.imero.chestradar.client.RadarTarget;
import fr.imero.chestradar.client.integration.JadeCompat;
import fr.imero.chestradar.client.render.RadarHud;
import fr.imero.chestradar.config.ChestRadarConfig;
import fr.imero.chestradar.theme.AeternumTheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.AbstractInput;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public class ConfigScreen extends Screen {
	private static final ItemStack BADGE = new ItemStack(Items.COMPARATOR);

	private static final int PANEL_MIN_WIDTH = 300;
	private static final int PANEL_MAX_WIDTH = 620;
	private static final int PANEL_MIN_HEIGHT = 220;
	private static final double PANEL_WIDTH_FRACTION = 0.55;

	private static final int SECTION_HEADER = 18;
	private static final int TOOLTIP_WIDTH = 230;

	private static final BooleanSupplier ALWAYS = () -> true;

	private final Screen parent;

	private record Requirement(BooleanSupplier gate, String labelKey) {
	}

	private static final class Row {
		final String key;
		final ClickableWidget widget;
		final String haystack;
		final List<Requirement> requirements = new ArrayList<>();
		BooleanSupplier enabled = ALWAYS;
		Text disabledOverride;
		int virtualY;
		boolean shown = true;

		Row(String key, ClickableWidget widget, String haystack) {
			this.key = key;
			this.widget = widget;
			this.haystack = haystack;
		}

		boolean active() {
			if (!enabled.getAsBoolean()) {
				return false;
			}
			for (Requirement requirement : requirements) {
				if (!requirement.gate().getAsBoolean()) {
					return false;
				}
			}
			return true;
		}

		Text disabledText() {
			for (Requirement requirement : requirements) {
				if (!requirement.gate().getAsBoolean()) {
					return Text.translatable("chestradar.config.requires",
						Text.translatable(requirement.labelKey()));
				}
			}
			return disabledOverride != null
				? disabledOverride
				: Text.translatable("chestradar.config.disabled");
		}
	}

	private record Section(String key, int virtualY) {
	}

	private final Map<String, List<Row>> sections = new LinkedHashMap<>();
	private final List<Row> allRows = new ArrayList<>();
	private final List<Section> visibleSections = new ArrayList<>();

	private String currentSection;
	private int contentHeight;
	private double scroll;

	private TextFieldWidget filterField;
	private String filterText = "";

	private final ColorPicker picker = new ColorPicker();

	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;

	public ConfigScreen(Screen parent) {
		super(Text.translatable("chestradar.config.title"));
		this.parent = parent;
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

	private int contentTop() {
		return toolbarSeparatorY() + 1 + Theme.SECTION_GAP;
	}

	private int footerTop() {
		return panelY + panelHeight - Theme.FOOTER_HEIGHT;
	}

	private int footerSeparatorY() {
		return footerTop() - Theme.SECTION_GAP - 1;
	}

	private int contentBottom() {
		return footerSeparatorY();
	}

	private int contentViewHeight() {
		return contentBottom() - contentTop();
	}

	private int maxScroll() {
		return Math.max(0, contentHeight - contentViewHeight());
	}

	private boolean inContentBand(double mouseY) {
		return mouseY >= contentTop() && mouseY < contentBottom();
	}

	@Override
	protected void init() {
		Theme.sync();
		sections.clear();
		allRows.clear();
		visibleSections.clear();

		panelWidth = Math.clamp((int) (width * PANEL_WIDTH_FRACTION), PANEL_MIN_WIDTH, PANEL_MAX_WIDTH);
		panelWidth = Math.min(panelWidth, Math.max(PANEL_MIN_WIDTH, width - 20));
		panelHeight = Math.max(PANEL_MIN_HEIGHT, height - 40);
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;

		int x = panelX + Theme.PADDING;
		int w = panelWidth - Theme.PADDING * 2;

		int filterY = toolbarTop() + (Theme.TOOLBAR_HEIGHT - Theme.SEARCH_FIELD_HEIGHT) / 2;
		filterField = new TextFieldWidget(textRenderer, x, filterY, w, Theme.SEARCH_FIELD_HEIGHT,
			Text.translatable("chestradar.config.filter"));
		filterField.setDrawsBackground(false);
		filterField.setPlaceholder(Text.translatable("chestradar.config.filter")
			.styled(s -> s.withColor(Theme.rgb(Theme.TEXT_SECONDARY))));
		filterField.setText(filterText);
		filterField.setChangedListener(text -> {
			filterText = text;
			applyFilter();
		});
		addDrawableChild(filterField);
		setInitialFocus(filterField);

		buildRows(x, w);

		addDrawableChild(new FlatButton(x + w - Theme.FOOTER_BUTTON_WIDTH,
			footerTop() + (Theme.FOOTER_HEIGHT - Theme.BUTTON_HEIGHT) / 2,
			Theme.FOOTER_BUTTON_WIDTH, Theme.BUTTON_HEIGHT, ScreenTexts.DONE, b -> close()).primary());

		applyFilter();
	}

	private void buildRows(int x, int w) {
		ChestRadarConfig config = RadarState.config();

		section("chestradar.config.section.search");
		slider(x, w, "chestradar.config.radius", 16, 1024,
			() -> config.searchRadius, v -> config.searchRadius = v, v -> unit(v, "chestradar.unit.blocks"));
		toggle(x, w, "chestradar.config.lore", config.searchIncludesLore,
			v -> config.searchIncludesLore = v);
		toggle(x, w, "chestradar.config.structured_search", config.structuredSearch,
			v -> config.structuredSearch = v);
		toggle(x, w, "chestradar.config.all_dimensions", config.searchAllDimensions,
			v -> config.searchAllDimensions = v);
		slider(x, w, "chestradar.config.chat_limit", 1, 50,
			() -> config.chatResultLimit, v -> config.chatResultLimit = (int) v, String::valueOf);

		section("chestradar.config.section.freshness");
		toggle(x, w, "chestradar.config.auto_refresh", config.autoRefreshIndex,
			v -> config.autoRefreshIndex = v);
		toggle(x, w, "chestradar.config.show_last_seen", config.showLastSeen,
			v -> config.showLastSeen = v);
		slider(x, w, "chestradar.config.stale_after", 0, 365,
			() -> config.staleAfterDays, v -> config.staleAfterDays = (int) v,
			v -> v == 0 ? I18n.translate("chestradar.config.never") : unit(v, "chestradar.unit.days"))
			.requires("chestradar.config.auto_refresh", () -> config.autoRefreshIndex);
		toggle(x, w, "chestradar.config.hide_stale", config.hideStaleContainers,
			v -> config.hideStaleContainers = v)
			.requires("chestradar.config.auto_refresh", () -> config.autoRefreshIndex)
			.requires("chestradar.config.stale_after", () -> config.staleAfterDays > 0);
		toggle(x, w, "chestradar.config.auto_purge", config.autoPurgeMissing,
			v -> config.autoPurgeMissing = v)
			.requires("chestradar.config.auto_refresh", () -> config.autoRefreshIndex);
		slider(x, w, "chestradar.config.purge_interval", 1, 60,
			() -> config.purgeCheckIntervalSeconds, v -> config.purgeCheckIntervalSeconds = (int) v,
			v -> unit(v, "chestradar.unit.seconds"))
			.requires("chestradar.config.auto_refresh", () -> config.autoRefreshIndex)
			.requires("chestradar.config.auto_purge", () -> config.autoPurgeMissing);

		section("chestradar.config.section.world");
		toggle(x, w, "chestradar.config.highlight", config.highlightEnabled,
			v -> config.highlightEnabled = v);
		slider(x, w, "chestradar.config.highlight_seconds", 0, 120,
			() -> config.highlightSeconds, v -> config.highlightSeconds = (int) v, v -> unit(v, "chestradar.unit.seconds"))
			.requires("chestradar.config.highlight", () -> config.highlightEnabled);
		toggle(x, w, "chestradar.config.highlight_labels", config.highlightLabels,
			v -> config.highlightLabels = v)
			.requires("chestradar.config.highlight", () -> config.highlightEnabled);
		slider(x, w, "chestradar.config.label_distance", 8, 512,
			() -> config.highlightLabelDistance, v -> config.highlightLabelDistance = v, v -> unit(v, "chestradar.unit.blocks"))
			.requires("chestradar.config.highlight", () -> config.highlightEnabled)
			.requires("chestradar.config.highlight_labels", () -> config.highlightLabels);
		toggle(x, w, "chestradar.config.slot_highlight", config.slotHighlightEnabled,
			v -> config.slotHighlightEnabled = v);
		slider(x, w, "chestradar.config.slot_highlight_seconds", 1, 60,
			() -> config.slotHighlightSeconds, v -> config.slotHighlightSeconds = (int) v,
			v -> unit(v, "chestradar.unit.seconds"))
			.requires("chestradar.config.slot_highlight", () -> config.slotHighlightEnabled);
		toggle(x, w, "chestradar.config.compass", config.compassEnabled,
			v -> config.compassEnabled = v);
		slider(x, w, "chestradar.config.arrival_distance", 1, 64,
			() -> config.compassArrivalDistance, v -> config.compassArrivalDistance = v, v -> unit(v, "chestradar.unit.blocks"))
			.requires("chestradar.config.compass", () -> config.compassEnabled);
		toggle(x, w, "chestradar.config.face_container", config.faceContainerOnLocate,
			v -> config.faceContainerOnLocate = v);
		toggle(x, w, "chestradar.config.look_at_preview", config.lookAtPreview,
			v -> config.lookAtPreview = v);
		slider(x, w, "chestradar.config.preview_rows", 1, 20,
			() -> config.lookAtPreviewRows, v -> config.lookAtPreviewRows = (int) v, String::valueOf)
			.requires("chestradar.config.look_at_preview", () -> config.lookAtPreview);
		toggle(x, w, "chestradar.config.inventory_totals", config.inventoryTotals,
			v -> config.inventoryTotals = v);
		toggle(x, w, "chestradar.config.jade", config.jadeIntegration,
			v -> config.jadeIntegration = v)
			.dependsOn(() -> config.lookAtPreview && JadeCompat.installed())
			.disabledSays("chestradar.config.jade.requires");

		section("chestradar.config.section.capture");
		toggle(x, w, "chestradar.config.barrels", config.indexBarrels,
			v -> config.indexBarrels = v);
		toggle(x, w, "chestradar.config.shulkers", config.indexShulkerBoxes,
			v -> config.indexShulkerBoxes = v);
		toggle(x, w, "chestradar.config.hopper_gui", config.indexHopperGuiContainers,
			v -> config.indexHopperGuiContainers = v);
		toggle(x, w, "chestradar.config.bulk_quantity", config.indexBulkQuantity,
			v -> config.indexBulkQuantity = v);
		toggle(x, w, "chestradar.config.nested", config.indexNestedContents,
			v -> config.indexNestedContents = v);

		section("chestradar.config.section.changes");
		toggle(x, w, "chestradar.config.report_changes", config.reportContainerChanges,
			v -> config.reportContainerChanges = v);
		toggle(x, w, "chestradar.config.between_visits", config.reportOnlyChangesBetweenVisits,
			v -> config.reportOnlyChangesBetweenVisits = v)
			.requires("chestradar.config.report_changes", () -> config.reportContainerChanges);
		slider(x, w, "chestradar.config.change_limit", 1, 50,
			() -> config.containerChangeLimit, v -> config.containerChangeLimit = (int) v,
			String::valueOf)
			.requires("chestradar.config.report_changes", () -> config.reportContainerChanges);

		section("chestradar.config.section.interface");
		toggle(x, w, "chestradar.config.pins", config.pinsEnabled,
			v -> config.pinsEnabled = v);
		slider(x, w, "chestradar.config.panel_width",
			(int) Math.round(ChestRadarConfig.PANEL_WIDTH_FRACTION_MIN * 100),
			(int) Math.round(ChestRadarConfig.PANEL_WIDTH_FRACTION_MAX * 100),
			() -> config.panelWidthFraction * 100, v -> config.panelWidthFraction = v / 100.0,
			v -> v + " %");

		section("chestradar.config.section.craft");
		toggle(x, w, "chestradar.config.craft_planner", config.craftPlannerEnabled,
			v -> config.craftPlannerEnabled = v);
		toggle(x, w, "chestradar.config.craft_tab", config.craftPlannerTab,
			v -> config.craftPlannerTab = v)
			.requires("chestradar.config.craft_planner", () -> config.craftPlannerEnabled);
		toggle(x, w, "chestradar.config.route", config.routeEnabled,
			v -> config.routeEnabled = v);
		slider(x, w, "chestradar.config.route_stops", 1, 24,
			() -> config.routeMaxStops, v -> config.routeMaxStops = (int) v, String::valueOf)
			.requires("chestradar.config.route", () -> config.routeEnabled);

		section("chestradar.config.section.theme");
		choice(x, w, "chestradar.config.theme", () -> AeternumTheme.preset().translationKey(),
			() -> {
				AeternumTheme.cycle();
				Theme.sync();
			});
		for (AeternumTheme.Role role : AeternumTheme.Role.values()) {
			add(role.translationKey(), new ColorRow(x, w, role));
		}
		add("chestradar.config.theme_reset",
			new ActionRow(x, w, Text.translatable("chestradar.config.theme_reset"), b -> {
				AeternumTheme.reset();
				Theme.sync();
			}));

		section("chestradar.config.section.data");
		Row clear = add("chestradar.config.clear",
			new ActionRow(x, w, Text.translatable("chestradar.config.clear"), b -> {
				RadarState.index().clear();
				RadarActions.clearHits();
				RadarTarget.clear();
				fr.imero.chestradar.client.RadarRoute.clear();
				RadarHud.invalidate();
				InventoryTotals.invalidate();
				RadarState.markDirty();
				RadarState.saveNow(client);
			}));
		clear.enabled = () -> client != null && client.world != null;
	}

	private void section(String key) {
		currentSection = key;
		sections.computeIfAbsent(key, k -> new ArrayList<>());
	}

	private Row add(String key, ClickableWidget widget) {
		String tipKey = key + ".tip";
		String haystack = (I18n.translate(key) + " " + I18n.translate(currentSection) + " "
			+ (I18n.hasTranslation(tipKey) ? I18n.translate(tipKey) : ""))
			.toLowerCase(Locale.ROOT);
		Row row = new Row(key, widget, haystack);
		sections.get(currentSection).add(row);
		allRows.add(row);
		addSelectableChild(widget);
		return row;
	}

	private record Declared(Row row) {
		Declared dependsOn(BooleanSupplier gate) {
			row.enabled = gate;
			return this;
		}

		Declared requires(String labelKey, BooleanSupplier gate) {
			row.requirements.add(new Requirement(gate, labelKey));
			return this;
		}

		void disabledSays(String key) {
			row.disabledOverride = Text.translatable(key);
		}
	}

	private Declared toggle(int x, int w, String key, boolean initial, Consumer<Boolean> setter) {
		return new Declared(add(key, new ToggleRow(x, w, Text.translatable(key), initial, setter)));
	}

	private Declared choice(int x, int w, String key, Supplier<String> valueKey, Runnable cycle) {
		return new Declared(add(key, new ChoiceRow(x, w, Text.translatable(key), valueKey, cycle)));
	}

	private void openPicker(ClickableWidget anchor, AeternumTheme.Role role) {
		AeternumTheme.Palette palette = AeternumTheme.palette();
		List<Integer> quick = new ArrayList<>();
		for (AeternumTheme.Role other : AeternumTheme.Role.values()) {
			quick.add(palette.role(other));
		}
		picker.open(Text.translatable(role.translationKey()), palette.role(role),
			anchor.getRight() - ColorPicker.WIDTH, anchor.getBottom(), width, height, quick,
			rgb -> {
				AeternumTheme.setRole(role, rgb);
				Theme.sync();
			});
	}

	private Declared slider(int x, int w, String key, int min, int max,
			DoubleSupplier getter, DoubleConsumer setter, IntFunction<String> format) {
		return new Declared(add(key, new SliderRow(x, w, key, min, max, getter, setter, format)));
	}

	private static String unit(int value, String key) {
		return value + " " + I18n.translate(key);
	}

	private void applyFilter() {
		String query = filterText.trim().toLowerCase(Locale.ROOT);
		for (Row row : allRows) {
			row.shown = query.isEmpty() || row.haystack.contains(query);
		}
		relayout();
	}

	private void relayout() {
		visibleSections.clear();
		int y = 0;
		for (Map.Entry<String, List<Row>> entry : sections.entrySet()) {
			List<Row> kept = entry.getValue().stream().filter(row -> row.shown).toList();
			if (kept.isEmpty()) {
				continue;
			}
			if (!visibleSections.isEmpty()) {
				y += Theme.SETTING_SECTION_GAP;
			}
			visibleSections.add(new Section(entry.getKey(), y));
			y += SECTION_HEADER;
			for (Row row : kept) {
				row.virtualY = y;
				y += Theme.SETTING_ROW_HEIGHT;
			}
		}
		contentHeight = y + Theme.PADDING;
		scroll = Math.clamp(scroll, 0, maxScroll());
		position();
	}

	private void position() {
		int top = contentTop();
		int bottom = contentBottom();
		for (Row row : allRows) {
			if (!row.shown) {
				row.widget.visible = false;
				continue;
			}
			int screenY = top + row.virtualY - (int) scroll;
			row.widget.setY(screenY);
			row.widget.visible = screenY + Theme.SETTING_ROW_HEIGHT > top && screenY < bottom;
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (inContentBand(mouseY) && maxScroll() > 0) {
			scroll = Math.clamp(scroll - vertical * Theme.SETTING_ROW_HEIGHT, 0, maxScroll());
			position();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	@Override
	public void close() {
		RadarState.config().save(ClientWorldKey.configFile());
		if (client != null) {
			client.setScreen(parent);
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		for (Row row : allRows) {
			row.widget.active = row.active();
		}

		Panel.backdrop(context, width, height);
		Panel.frame(context, panelX, panelY, panelWidth, panelHeight);
		Panel.badge(context, BADGE, panelX + Theme.PADDING, panelY);
		Panel.masthead(context, textRenderer, title, summary(), panelX, panelY, panelWidth, Theme.ICON);

		drawSeparator(context, headerSeparatorY());
		drawSeparator(context, toolbarSeparatorY());
		drawSeparator(context, footerSeparatorY());

		context.enableScissor(panelX + 1, contentTop(), panelX + panelWidth - 1, contentBottom());
		for (Section section : visibleSections) {
			int y = contentTop() + section.virtualY() - (int) scroll;
			Wordmark.draw(context, textRenderer, Text.translatable(section.key()).getString(),
				panelX + Theme.PADDING, y + 5, Theme.ACCENT, Wordmark.LABEL_TRACKING);
			context.fill(panelX + Theme.PADDING, y + SECTION_HEADER - 5,
				panelX + panelWidth - Theme.PADDING, y + SECTION_HEADER - 4, Theme.BORDER);
		}
		for (Row row : allRows) {
			if (row.shown && row.widget.visible) {
				row.widget.render(context, mouseX, mouseY, delta);
			}
		}
		if (visibleSections.isEmpty()) {
			Text empty = Text.translatable("chestradar.config.no_match");
			context.drawText(textRenderer, empty,
				panelX + (panelWidth - textRenderer.getWidth(empty)) / 2,
				contentTop() + contentViewHeight() / 2 - 4, Theme.TEXT_SECONDARY, false);
		}
		context.disableScissor();

		super.render(context, mouseX, mouseY, delta);

		int underlineY = filterField.getY() + filterField.getHeight() + 2;
		context.fill(filterField.getX(), underlineY, filterField.getX() + filterField.getWidth(),
			underlineY + 1, filterField.isFocused() ? Theme.ACCENT : Theme.BORDER);

		drawFooterHint(context);
		drawScrollbar(context);
		drawSettingTooltip(context, mouseX, mouseY);
		picker.render(context, textRenderer, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubleClick) {
		if (picker.isOpen()) {
			return picker.mouseClicked(click.x(), click.y(), click.button());
		}
		return super.mouseClicked(click, doubleClick);
	}

	@Override
	public boolean mouseDragged(Click click, double offsetX, double offsetY) {
		if (picker.mouseDragged(click.x(), click.y())) {
			return true;
		}
		return super.mouseDragged(click, offsetX, offsetY);
	}

	@Override
	public boolean mouseReleased(Click click) {
		if (picker.mouseReleased()) {
			return true;
		}
		return super.mouseReleased(click);
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (picker.keyPressed(input.key())) {
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public boolean charTyped(CharInput input) {
		if (picker.charTyped((char) input.codepoint())) {
			return true;
		}
		return super.charTyped(input);
	}

	private Text summary() {
		int total = allRows.size();
		long shown = allRows.stream().filter(row -> row.shown).count();
		return shown == total
			? Text.empty().append(accent(Integer.toString(total)))
				.append(Text.literal(" " + I18n.translate("chestradar.config.settings")))
			: Text.empty().append(accent(Long.toString(shown)))
				.append(Text.literal(" / " + total));
	}

	private static Text accent(String value) {
		return Text.literal(value).styled(style -> style.withColor(Theme.rgb(Theme.ACCENT)));
	}

	private void drawFooterHint(DrawContext context) {
		Text hint = Text.translatable("chestradar.config.hint");
		int available = panelWidth - Theme.PADDING * 2 - Theme.FOOTER_BUTTON_WIDTH - Theme.GUTTER;
		if (textRenderer.getWidth(hint) > available) {
			return;
		}
		context.drawText(textRenderer, hint, panelX + Theme.PADDING,
			footerTop() + (Theme.FOOTER_HEIGHT - 8) / 2, Theme.TEXT_SECONDARY, false);
	}

	private void drawSettingTooltip(DrawContext context, int mouseX, int mouseY) {
		if (!inContentBand(mouseY)) {
			return;
		}
		for (Row row : allRows) {
			if (!row.shown || !row.widget.visible || !row.widget.isMouseOver(mouseX, mouseY)) {
				continue;
			}
			String tipKey = row.key + ".tip";
			if (!I18n.hasTranslation(tipKey)) {
				return;
			}
			List<OrderedText> lines = new ArrayList<>();
			lines.add(Text.translatable(row.key)
				.styled(s -> s.withColor(Theme.rgb(Theme.TEXT_PRIMARY))).asOrderedText());
			lines.addAll(textRenderer.wrapLines(Text.translatable(tipKey)
				.styled(s -> s.withColor(Theme.rgb(Theme.TEXT_SECONDARY))), TOOLTIP_WIDTH));
			if (!row.widget.active) {
				lines.addAll(textRenderer.wrapLines(row.disabledText().copy()
					.styled(s -> s.withColor(Theme.rgb(Theme.TEXT_STALE))), TOOLTIP_WIDTH));
			}
			context.drawOrderedTooltip(textRenderer, lines, mouseX, mouseY);
			return;
		}
	}

	private void drawSeparator(DrawContext context, int y) {
		Panel.separator(context, panelX, y, panelWidth);
	}

	private void drawScrollbar(DrawContext context) {
		Panel.scrollbar(context, panelX + panelWidth - Theme.SCROLLBAR_WIDTH - 2, contentTop(),
			contentViewHeight(), contentHeight, scroll);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private static void drawRowLabel(DrawContext context, TextRenderer font, Text label,
			int x, int y, int maxWidth, int color) {
		context.drawText(font, font.trimToWidth(label.getString(), Math.max(0, maxWidth)),
			x, y, color, false);
	}

	private void drawRowBackground(DrawContext context, ClickableWidget widget, boolean hot) {
		if (hot) {
			context.fill(widget.getX() - 4, widget.getY(), widget.getRight() + 4,
				widget.getBottom(), Theme.ROW_HOVER);
		}
	}

	private final class ToggleRow extends PressableWidget {
		private final Consumer<Boolean> setter;
		private boolean value;

		ToggleRow(int x, int width, Text label, boolean initial, Consumer<Boolean> setter) {
			super(x, 0, width, Theme.SETTING_ROW_HEIGHT, label);
			this.value = initial;
			this.setter = setter;
		}

		@Override
		public void onPress(AbstractInput input) {
			value = !value;
			setter.accept(value);
		}

		@Override
		public boolean isMouseOver(double mouseX, double mouseY) {
			return super.isMouseOver(mouseX, mouseY) && inContentBand(mouseY);
		}

		@Override
		protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
			boolean hot = active && (isMouseOver(mouseX, mouseY) || isFocused());
			drawRowBackground(context, this, hot);
			int trackX = getRight() - Theme.SWITCH_WIDTH;
			int trackY = getY() + (getHeight() - Theme.SWITCH_HEIGHT) / 2;
			drawRowLabel(context, MinecraftClient.getInstance().textRenderer, getMessage(),
				getX(), getY() + (getHeight() - 8) / 2, trackX - getX() - Theme.GUTTER,
				active ? Theme.TEXT_PRIMARY : Theme.TEXT_DISABLED);
			context.fill(trackX, trackY, trackX + Theme.SWITCH_WIDTH, trackY + Theme.SWITCH_HEIGHT,
				value && active ? Theme.ACCENT : Theme.SWITCH_OFF);
			int knobX = value ? trackX + Theme.SWITCH_WIDTH - 9 : trackX + 1;
			context.fill(knobX, trackY + 1, knobX + 8, trackY + Theme.SWITCH_HEIGHT - 1,
				value && active ? Theme.SWITCH_KNOB_ON : Theme.SWITCH_KNOB);
		}

		@Override
		protected void appendClickableNarrations(NarrationMessageBuilder builder) {
			appendDefaultNarrations(builder);
		}
	}

	private final class SliderRow extends SliderWidget {
		private final String key;
		private final int min;
		private final int max;
		private final DoubleConsumer setter;
		private final IntFunction<String> format;

		SliderRow(int x, int width, String key, int min, int max,
				DoubleSupplier getter, DoubleConsumer setter, IntFunction<String> format) {
			super(x, 0, width, Theme.SETTING_ROW_HEIGHT, Text.empty(),
				Math.clamp((getter.getAsDouble() - min) / (double) (max - min), 0.0, 1.0));
			this.key = key;
			this.min = min;
			this.max = max;
			this.setter = setter;
			this.format = format;
			updateMessage();
		}

		private int current() {
			return (int) Math.round(min + value * (max - min));
		}

		@Override
		protected void updateMessage() {
			setMessage(Text.translatable(key));
		}

		@Override
		protected void applyValue() {
			setter.accept(current());
		}

		@Override
		public boolean isMouseOver(double mouseX, double mouseY) {
			return super.isMouseOver(mouseX, mouseY) && inContentBand(mouseY);
		}

		@Override
		public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
			boolean hot = active && (isMouseOver(mouseX, mouseY) || isFocused());
			drawRowBackground(context, this, hot);
			TextRenderer font = MinecraftClient.getInstance().textRenderer;
			String shown = format.apply(current());
			int valueWidth = font.getWidth(shown);
			int textY = getY() + 4;
			drawRowLabel(context, font, getMessage(), getX(), textY,
				getWidth() - valueWidth - Theme.GUTTER,
				active ? Theme.TEXT_PRIMARY : Theme.TEXT_DISABLED);
			context.drawText(font, shown, getRight() - valueWidth, textY,
				active ? Theme.ACCENT : Theme.TEXT_DISABLED, false);

			int railLeft = getX() + 4;
			int railRight = getRight() - 4;
			int railY = getBottom() - 7;
			context.fill(railLeft, railY, railRight, railY + 2, Theme.SWITCH_OFF);
			int fillEnd = railLeft + (int) Math.round((getWidth() - 8) * value);
			context.fill(railLeft, railY, fillEnd, railY + 2,
				active ? Theme.ACCENT : Theme.TEXT_DISABLED);
			int knobX = Math.clamp(fillEnd - 2, railLeft, railRight - 4);
			context.fill(knobX, railY - 2, knobX + 4, railY + 4,
				active ? Theme.ACCENT : Theme.TEXT_DISABLED);
		}
	}

	private final class ChoiceRow extends PressableWidget {
		private final Supplier<String> valueKey;
		private final Runnable cycle;

		ChoiceRow(int x, int width, Text label, Supplier<String> valueKey, Runnable cycle) {
			super(x, 0, width, Theme.SETTING_ROW_HEIGHT, label);
			this.valueKey = valueKey;
			this.cycle = cycle;
		}

		@Override
		public void onPress(AbstractInput input) {
			cycle.run();
		}

		@Override
		public boolean isMouseOver(double mouseX, double mouseY) {
			return super.isMouseOver(mouseX, mouseY) && inContentBand(mouseY);
		}

		@Override
		protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
			boolean hot = active && (isMouseOver(mouseX, mouseY) || isFocused());
			drawRowBackground(context, this, hot);
			TextRenderer font = MinecraftClient.getInstance().textRenderer;
			String value = I18n.translate(valueKey.get());
			String shown = hot ? value + " ›" : value;
			int valueWidth = font.getWidth(shown);
			int textY = getY() + (getHeight() - 8) / 2;
			drawRowLabel(context, font, getMessage(), getX(), textY,
				getWidth() - valueWidth - Theme.GUTTER,
				active ? Theme.TEXT_PRIMARY : Theme.TEXT_DISABLED);
			context.drawText(font, shown, getRight() - valueWidth, textY,
				active ? Theme.ACCENT : Theme.TEXT_DISABLED, false);
		}

		@Override
		protected void appendClickableNarrations(NarrationMessageBuilder builder) {
			appendDefaultNarrations(builder);
		}
	}

	private final class ColorRow extends PressableWidget {
		private final AeternumTheme.Role role;

		ColorRow(int x, int width, AeternumTheme.Role role) {
			super(x, 0, width, Theme.SETTING_ROW_HEIGHT, Text.translatable(role.translationKey()));
			this.role = role;
		}

		@Override
		public void onPress(AbstractInput input) {
			openPicker(this, role);
		}

		@Override
		public boolean isMouseOver(double mouseX, double mouseY) {
			return super.isMouseOver(mouseX, mouseY) && inContentBand(mouseY);
		}

		@Override
		protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
			boolean hot = active && (isMouseOver(mouseX, mouseY) || isFocused());
			drawRowBackground(context, this, hot);
			TextRenderer font = MinecraftClient.getInstance().textRenderer;
			int rgb = AeternumTheme.palette().role(role);
			int size = Theme.swatchPreview();
			int swatchX = getRight() - size;
			int swatchY = getY() + (getHeight() - size) / 2;
			String hex = "#" + AeternumTheme.hex(rgb);
			int hexWidth = font.getWidth(hex);
			int hexX = swatchX - Theme.GUTTER - hexWidth;
			int textY = getY() + (getHeight() - 8) / 2;
			drawRowLabel(context, font, getMessage(), getX(), textY,
				hexX - getX() - Theme.GUTTER,
				active ? Theme.TEXT_PRIMARY : Theme.TEXT_DISABLED);
			context.drawText(font, hex, hexX, textY,
				active ? Theme.TEXT_SECONDARY : Theme.TEXT_DISABLED, false);
			context.fill(swatchX - 1, swatchY - 1, swatchX + size + 1, swatchY + size + 1,
				hot ? Theme.ACCENT : Theme.BORDER);
			context.fill(swatchX, swatchY, swatchX + size, swatchY + size, Theme.opaque(rgb));
		}

		@Override
		protected void appendClickableNarrations(NarrationMessageBuilder builder) {
			appendDefaultNarrations(builder);
		}
	}

	private final class ActionRow extends FlatButton {
		ActionRow(int x, int width, Text label, Consumer<FlatButton> onPress) {
			super(x, 0, width, Theme.SETTING_ROW_HEIGHT - 4, label, onPress);
		}

		@Override
		public boolean isMouseOver(double mouseX, double mouseY) {
			return super.isMouseOver(mouseX, mouseY) && inContentBand(mouseY);
		}
	}
}
