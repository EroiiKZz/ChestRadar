package fr.imero.chestradar.client.screen;

import fr.imero.chestradar.ChestRadar;
import fr.imero.chestradar.client.Bearing;
import fr.imero.chestradar.client.ContainerFocus;
import fr.imero.chestradar.client.RadarRoute;
import fr.imero.chestradar.client.RadarState;
import fr.imero.chestradar.client.craft.CraftPlanner;
import fr.imero.chestradar.client.craft.RecipeCatalog;
import fr.imero.chestradar.search.CompactCount;
import fr.imero.chestradar.search.CraftPlan;
import fr.imero.chestradar.search.Hit;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

public class CraftPlanScreen extends Screen {
	private static final ItemStack BADGE = new ItemStack(Items.CRAFTING_TABLE);

	private static final int MAX_MATCHES = 80;
	private static final int TOOLTIP_HITS = 6;
	private static final int QUANTITY_FIELD_WIDTH = 46;
	private static final int STEP_WIDTH = 12;
	private static final int QUANTITY_BLOCK = QUANTITY_FIELD_WIDTH + STEP_WIDTH * 2;

	private final Screen parent;
	private final String initialQuery;
	private final RecipeCatalog.Entry preset;
	private final int presetWanted;

	private List<RecipeCatalog.Entry> catalog = List.of();
	private List<RecipeCatalog.Entry> matches = List.of();

	private TextFieldWidget searchField;
	private TextFieldWidget quantityField;
	private FlatButton minusButton;
	private FlatButton plusButton;
	private FlatButton routeButton;
	private FlatButton backButton;

	private RecipeCatalog.Entry current;
	private CraftPlanner.Planned planned;
	private final Deque<Step> trail = new ArrayDeque<>();

	private String query = "";
	private double scroll;
	private boolean syncing;
	private boolean tooltipFailureLogged;

	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;

	private record Step(RecipeCatalog.Entry entry, int wanted) {
	}

	public CraftPlanScreen(Screen parent) {
		this(parent, null);
	}

	public CraftPlanScreen(Screen parent, String initialQuery) {
		this(parent, initialQuery, null, 0);
	}

	public CraftPlanScreen(Screen parent, RecipeCatalog.Entry preset, int presetWanted) {
		this(parent, null, preset, presetWanted);
	}

	private CraftPlanScreen(Screen parent, String initialQuery, RecipeCatalog.Entry preset,
			int presetWanted) {
		super(Text.translatable("chestradar.craft.title"));
		this.parent = parent;
		this.initialQuery = initialQuery;
		this.preset = preset;
		this.presetWanted = presetWanted;
	}

