package fr.imero.chestradar.client.screen;

import fr.imero.chestradar.ChestRadar;
import fr.imero.chestradar.client.ContainerFocus;
import fr.imero.chestradar.client.RadarRoute;
import fr.imero.chestradar.client.RadarState;
import fr.imero.chestradar.client.craft.CraftPlanner;
import fr.imero.chestradar.client.craft.RecipeCatalog;
import fr.imero.chestradar.search.CompactCount;
import fr.imero.chestradar.search.CraftPlan;
import fr.imero.chestradar.search.Hit;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.components.Tooltip;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class CraftPlanScreen extends Screen {
	private static final int MAX_MATCHES = 80;
	private static final int TOOLTIP_HITS = 6;
	private static final int MAX_HEIGHT = 370;
	private static final int QUANTITY_FIELD = 30;
	private static final int SUMMARY = 34;
	private static final int ROUTE_CARD = 34;

	private final Screen parent;
	private final String initialQuery;
	private final RecipeCatalog.Entry preset;
	private final int presetWanted;

	private List<RecipeCatalog.Entry> catalog = List.of();
	private List<RecipeCatalog.Entry> matches = List.of();

	private FlatField searchField;
	private FlatField quantityField;
	private FlatButton minusButton;
	private FlatButton plusButton;
	private FlatButton routeButton;
	private FlatButton backButton;

	private RecipeCatalog.Entry current;
	private CraftPlanner.Planned planned;
	private int routeStops;
	private final Deque<Step> trail = new ArrayDeque<>();

	private String query = "";
	private double scroll;
	private boolean syncing;
	private boolean tooltipFailureLogged;

	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int quantityX;

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
		super(Component.translatable("chestradar.craft.title"));
		this.parent = parent;
		this.initialQuery = initialQuery;
		this.preset = preset;
		this.presetWanted = presetWanted;
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
		if (catalog.isEmpty()) {
			catalog = RecipeCatalog.build(minecraft);
		}
		panelWidth = Math.clamp(width - Theme.SPACE_5 * 2, Math.min(260, width), Theme.NARROW);
		panelHeight = Math.clamp(height - Theme.SPACE_5 * 2, Math.min(220, height), MAX_HEIGHT);
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;

		int right = panelX + panelWidth - Theme.PADDING;
		Component closeLabel = Component.translatable("chestradar.craft.close");
		int closeWidth = font.width(closeLabel) + 20;
		addRenderableWidget(new FlatButton(right - closeWidth, panelY + 10, closeWidth, 20, closeLabel, b -> onClose()));
		Component backLabel = Component.translatable("chestradar.craft.back");
		int backWidth = font.width(backLabel) + 20;
		backButton = new FlatButton(right - closeWidth - 5 - backWidth, panelY + 10, backWidth, 20, backLabel,
			b -> back());
		addRenderableWidget(backButton);

		int toolY = toolbarTop();
		int quantityLabel = font.width(Component.translatable("chestradar.craft.quantity"));
		int quantityWidth = 6 + quantityLabel + 4 + step() * 2 + QUANTITY_FIELD + 2;
		quantityX = right - quantityWidth;
		searchField = new FlatField(font, panelX + Theme.PADDING, toolY, quantityX - 6 - panelX - Theme.PADDING, Theme.CONTROL,
			Component.translatable("chestradar.craft.search"));
		searchField.setHint(Component.translatable("chestradar.craft.search")
			.withStyle(s -> s.withColor(Theme.rgb(Theme.TEXT_SECONDARY))));
		searchField.setResponder(text -> {
			if (syncing) {
				return;
			}
			query = text;
			current = null;
			planned = null;
			trail.clear();
			refreshMatches();
		});
		addRenderableWidget(searchField);

		int stepX = quantityX + 6 + quantityLabel + 4;
		minusButton = new FlatButton(stepX, toolY + Theme.SPACE_1, step(), step(), Component.literal("-"), b -> bump(-1, b.shiftHeld()));
		minusButton.setTooltip(Tooltip.create(Component.translatable("chestradar.craft.quantity_tip")));
		addRenderableWidget(minusButton);

		quantityField = new FlatField(font, stepX + step(), toolY, QUANTITY_FIELD, Theme.CONTROL,
			Component.translatable("chestradar.craft.quantity"));
		quantityField.quiet(true);
		quantityField.setMaxLength(6);
		quantityField.setHint(Component.literal("1"));
		quantityField.setTooltip(Tooltip.create(Component.translatable("chestradar.craft.quantity_tip")));
		quantityField.setResponder(text -> {
			String digits = text.replaceAll("[^0-9]", "");
			if (!digits.equals(text)) {
				quantityField.setValue(digits);
				return;
			}
			if (!syncing) {
				replan();
			}
		});
		addRenderableWidget(quantityField);

		plusButton = new FlatButton(stepX + step() + QUANTITY_FIELD, toolY + Theme.SPACE_1, step(), step(), Component.literal("+"),
			b -> bump(1, b.shiftHeld()));
		plusButton.setTooltip(Tooltip.create(Component.translatable("chestradar.craft.quantity_tip")));
		addRenderableWidget(plusButton);

		Component routeLabel = Component.translatable("chestradar.craft.route");
		int routeWidth = font.width(routeLabel) + 24;
		routeButton = new FlatButton(right - 8 - routeWidth, routeCardTop() + (ROUTE_CARD - Theme.CONTROL) / 2, routeWidth,
			Theme.CONTROL, routeLabel, b -> startRoute()).primary();
		addRenderableWidget(routeButton);

		if (initialQuery != null && !initialQuery.isBlank() && query.isEmpty()) {
			query = initialQuery;
		}
		syncing = true;
		searchField.setValue(query);
		syncing = false;
		refreshMatches();

		if (current != null && planned != null) {
			syncing = true;
			quantityField.setValue(String.valueOf(planned.plan().wantedOutput()));
			syncing = false;
		} else if (preset != null) {
			select(preset, presetWanted);
		}
		setInitialFocus(searchField);
		updateButtons();
	}

	private int toolbarTop() {
		return panelY + 38;
	}

	private int contentTop() {
		return toolbarTop() + Theme.CONTROL + 10;
	}

	private int routeCardTop() {
		return panelY + panelHeight - Theme.PADDING - ROUTE_CARD;
	}

	private int hintY() {
		return routeCardTop() - 14;
	}

	private int listTop() {
		return planned != null ? contentTop() + SUMMARY + 8 : contentTop();
	}

	private int listBottom() {
		return planned != null ? hintY() - 6 : routeCardTop() - 8;
	}

	private int listHeight() {
		return listBottom() - listTop();
	}

	private int left() {
		return panelX + Theme.PADDING;
	}

	private int listWidth() {
		return panelWidth - Theme.PADDING * 2;
	}

	private void refreshMatches() {
		matches = RecipeCatalog.search(catalog, searchField == null ? "" : searchField.getValue(),
			MAX_MATCHES);
		scroll = 0;
		updateButtons();
	}

	private int wanted() {
		String text = quantityField == null ? "" : quantityField.getValue();
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
		quantityField.setValue(String.valueOf(Math.max(1, value)));
		syncing = false;
		replan();
	}

	private boolean shiftDown() {
		return minecraft != null
			&& (InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
				|| InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT));
	}

	private boolean overQuantity(double mouseX, double mouseY) {
		return mouseX >= quantityX && mouseX <= panelX + panelWidth - Theme.PADDING
			&& mouseY >= toolbarTop() && mouseY < toolbarTop() + Theme.CONTROL;
	}

	private void select(RecipeCatalog.Entry entry, int wantedOutput) {
		current = entry;
		syncing = true;
		quantityField.setValue(String.valueOf(Math.max(1, wantedOutput)));
		syncing = false;
		replan();
		scroll = 0;
	}

	private void replan() {
		planned = current == null ? null : CraftPlanner.plan(minecraft, current, wanted());
		routeStops = fetchable() && RadarState.config().routeEnabled ? CraftPlanner.route(minecraft, planned).size() : 0;
		updateButtons();
	}

	private boolean fetchable() {
		return planned != null && planned.plan().needs().stream().anyMatch(need -> need.toFetch() > 0);
	}

	private void updateButtons() {
		boolean fetchable = fetchable();
		if (routeButton != null) {
			routeButton.active = RadarState.config().routeEnabled && fetchable;
			routeButton.setTooltip(Tooltip.create(routeHint(fetchable)));
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

	private Component routeHint(boolean fetchable) {
		if (!RadarState.config().routeEnabled) {
			return Component.translatable("chestradar.craft.route_tip.disabled");
		}
		if (planned == null) {
			return Component.translatable("chestradar.craft.route_tip.pick");
		}
		if (fetchable) {
			return Component.translatable("chestradar.craft.route_tip.ready");
		}
		return planned.plan().craftableNow()
			? Component.translatable("chestradar.craft.route_tip.in_hand")
			: Component.translatable("chestradar.craft.route_tip.nowhere");
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
		if (minecraft != null && minecraft.player != null) {
			minecraft.player.sendSystemMessage(Component.translatable("chestradar.craft.no_sub_recipe",
				need.ingredient().label()).withStyle(ChatFormatting.GRAY));
		}
	}

	private void goTo(CraftPlan.Need need) {
		Hit nearest = planned == null ? null : planned.nearest(need.ingredient());
		if (nearest == null) {
			return;
		}
		leaveContainerScreen();
		ContainerFocus.focus(minecraft, planned.hitsOf(need.ingredient()), nearest.container().key(),
			need.ingredient().label(), nearest.totalCount());
	}

	private void startRoute() {
		if (planned == null) {
			return;
		}
		var route = CraftPlanner.route(minecraft, planned);
		String goal = planned.plan().wantedOutput() + " " + planned.plan().outputLabel();
		leaveContainerScreen();
		if (minecraft != null) {
			minecraft.setScreenAndShow(null);
		}
		if (!RadarRoute.start(minecraft, route, goal) && minecraft != null && minecraft.player != null) {
			minecraft.player.sendSystemMessage(Component.translatable("chestradar.route.none")
				.withStyle(ChatFormatting.GRAY));
		}
	}

	private void leaveContainerScreen() {
		if (parent instanceof AbstractContainerScreen<?> && minecraft != null && minecraft.player != null) {
			minecraft.player.closeContainer();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		RadarTheme.sync();
		Panel.backdrop(context, width, height);
		Shape.window(context, panelX, panelY, panelX + panelWidth, panelY + panelHeight);
		drawTitle(context, panelX + Theme.PADDING + 2, panelY + 12);
		Shape.well(context, quantityX, toolbarTop(), panelX + panelWidth - Theme.PADDING, toolbarTop() + Theme.CONTROL);
		context.text(font, Component.translatable("chestradar.craft.quantity"), quantityX + 6,
			toolbarTop() + (Theme.CONTROL - 8) / 2, Theme.TEXT_SECONDARY, false);
		if (planned != null) {
			renderSummary(context);
			context.text(font, Component.translatable("chestradar.craft.row_hint"), left(), hintY(),
				Theme.TEXT_SECONDARY, false);
		}
		renderRouteCard(context, mouseX, mouseY);

		super.extractRenderState(context, mouseX, mouseY, delta);

		context.enableScissor(left(), listTop(), left() + listWidth(), listBottom());
		if (planned != null) {
			renderNeeds(context, mouseX, mouseY);
		} else {
			renderMatches(context, mouseX, mouseY);
		}
		context.disableScissor();
		Panel.scrollbar(context, panelX + panelWidth - Theme.PADDING + 4, listTop(), listHeight(), contentHeight(), scroll);

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

	private static int step() {
		return Theme.CONTROL - Theme.SPACE_1 * 2;
	}

	private void drawTitle(GuiGraphicsExtractor context, int x, int y) {
		Type.X2.draw(context, font, title, x, y, Theme.TEXT_PRIMARY);
	}

	private void renderSummary(GuiGraphicsExtractor context) {
		int x0 = left();
		int y0 = contentTop();
		int x1 = x0 + listWidth();
		int y1 = y0 + SUMMARY;
		Shape.fill(context, x0, y0, x1, y1, Theme.TILE_RADIUS, Theme.alpha(Theme.ACCENT, 0x14));
		Shape.stroke(context, x0, y0, x1, y1, Theme.TILE_RADIUS, 1, Theme.alpha(Theme.ACCENT, 0x4D));
		drawStack(context, planned.entry().output(), x0 + 9, y0 + 9);

		CraftPlan.Plan plan = planned.plan();
		Component state;
		int stateColor;
		if (plan.craftableNow()) {
			state = Component.translatable("chestradar.craft.ready");
			stateColor = RadarTheme.PROGRESS_DONE;
		} else if (plan.complete()) {
			state = Component.translatable("chestradar.craft.gatherable");
			stateColor = Theme.ACCENT;
		} else {
			state = Component.translatable("chestradar.craft.blocked", plan.missingKinds());
			stateColor = Theme.ALERT;
		}
		Component pill = state;
		int pillWidth = font.width(pill) + 12;
		int pillRight = x1 - 8;
		Shape.pill(context, pillRight - pillWidth, y0 + 5, pillRight, y0 + 17, stateColor);
		context.text(font, pill, pillRight - pillWidth + 6, y0 + 7, Theme.ON_ACCENT, false);

		int textX = x0 + 32;
		int room = pillRight - pillWidth - 8 - textX;
		int wide = x1 - 8 - textX;
		String head = plan.outputLabel() + " ×" + plan.wantedOutput();
		context.text(font, Fit.text(font, head, room), textX, y0 + 7, Theme.TEXT_PRIMARY, false);
		String station = Component.translatable("chestradar.craft.station." + planned.entry().station().name())
			.getString() + "  ·  " + Component.translatable("chestradar.craft.crafts", plan.crafts(),
				CompactCount.format(plan.producedOutput())).getString();
		context.text(font, Fit.text(font, station, wide), textX, y0 + 19, Theme.TEXT_SECONDARY,
			false);
	}

	private void renderRouteCard(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		int x0 = left();
		int y0 = routeCardTop();
		int x1 = x0 + listWidth();
		Shape.tile(context, x0, y0, x1, y0 + ROUTE_CARD);
		int room = routeButton.getX() - 8 - x0 - 9;
		context.text(font, Component.translatable("chestradar.craft.route_card"), x0 + 9, y0 + 7,
			Theme.TEXT_PRIMARY, false);
		Component detail = routeStops > 0 ? Component.translatable("chestradar.craft.route_stops", routeStops)
			: routeHint(fetchable());
		Fit.draw(context, font, detail, x0 + 9, y0 + 19, room, Theme.TEXT_SECONDARY, mouseX, mouseY);
	}

	private void renderMatches(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		int x = left();
		int width = listWidth();
		if (catalog.isEmpty()) {
			center(context, Component.translatable("chestradar.craft.no_recipes"));
			return;
		}
		if (matches.isEmpty()) {
			center(context, Component.translatable("chestradar.craft.no_match"));
			return;
		}
		scroll = Math.clamp(scroll, 0, maxScroll());
		int cursor = listTop() - (int) scroll;
		for (RecipeCatalog.Entry entry : matches) {
			if (cursor + Theme.ROW >= listTop() && cursor <= listBottom()) {
				boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= cursor && mouseY < cursor + Theme.ROW
					&& mouseY >= listTop() && mouseY < listBottom();
				if (hovered) {
					Shape.fill(context, x, cursor, x + width, cursor + Theme.ROW, Theme.TILE_RADIUS, Theme.TILE);
				}
				drawStack(context, entry.output(), x + 3, cursor + 2);
				Component right = Component.literal(Component.translatable("chestradar.craft.station." + entry.station().name())
					.getString() + "  ·  ×" + entry.outputPerCraft());
				int rightWidth = font.width(right);
				context.text(font, right, x + width - 6 - rightWidth, cursor + 7, Theme.TEXT_SECONDARY, false);
				context.text(font, Fit.text(font, entry.label(), width - rightWidth - Theme.ICON - 18),
					x + Theme.ICON + 8, cursor + 6, hovered ? Theme.ACCENT : Theme.TEXT_PRIMARY, false);
			}
			cursor += Theme.ROW;
		}
	}

	private void renderNeeds(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		int x = left();
		int width = listWidth();
		scroll = Math.clamp(scroll, 0, maxScroll());
		int cursor = listTop() - (int) scroll;
		for (CraftPlan.Need need : planned.plan().needs()) {
			if (cursor + Theme.CONTROL_LARGE >= listTop() && cursor <= listBottom()) {
				boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= cursor && mouseY < cursor + Theme.CONTROL_LARGE
					&& mouseY >= listTop() && mouseY < listBottom();
				renderNeed(context, need, x, cursor, width, hovered);
			}
			cursor += Theme.CONTROL_LARGE + Theme.SPACE_2;
		}
	}

	private void renderNeed(GuiGraphicsExtractor context, CraftPlan.Need need, int x, int y, int width,
			boolean hovered) {
		Shape.fill(context, x, y, x + width, y + Theme.CONTROL_LARGE, Theme.TILE_RADIUS,
			hovered ? Theme.mix(Theme.TILE, Theme.TEXT_PRIMARY, 0.07) : Theme.TILE);
		drawStack(context, planned.entry().iconFor(need.ingredient()), x + 5, y + 4);

		int right = x + width - 6;
		int pillY = y + 6;
		int minLeft = x + Theme.ICON + 60;
		if (need.missing() > 0) {
			right = pill(context, Component.translatable("chestradar.craft.missing",
				CompactCount.format(need.missing())), right, pillY, Theme.alpha(Theme.ALERT, 0x2E),
				Theme.mix(Theme.ALERT, 0xFFFFFFFF, 0.25), minLeft);
		}
		if (need.toFetch() > 0) {
			right = pill(context, Component.translatable("chestradar.craft.to_fetch",
				CompactCount.format(need.toFetch())), right, pillY, Theme.WINDOW, Theme.ACCENT, minLeft);
		}
		if (need.inInventory() > 0) {
			right = pill(context, Component.translatable("chestradar.craft.carried",
				CompactCount.format(Math.min(need.inInventory(), need.needed()))), right, pillY, Theme.WINDOW,
				need.inHand() ? RadarTheme.PROGRESS_DONE : Theme.mix(Theme.TEXT_PRIMARY, Theme.TEXT_SECONDARY, 0.3),
				minLeft);
		}

		int nameX = x + Theme.ICON + 10;
		String name = need.ingredient().label();
		if (need.ingredient().itemIds().size() > 1) {
			name += " (+" + (need.ingredient().itemIds().size() - 1) + ")";
		}
		String amount = " ×" + CompactCount.format(need.needed());
		int room = Math.max(0, right - nameX - 6 - font.width(amount));
		String shown = Fit.text(font, name, room);
		context.text(font, shown, nameX, y + 8, hovered ? Theme.ACCENT : Theme.TEXT_PRIMARY, false);
		context.text(font, amount, nameX + font.width(shown), y + 8, Theme.TEXT_SECONDARY, false);
	}

	private int pill(GuiGraphicsExtractor context, Component text, int right, int y, int background, int color,
			int minLeft) {
		Component label = text;
		int pillWidth = font.width(label) + 12;
		if (right - pillWidth < minLeft) {
			return right;
		}
		Shape.pill(context, right - pillWidth, y, right, y + 12, background);
		context.text(font, label, right - pillWidth + 6, y + 2, color, false);
		return right - pillWidth - 4;
	}

	private void renderMatchTooltip(GuiGraphicsExtractor context, RecipeCatalog.Entry entry, int mouseX,
			int mouseY) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal(entry.label()).withStyle(ChatFormatting.WHITE));
		lines.add(Component.translatable("chestradar.craft.station." + entry.station().name())
			.append("  ·  x" + entry.outputPerCraft()).withStyle(ChatFormatting.GRAY));
		lines.add(Component.translatable("chestradar.craft.match_hint", entry.stackSize())
			.withStyle(ChatFormatting.DARK_GRAY));
		context.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
	}

	private void renderNeedTooltip(GuiGraphicsExtractor context, CraftPlan.Need need, int mouseX, int mouseY) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal(need.ingredient().label()).withStyle(ChatFormatting.WHITE));
		lines.add(Component.translatable("chestradar.craft.tooltip_need",
			need.ingredient().perCraft(), need.needed()).withStyle(ChatFormatting.GRAY));
		List<Hit> hits = planned.hitsOf(need.ingredient());
		if (hits.isEmpty()) {
			lines.add(Component.translatable("chestradar.craft.tooltip_nowhere").withStyle(ChatFormatting.DARK_GRAY));
		} else {
			int shown = Math.min(hits.size(), TOOLTIP_HITS);
			for (int i = 0; i < shown; i++) {
				Hit hit = hits.get(i);
				lines.add(Component.literal("  " + hit.container().title() + "  x"
					+ CompactCount.format(hit.totalCount()) + "  " + (int) hit.distance() + "m")
					.withStyle(ChatFormatting.GRAY));
			}
			if (hits.size() > shown) {
				lines.add(Component.translatable("chestradar.craft.tooltip_more", hits.size() - shown)
					.withStyle(ChatFormatting.DARK_GRAY));
			}
		}
		context.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
	}

	private void drawStack(GuiGraphicsExtractor context, ItemStack stack, int x, int y) {
		try {
			context.item(stack, x, y);
		} catch (Exception e) {
			context.fill(x + 3, y + 5, x + Theme.ICON - 3, y + 11, Theme.BORDER);
			if (!tooltipFailureLogged) {
				tooltipFailureLogged = true;
				ChestRadar.LOGGER.warn("[chestradar] rendu d'icône impossible dans le planificateur", e);
			}
		}
	}

	private void center(GuiGraphicsExtractor context, Component text) {
		Component shown = text;
		context.text(font, shown, panelX + (panelWidth - font.width(shown)) / 2, listTop() + listHeight() / 2 - 4,
			Theme.TEXT_SECONDARY, false);
	}

	private int contentHeight() {
		if (planned != null) {
			return Math.max(0, planned.plan().needs().size() * (Theme.CONTROL_LARGE + Theme.SPACE_2) - Theme.SPACE_2);
		}
		return matches.size() * Theme.ROW;
	}

	private int maxScroll() {
		return Math.max(0, contentHeight() - listHeight());
	}

	private CraftPlan.Need needAt(double mouseX, double mouseY) {
		if (planned == null || mouseX < left() || mouseX >= left() + listWidth()
			|| mouseY < listTop() || mouseY >= listBottom()) {
			return null;
		}
		int local = (int) (mouseY - listTop() + scroll);
		int index = local / (Theme.CONTROL_LARGE + Theme.SPACE_2);
		List<CraftPlan.Need> needs = planned.plan().needs();
		return index < needs.size() && local - index * (Theme.CONTROL_LARGE + Theme.SPACE_2) < Theme.CONTROL_LARGE ? needs.get(index) : null;
	}

	private RecipeCatalog.Entry matchAt(double mouseX, double mouseY) {
		if (planned != null || mouseX < left() || mouseX >= left() + listWidth()
			|| mouseY < listTop() || mouseY >= listBottom()) {
			return null;
		}
		int index = (int) ((mouseY - listTop() + scroll) / Theme.ROW);
		return index < matches.size() ? matches.get(index) : null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
		int button = click.button();
		RecipeCatalog.Entry match = matchAt(click.x(), click.y());
		if (match != null && button == InputConstants.MOUSE_BUTTON_LEFT) {
			select(match, click.hasShiftDown() ? match.stackSize() : match.outputPerCraft());
			return true;
		}
		CraftPlan.Need need = needAt(click.x(), click.y());
		if (need != null) {
			if (button == InputConstants.MOUSE_BUTTON_LEFT) {
				goTo(need);
				return true;
			}
			if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
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
		if (mouseX >= panelX && mouseX <= panelX + panelWidth && mouseY >= listTop() && mouseY <= listBottom()) {
			scroll = Math.clamp(scroll - vertical * Theme.CONTROL_LARGE, 0, maxScroll());
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	@Override
	public void onClose() {
		if (minecraft == null) {
			return;
		}
		minecraft.setScreenAndShow(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
