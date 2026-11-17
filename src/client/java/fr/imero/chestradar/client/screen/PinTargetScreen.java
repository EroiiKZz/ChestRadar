package fr.imero.chestradar.client.screen;

import fr.imero.chestradar.client.RadarPins;
import fr.imero.chestradar.search.AggregatedItem;
import fr.imero.chestradar.search.Pin;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

public class PinTargetScreen extends Screen {
	private static final int PANEL_WIDTH = 220;
	private static final int PANEL_HEIGHT = 96;
	private static final int FIELD_HEIGHT = 18;
	private static final int BUTTON_HEIGHT = 18;
	private static final int MAX_TARGET = 100_000_000;

	private final Screen parent;
	private final AggregatedItem item;
	private TextFieldWidget field;

	private int panelX;
	private int panelY;

	public PinTargetScreen(Screen parent, AggregatedItem item) {
		super(Text.translatable("chestradar.pin.target_title"));
		this.parent = parent;
		this.item = item;
	}

	@Override
	protected void init() {
		panelX = (width - PANEL_WIDTH) / 2;
		panelY = (height - PANEL_HEIGHT) / 2;

		int x = panelX + Theme.PADDING;
		int w = PANEL_WIDTH - Theme.PADDING * 2;

		field = new TextFieldWidget(textRenderer, x, panelY + 40, w, FIELD_HEIGHT,
			Text.translatable("chestradar.pin.target_title"));
		field.setMaxLength(9);
		field.setTextPredicate(text -> text.chars().allMatch(Character::isDigit));
		Pin existing = RadarPins.find(item.identity().fingerprint());
		if (existing != null && existing.hasTarget()) {
			field.setText(Integer.toString(existing.target()));
		}
		addDrawableChild(field);
		setInitialFocus(field);

		int buttonWidth = (w - Theme.GUTTER) / 2;
		int buttonY = panelY + PANEL_HEIGHT - Theme.PADDING - BUTTON_HEIGHT;
		addDrawableChild(new FlatButton(x, buttonY, buttonWidth, BUTTON_HEIGHT,
			ScreenTexts.DONE, b -> apply()).primary());
		addDrawableChild(new FlatButton(x + buttonWidth + Theme.GUTTER, buttonY, buttonWidth,
			BUTTON_HEIGHT, ScreenTexts.CANCEL, b -> close()));
	}

	private void apply() {
		String text = field.getText();
		long parsed = text.isEmpty() ? 0L : Long.parseLong(text);
		RadarPins.setTarget(item.identity().fingerprint(), (int) Math.min(parsed, MAX_TARGET));
		close();
	}

	@Override
	public void close() {
		if (client != null) {
			client.setScreen(parent);
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		Panel.backdrop(context, width, height);
		Panel.frame(context, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT);

		Wordmark.draw(context, textRenderer, title.getString(), panelX + Theme.PADDING,
			panelY + Theme.PADDING, Theme.TEXT_PRIMARY, Wordmark.TITLE_TRACKING);
		context.drawText(textRenderer,
			textRenderer.trimToWidth(item.identity().displayName(), PANEL_WIDTH - Theme.PADDING * 2),
			panelX + Theme.PADDING, panelY + Theme.PADDING + 12, Theme.ACCENT, false);
		context.drawText(textRenderer, Text.translatable("chestradar.pin.target_hint"),
			panelX + Theme.PADDING, panelY + 62, Theme.TEXT_SECONDARY, false);

		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