	@Override
	protected void init() {
		if (catalog.isEmpty()) {
			catalog = RecipeCatalog.build(client);
		}

		panelWidth = Math.max(RadarTheme.PANEL_MIN_WIDTH,
			Math.min(RadarTheme.PANEL_MAX_WIDTH, (int) (width * RadarState.config().panelWidthFraction)));
		panelHeight = Math.max(RadarTheme.PANEL_MIN_HEIGHT,
			Math.min(RadarTheme.PANEL_MAX_HEIGHT, (int) (height * RadarTheme.PANEL_SIZE_FRACTION)));
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;

		int inner = panelX + Theme.PADDING;
		int innerWidth = panelWidth - Theme.PADDING * 2;
		int toolbarY = toolbarTop();

		int fieldY = toolbarY + (Theme.TOOLBAR_HEIGHT - Theme.SEARCH_FIELD_HEIGHT) / 2;
		searchField = new TextFieldWidget(textRenderer, inner, fieldY,
			innerWidth - QUANTITY_BLOCK - Theme.GUTTER, Theme.SEARCH_FIELD_HEIGHT,
			Text.translatable("chestradar.craft.search"));
		searchField.setDrawsBackground(false);
		searchField.setPlaceholder(Text.translatable("chestradar.craft.search")
			.styled(s -> s.withColor(Theme.rgb(Theme.TEXT_SECONDARY))));
		searchField.setChangedListener(text -> {
			if (syncing) {
				return;
			}
			query = text;
			current = null;
			planned = null;
			trail.clear();
			refreshMatches();
		});
		addDrawableChild(searchField);

		int quantityX = inner + innerWidth - QUANTITY_BLOCK;
		minusButton = new FlatButton(quantityX, fieldY, STEP_WIDTH, Theme.SEARCH_FIELD_HEIGHT,
			Text.literal("-"), b -> bump(-1, b.shiftHeld()));
		minusButton.setTooltip(Tooltip.of(Text.translatable("chestradar.craft.quantity_tip")));
		addDrawableChild(minusButton);

		quantityField = new TextFieldWidget(textRenderer, quantityX + STEP_WIDTH, fieldY,
			QUANTITY_FIELD_WIDTH, Theme.SEARCH_FIELD_HEIGHT,
			Text.translatable("chestradar.craft.quantity"));
		quantityField.setDrawsBackground(false);
		quantityField.setMaxLength(6);
		quantityField.setPlaceholder(Text.translatable("chestradar.craft.quantity")
			.styled(s -> s.withColor(Theme.rgb(Theme.TEXT_SECONDARY))));
		quantityField.setTextPredicate(text -> text.chars().allMatch(Character::isDigit));
		quantityField.setTooltip(Tooltip.of(Text.translatable("chestradar.craft.quantity_tip")));
		quantityField.setChangedListener(text -> {
			if (!syncing) {
				replan();
			}
		});
		addDrawableChild(quantityField);

		plusButton = new FlatButton(quantityX + STEP_WIDTH + QUANTITY_FIELD_WIDTH, fieldY,
			STEP_WIDTH, Theme.SEARCH_FIELD_HEIGHT, Text.literal("+"), b -> bump(1, b.shiftHeld()));
		plusButton.setTooltip(Tooltip.of(Text.translatable("chestradar.craft.quantity_tip")));
		addDrawableChild(plusButton);

		int footerButtonY = footerTop() + (Theme.FOOTER_HEIGHT - Theme.BUTTON_HEIGHT) / 2;
		backButton = new FlatButton(inner, footerButtonY, Theme.BUTTON_HEIGHT, Theme.BUTTON_HEIGHT,
			Text.literal("◀"), b -> back());
		addDrawableChild(backButton);

		routeButton = new FlatButton(inner + Theme.BUTTON_HEIGHT + Theme.GUTTER, footerButtonY,
			Theme.FOOTER_BUTTON_WIDTH + 20, Theme.BUTTON_HEIGHT,
			Text.translatable("chestradar.craft.route"), b -> startRoute());
		addDrawableChild(routeButton);

		addDrawableChild(new FlatButton(inner + innerWidth - Theme.FOOTER_BUTTON_WIDTH, footerButtonY,
			Theme.FOOTER_BUTTON_WIDTH, Theme.BUTTON_HEIGHT,
			Text.translatable("chestradar.craft.close"), b -> close()));

		if (initialQuery != null && !initialQuery.isBlank() && query.isEmpty()) {
			query = initialQuery;
		}
		syncing = true;
		searchField.setText(query);
		syncing = false;
		refreshMatches();

		if (current != null && planned != null) {
			syncing = true;
			quantityField.setText(String.valueOf(planned.plan().wantedOutput()));
			syncing = false;
		} else if (preset != null) {
			select(preset, presetWanted);
		}
		setInitialFocus(searchField);
		updateButtons();
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

	private int listWidth() {
		return panelWidth - Theme.PADDING * 2;
	}

	private void refreshMatches() {
		matches = RecipeCatalog.search(catalog, searchField == null ? "" : searchField.getText(),
			MAX_MATCHES);
		scroll = 0;
		updateButtons();
	}

	private int wanted() {
		String text = quantityField == null ? "" : quantityField.getText();
		if (text.isBlank()) {
			return current == null ? 1 : current.outputPerCraft();
		}
		try {
			return Math.clamp(Integer.parseInt(text), 1, CraftPlan.MAX_WANTED);
		} catch (NumberFormatException e) {
			return CraftPlan.MAX_WANTED;
		}
	}

	private void bump(int direction, boolean stack) {
		if (current == null) {
			return;
		}
		int step = stack ? current.stackSize() : current.outputPerCraft();
		long next = (long) wanted() + (long) direction * step;
		setWanted((int) Math.clamp(next, 1L, (long) CraftPlan.MAX_WANTED));
	}

	private void setWanted(int value) {
		syncing = true;
		quantityField.setText(String.valueOf(Math.max(1, value)));
		syncing = false;
		replan();
	}

	private boolean shiftDown() {
		return client != null
			&& (InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
				|| InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT));
	}

