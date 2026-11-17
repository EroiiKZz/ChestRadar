package fr.imero.chestradar.client.screen;

import fr.imero.chestradar.client.RadarPins;
import fr.imero.chestradar.search.AggregatedItem;
import fr.imero.chestradar.search.Pin;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class PinTargetScreen extends Screen {
	private static final int PANEL_WIDTH = 220;
	private static final int MAX_TARGET = 100_000_000;

	private final Screen parent;
	private final AggregatedItem item;
	private FlatField field;

	private int panelX;
	private int panelY;

	public PinTargetScreen(Screen parent, AggregatedItem item) {
		super(Component.translatable("chestradar.pin.target_title"));
		this.parent = parent;
		this.item = item;
	}

	@Override
	protected void setInitialFocus() {
		if (field != null) {
			setInitialFocus(field);
		}
	}

	@Override
	protected void init() {
		panelX = (width - PANEL_WIDTH) / 2;
		panelY = (height - (40 + Theme.CONTROL * 2 + Theme.PADDING * 2)) / 2;

		int x = panelX + Theme.PADDING;
		int w = PANEL_WIDTH - Theme.PADDING * 2;

		field = new FlatField(font, x, panelY + 40, w, Theme.FIELD_HEIGHT,
			Component.translatable("chestradar.pin.target_title"));
		field.setMaxLength(9);
		field.setResponder(text -> {
			String digits = text.replaceAll("[^0-9]", "");
			if (!digits.equals(text)) {
				field.setValue(digits);
			}
		});
		Pin existing = RadarPins.find(item.identity().fingerprint());
		if (existing != null && existing.hasTarget()) {
			field.setValue(Integer.toString(existing.target()));
		}
		addRenderableWidget(field);
		setInitialFocus(field);

		int buttonWidth = (w - Theme.GUTTER) / 2;
		int buttonY = panelY + (40 + Theme.CONTROL * 2 + Theme.PADDING * 2) - Theme.PADDING - Theme.CONTROL;
		addRenderableWidget(new FlatButton(x, buttonY, buttonWidth, Theme.CONTROL,
			FlatButton.DONE, b -> apply()).primary());
		addRenderableWidget(new FlatButton(x + buttonWidth + Theme.GUTTER, buttonY, buttonWidth,
			Theme.CONTROL, FlatButton.CANCEL, b -> onClose()));
	}

	private void apply() {
		String text = field.getValue();
		long parsed = text.isEmpty() ? 0L : Long.parseLong(text);
		RadarPins.setTarget(item.identity().fingerprint(), (int) Math.min(parsed, MAX_TARGET));
		onClose();
	}

	@Override
	public void onClose() {
		if (minecraft != null) {
			minecraft.setScreenAndShow(parent);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		Panel.backdrop(context, width, height);
		Panel.frame(context, panelX, panelY, PANEL_WIDTH, (40 + Theme.CONTROL * 2 + Theme.PADDING * 2));

		Wordmark.draw(context, font, title.getString(), panelX + Theme.PADDING,
			panelY + Theme.PADDING, Theme.TEXT_PRIMARY, Wordmark.TITLE_TRACKING);
		context.text(font,
			Fit.text(font, item.identity().displayName(), PANEL_WIDTH - Theme.PADDING * 2),
			panelX + Theme.PADDING, panelY + Theme.PADDING + 12, Theme.ACCENT, false);
		context.text(font, Component.translatable("chestradar.pin.target_hint"),
			panelX + Theme.PADDING, panelY + 62, Theme.TEXT_SECONDARY, false);

		super.extractRenderState(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
