package fr.imero.chestradar.client.craft;

import fr.imero.chestradar.client.RadarState;
import fr.imero.chestradar.client.screen.CraftPlanScreen;
import fr.imero.chestradar.client.screen.FlatButton;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.RecipeBookScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;

public final class CraftPlanTab {
	private static final int MIN_WIDTH = 34;
	private static final int HEIGHT = 16;
	private static final int GAP = 3;
	private static final int TEXT_PADDING = 10;

	private CraftPlanTab() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof RecipeBookScreen<?> book)
				|| !RadarState.config().craftPlannerEnabled
				|| !RadarState.config().craftPlannerTab) {
				return;
			}
			if (Screens.getButtons(screen).stream().anyMatch(widget -> widget instanceof Tab)) {
				return;
			}
			Screens.getButtons(screen).add(new Tab(book));
		});
	}

	private static final class Tab extends FlatButton {
		private final RecipeBookScreen<?> owner;

		private boolean selected;

		private boolean styled;

		private Tab(RecipeBookScreen<?> owner) {
			super(0, 0, MIN_WIDTH, HEIGHT, Text.translatable("chestradar.craft.tab"), button -> {
				MinecraftClient client = MinecraftClient.getInstance();
				if (client == null) {
					return;
				}
				RecipeCatalog.Entry entry = RecipeBookSelection.entry(client, owner);
				if (entry == null) {
					client.setScreen(new CraftPlanScreen(owner,
						RecipeBookSelection.pendingResult(owner)));
					return;
				}
				int wanted = button.shiftHeld() ? entry.stackSize() : entry.outputPerCraft();
				client.setScreen(new CraftPlanScreen(owner, entry, wanted));
			});
			this.owner = owner;
			refresh();
		}

		@Override
		protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
			refresh();
			super.renderWidget(context, mouseX, mouseY, delta);
		}

		private void refresh() {
			boolean nowSelected = RecipeBookSelection.has(owner);
			if (nowSelected != selected || !styled) {
				selected = nowSelected;
				styled = true;
				setMessage(Text.translatable(selected
					? "chestradar.craft.tab_route" : "chestradar.craft.tab"));
				setTooltip(Tooltip.of(Text.translatable(selected
					? "chestradar.craft.tab_tip_selected" : "chestradar.craft.tab_tip")));
				primary(selected);
				resize();
			}
			reposition();
		}

		private void resize() {
			MinecraftClient client = MinecraftClient.getInstance();
			int text = client == null ? 0 : client.textRenderer.getWidth(getMessage());
			setWidth(Math.max(MIN_WIDTH, text + TEXT_PADDING));
		}

		private void reposition() {
			int right = Math.min(owner.x + owner.backgroundWidth, owner.width - 1);
			setX(Math.max(1, right - getWidth()));
			setY(Math.min(owner.y + owner.backgroundHeight + GAP, owner.height - HEIGHT - 1));
		}
	}
}