	private boolean overQuantity(double mouseX, double mouseY) {
		if (minusButton == null || plusButton == null || quantityField == null) {
			return false;
		}
		return mouseX >= minusButton.getX() && mouseX <= plusButton.getRight()
			&& mouseY >= quantityField.getY() - 2
			&& mouseY <= quantityField.getY() + quantityField.getHeight() + 2;
	}

	private void select(RecipeCatalog.Entry entry, int wantedOutput) {
		current = entry;
		syncing = true;
		quantityField.setText(String.valueOf(Math.max(1, wantedOutput)));
		syncing = false;
		replan();
		scroll = 0;
	}

	private void replan() {
		planned = current == null ? null : CraftPlanner.plan(client, current, wanted());
		updateButtons();
	}

	private void updateButtons() {
		boolean fetchable = planned != null
			&& planned.plan().needs().stream().anyMatch(need -> need.toFetch() > 0);
		if (routeButton != null) {
			routeButton.active = RadarState.config().routeEnabled && fetchable;
			routeButton.setTooltip(Tooltip.of(routeHint(fetchable)));
		}
		if (minusButton != null) {
			minusButton.active = current != null;
		}
		if (plusButton != null) {
			plusButton.active = current != null;
		}
		if (backButton != null) {
			backButton.active = !trail.isEmpty();
			backButton.visible = !trail.isEmpty();
		}
	}

	private Text routeHint(boolean fetchable) {
		if (!RadarState.config().routeEnabled) {
			return Text.translatable("chestradar.craft.route_tip.disabled");
		}
		if (planned == null) {
			return Text.translatable("chestradar.craft.route_tip.pick");
		}
		if (fetchable) {
			return Text.translatable("chestradar.craft.route_tip.ready");
		}
		return planned.plan().craftableNow()
			? Text.translatable("chestradar.craft.route_tip.in_hand")
			: Text.translatable("chestradar.craft.route_tip.nowhere");
	}

	private void back() {
		Step step = trail.pollLast();
		if (step != null) {
			select(step.entry(), step.wanted());
		}
		updateButtons();
	}

	private void descend(CraftPlan.Need need) {
		for (String itemId : need.ingredient().itemIds()) {
			RecipeCatalog.Entry sub = RecipeCatalog.forItemId(catalog, itemId);
			if (sub == null) {
				continue;
			}
			if (current != null) {
				trail.addLast(new Step(current, wanted()));
			}
			int target = need.missing() > 0 ? need.missing() : need.needed();
			select(sub, target);
			return;
		}
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.translatable("chestradar.craft.no_sub_recipe",
				need.ingredient().label()).formatted(Formatting.GRAY), false);
		}
	}

	private void goTo(CraftPlan.Need need) {
		Hit nearest = planned == null ? null : planned.nearest(need.ingredient());
		if (nearest == null) {
			return;
		}
		leaveContainerScreen();
		ContainerFocus.focus(client, planned.hitsOf(need.ingredient()), nearest.container().key(),
			need.ingredient().label(), nearest.totalCount());
	}

	private void startRoute() {
		if (planned == null) {
			return;
		}
		var route = CraftPlanner.route(client, planned);
		String goal = planned.plan().wantedOutput() + " " + planned.plan().outputLabel();
		leaveContainerScreen();
		if (client != null) {
			client.setScreen(null);
		}
		if (!RadarRoute.start(client, route, goal) && client != null && client.player != null) {
			client.player.sendMessage(Text.translatable("chestradar.route.none")
				.formatted(Formatting.GRAY), false);
		}
	}

	private void leaveContainerScreen() {
		if (parent instanceof HandledScreen<?> && client != null && client.player != null) {
			client.player.closeHandledScreen();
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		Panel.backdrop(context, width, height);
		Panel.frame(context, panelX, panelY, panelWidth, panelHeight);
		Panel.badge(context, BADGE, panelX + Theme.PADDING, panelY);
		Panel.masthead(context, textRenderer, title, Text.literal(headerSummary()), panelX, panelY,
			panelWidth, Theme.ICON);

		drawSectionSeparator(context, headerSeparatorY());
		drawSectionSeparator(context, toolbarSeparatorY());
		drawSectionSeparator(context, footerSeparatorY());

		super.render(context, mouseX, mouseY, delta);

		int underlineY = searchField.getY() + searchField.getHeight() + 2;
		context.fill(searchField.getX(), underlineY, searchField.getX() + searchField.getWidth(),
			underlineY + 1, searchField.isFocused() ? Theme.ACCENT : Theme.BORDER);
		context.fill(quantityField.getX(), underlineY,
			quantityField.getX() + quantityField.getWidth(), underlineY + 1,
			quantityField.isFocused() ? Theme.ACCENT : Theme.BORDER);

		context.enableScissor(panelX + Theme.PADDING, listTop(),
			panelX + panelWidth - Theme.PADDING, listTop() + listHeight());
		if (planned != null) {
			renderPlan(context, mouseX, mouseY);
		} else {
			renderMatches(context, mouseX, mouseY);
		}
		context.disableScissor();

		if (planned != null) {
			CraftPlan.Need hovered = needAt(mouseX, mouseY);
			if (hovered != null) {
				renderNeedTooltip(context, hovered, mouseX, mouseY);
			}
			return;
		}
		RecipeCatalog.Entry match = matchAt(mouseX, mouseY);
		if (match != null) {
			renderMatchTooltip(context, match, mouseX, mouseY);
		}
	}

	private String headerSummary() {
		if (planned == null) {
			return catalog.isEmpty() ? "" : matches.size() + " / " + catalog.size();
		}
		CraftPlan.Plan plan = planned.plan();
		String station = Text.translatable("chestradar.craft.station." + planned.entry().station().name())
			.getString();
		return station + "  ·  " + Text.translatable("chestradar.craft.crafts", plan.crafts(),
			CompactCount.format(plan.producedOutput())).getString();
	}

	private void renderMatches(DrawContext context, int mouseX, int mouseY) {
		int x = panelX + Theme.PADDING;
		int width = listWidth();
		if (catalog.isEmpty()) {
			center(context, Text.translatable("chestradar.craft.no_recipes"));
			return;
		}
		if (matches.isEmpty()) {
			center(context, Text.translatable("chestradar.craft.no_match"));
			return;
		}
		scroll = Math.clamp(scroll, 0, maxScroll());
		int cursor = listTop() - (int) scroll;
		for (RecipeCatalog.Entry entry : matches) {
			if (cursor + RadarTheme.ROW_HEIGHT >= listTop() && cursor <= listTop() + listHeight()) {
				boolean hovered = mouseX >= x && mouseX <= x + width
					&& mouseY >= cursor && mouseY < cursor + RadarTheme.ROW_HEIGHT;
				if (hovered) {
					context.fill(x, cursor, x + width, cursor + RadarTheme.ROW_HEIGHT, Theme.ROW_HOVER);
				}
				drawStack(context, entry.output(), x + 2, cursor + 2);
				String right = Text.translatable("chestradar.craft.station." + entry.station().name())
					.getString() + "  ·  x" + entry.outputPerCraft();
				int rightWidth = textRenderer.getWidth(right);
				context.drawText(textRenderer, right, x + width - rightWidth, cursor + 6,
					Theme.TEXT_SECONDARY, false);
				context.drawText(textRenderer, textRenderer.trimToWidth(entry.label(),
						width - rightWidth - Theme.ICON - 12), x + Theme.ICON + 6, cursor + 6,
					hovered ? Theme.ACCENT : Theme.TEXT_PRIMARY, false);
			}
			cursor += RadarTheme.ROW_HEIGHT;
		}
	}

	private void renderPlan(DrawContext context, int mouseX, int mouseY) {
		int x = panelX + Theme.PADDING;
		int width = listWidth();
		scroll = Math.clamp(scroll, 0, maxScroll());
		int cursor = listTop() - (int) scroll;

		drawStack(context, planned.entry().output(), x + 2, cursor + 2);
		String head = planned.plan().outputLabel();
		context.drawText(textRenderer, head, x + Theme.ICON + 6, cursor + 6, Theme.ACCENT, false);
		String state = planned.plan().craftableNow()
			? Text.translatable("chestradar.craft.ready").getString()
			: planned.plan().complete()
				? Text.translatable("chestradar.craft.gatherable").getString()
				: Text.translatable("chestradar.craft.blocked", planned.plan().missingKinds()).getString();
		int stateWidth = textRenderer.getWidth(state);
		context.drawText(textRenderer, state, x + width - stateWidth, cursor + 6,
			planned.plan().craftableNow() ? RadarTheme.PROGRESS_DONE
				: planned.plan().complete() ? Theme.ACCENT : RadarTheme.MISSING, false);
		cursor += RadarTheme.ROW_HEIGHT + 2;
		context.fill(x, cursor - 2, x + width, cursor - 1, Theme.BORDER);

		for (CraftPlan.Need need : planned.plan().needs()) {
			if (cursor + RadarTheme.ROW_HEIGHT >= listTop() && cursor <= listTop() + listHeight()) {
				boolean hovered = mouseX >= x && mouseX <= x + width
					&& mouseY >= cursor && mouseY < cursor + RadarTheme.ROW_HEIGHT;
				renderNeed(context, need, x, cursor, width, hovered);
			}
			cursor += RadarTheme.ROW_HEIGHT;
		}
	}

	private void renderNeed(DrawContext context, CraftPlan.Need need, int x, int y, int width,
			boolean hovered) {
		if (hovered) {
			context.fill(x, y, x + width, y + RadarTheme.ROW_HEIGHT, Theme.ROW_HOVER);
		}
		drawStack(context, planned.entry().iconFor(need.ingredient()), x + 2, y + 2);

		int right = x + width;
		int baseline = y + 6;

		if (need.missing() > 0) {
			right = drawSegment(context, Text.translatable("chestradar.craft.missing",
				CompactCount.format(need.missing())).getString(), right, baseline, RadarTheme.MISSING, x);
		}
		if (need.toFetch() > 0) {
			Hit nearest = planned.nearest(need.ingredient());
			String label = Text.translatable("chestradar.craft.to_fetch",
				CompactCount.format(need.toFetch())).getString();
			if (nearest != null) {
				label += "  " + (int) nearest.distance() + "m " + Bearing.toward(client,
					nearest.container().key());
			}
			right = drawSegment(context, label, right, baseline, Theme.ACCENT, x);
		}
		if (need.inInventory() > 0) {
			right = drawSegment(context, Text.translatable("chestradar.craft.carried",
					CompactCount.format(Math.min(need.inInventory(), need.needed()))).getString(),
				right, baseline, need.inHand() ? RadarTheme.PROGRESS_DONE : Theme.TEXT_SECONDARY, x);
		}
		right = drawSegment(context, "x" + String.format(Locale.ROOT, "%,d", need.needed()),
			right, baseline, need.covered() ? Theme.TEXT_SECONDARY : Theme.TEXT_PRIMARY, x);

		int nameX = x + Theme.ICON + 6;
		String name = need.ingredient().label();
		if (need.ingredient().itemIds().size() > 1) {
			name += " (+" + (need.ingredient().itemIds().size() - 1) + ")";
		}
		context.drawText(textRenderer, textRenderer.trimToWidth(name, Math.max(0, right - nameX - 6)),
			nameX, baseline, hovered ? Theme.ACCENT : Theme.TEXT_PRIMARY, false);
	}

	private int drawSegment(DrawContext context, String text, int right, int y, int color, int left) {
		int textWidth = textRenderer.getWidth(text);
		if (right - textWidth < left + Theme.ICON * 3) {
			return right;
		}
		context.drawText(textRenderer, text, right - textWidth, y, color, false);
		return right - textWidth - Theme.GUTTER;
	}

	private void renderMatchTooltip(DrawContext context, RecipeCatalog.Entry entry, int mouseX,
			int mouseY) {
		List<Text> lines = new ArrayList<>();
		lines.add(Text.literal(entry.label()).formatted(Formatting.WHITE));
		lines.add(Text.translatable("chestradar.craft.station." + entry.station().name())
			.append("  ·  x" + entry.outputPerCraft()).formatted(Formatting.GRAY));
		lines.add(Text.translatable("chestradar.craft.match_hint", entry.stackSize())
			.formatted(Formatting.DARK_GRAY));
		context.drawTooltip(textRenderer, lines, mouseX, mouseY);
	}

	private void renderNeedTooltip(DrawContext context, CraftPlan.Need need, int mouseX, int mouseY) {
		List<Text> lines = new ArrayList<>();
		lines.add(Text.literal(need.ingredient().label()).formatted(Formatting.WHITE));
		lines.add(Text.translatable("chestradar.craft.tooltip_need",
			need.ingredient().perCraft(), need.needed()).formatted(Formatting.GRAY));
		List<Hit> hits = planned.hitsOf(need.ingredient());
		if (hits.isEmpty()) {
			lines.add(Text.translatable("chestradar.craft.tooltip_nowhere").formatted(Formatting.DARK_GRAY));
		} else {
			int shown = Math.min(hits.size(), TOOLTIP_HITS);
			for (int i = 0; i < shown; i++) {
				Hit hit = hits.get(i);
				lines.add(Text.literal("  " + hit.container().title() + "  x"
					+ CompactCount.format(hit.totalCount()) + "  " + (int) hit.distance() + "m")
					.formatted(Formatting.GRAY));
			}
			if (hits.size() > shown) {
				lines.add(Text.translatable("chestradar.craft.tooltip_more", hits.size() - shown)
					.formatted(Formatting.DARK_GRAY));
			}
		}
		lines.add(Text.translatable("chestradar.craft.row_hint").formatted(Formatting.DARK_GRAY));
		context.drawTooltip(textRenderer, lines, mouseX, mouseY);
	}

	private void drawStack(DrawContext context, ItemStack stack, int x, int y) {
		try {
			context.drawItem(stack, x, y);
		} catch (Exception e) {
			context.fill(x + 3, y + 5, x + Theme.ICON - 3, y + 11, Theme.BORDER);
			if (!tooltipFailureLogged) {
				tooltipFailureLogged = true;
				ChestRadar.LOGGER.warn("[chestradar] rendu d'icône impossible dans le planificateur", e);
			}
		}
	}

	private void center(DrawContext context, Text text) {
		context.drawText(textRenderer, text,
			panelX + (panelWidth - textRenderer.getWidth(text)) / 2,
			listTop() + listHeight() / 2 - 4, Theme.TEXT_SECONDARY, false);
	}

	private void drawSectionSeparator(DrawContext context, int y) {
		Panel.separator(context, panelX, y, panelWidth);
	}

	private int contentHeight() {
		if (planned != null) {
			return RadarTheme.ROW_HEIGHT + 2 + planned.plan().needs().size() * RadarTheme.ROW_HEIGHT;
		}
		return matches.size() * RadarTheme.ROW_HEIGHT;
	}

	private int maxScroll() {
		return Math.max(0, contentHeight() - listHeight());
	}

	private CraftPlan.Need needAt(double mouseX, double mouseY) {
		if (planned == null || mouseX < panelX + Theme.PADDING
			|| mouseX > panelX + panelWidth - Theme.PADDING
			|| mouseY < listTop() || mouseY > listTop() + listHeight()) {
			return null;
		}
		int cursor = listTop() - (int) scroll + RadarTheme.ROW_HEIGHT + 2;
		for (CraftPlan.Need need : planned.plan().needs()) {
			if (mouseY >= cursor && mouseY < cursor + RadarTheme.ROW_HEIGHT) {
				return need;
			}
			cursor += RadarTheme.ROW_HEIGHT;
		}
		return null;
	}

	private RecipeCatalog.Entry matchAt(double mouseX, double mouseY) {
		if (planned != null || mouseX < panelX + Theme.PADDING
			|| mouseX > panelX + panelWidth - Theme.PADDING
			|| mouseY < listTop() || mouseY > listTop() + listHeight()) {
			return null;
		}
		int cursor = listTop() - (int) scroll;
		for (RecipeCatalog.Entry entry : matches) {
			if (mouseY >= cursor && mouseY < cursor + RadarTheme.ROW_HEIGHT) {
				return entry;
			}
			cursor += RadarTheme.ROW_HEIGHT;
		}
		return null;
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubleClick) {
		int button = click.button();
		RecipeCatalog.Entry match = matchAt(click.x(), click.y());
		if (match != null && button == InputUtil.GLFW_MOUSE_BUTTON_LEFT) {
			select(match, click.hasShift() ? match.stackSize() : match.outputPerCraft());
			return true;
		}
		CraftPlan.Need need = needAt(click.x(), click.y());
		if (need != null) {
			if (button == InputUtil.GLFW_MOUSE_BUTTON_LEFT) {
				goTo(need);
				return true;
			}
			if (button == InputUtil.GLFW_MOUSE_BUTTON_RIGHT) {
				descend(need);
				return true;
			}
		}
		return super.mouseClicked(click, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (vertical != 0 && current != null && overQuantity(mouseX, mouseY)) {
			bump(vertical > 0 ? 1 : -1, shiftDown());
			return true;
		}
		if (mouseX >= panelX && mouseX <= panelX + panelWidth
			&& mouseY >= listTop() && mouseY <= listTop() + listHeight()) {
			scroll = Math.clamp(scroll - vertical * RadarTheme.ROW_HEIGHT, 0, maxScroll());
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	@Override
	public void close() {
		if (client == null) {
			return;
		}
		client.setScreen(parent);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
