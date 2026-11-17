package fr.imero.chestradar.client.craft;

import fr.imero.chestradar.client.RadarState;
import fr.imero.chestradar.client.screen.CraftPlanScreen;
import fr.imero.chestradar.client.screen.FlatButton;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

public final class CraftPlanTab {
	private static final int MIN_WIDTH = 34;
	private static final int HEIGHT = 16;
	private static final int GAP = 3;
	private static final int TEXT_PADDING = 10;

	private CraftPlanTab() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof AbstractRecipeBookScreen<?> book)
				|| !RadarState.config().craftPlannerEnabled
				|| !RadarState.config().craftPlannerTab) {
				return;
			}
			if (Screens.getWidgets(screen).stream().anyMatch(widget -> widget instanceof Tab)) {
				return;
			}
			Screens.getWidgets(screen).add(new Tab(book));
		});
	}

	private static final class Tab extends FlatButton {
		private final AbstractRecipeBookScreen<?> owner;

		private boolean selected;

		private boolean styled;

		private Tab(AbstractRecipeBookScreen<?> owner) {
			super(0, 0, MIN_WIDTH, HEIGHT, Component.translatable("chestradar.craft.tab"), button -> {
				Minecraft client = Minecraft.getInstance();
				if (client == null) {
					return;
				}
				RecipeCatalog.Entry entry = RecipeBookSelection.entry(client, owner);
				if (entry == null) {
					client.setScreenAndShow(new CraftPlanScreen(owner,
						RecipeBookSelection.pendingResult(owner)));
					return;
				}
				int wanted = button.shiftHeld() ? entry.stackSize() : entry.outputPerCraft();
				client.setScreenAndShow(new CraftPlanScreen(owner, entry, wanted));
			});
			this.owner = owner;
			refresh();
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
			refresh();
			super.extractContents(context, mouseX, mouseY, delta);
		}

		private void refresh() {
			boolean nowSelected = RecipeBookSelection.has(owner);
			if (nowSelected != selected || !styled) {
				selected = nowSelected;
				styled = true;
				setMessage(Component.translatable(selected
					? "chestradar.craft.tab_route" : "chestradar.craft.tab"));
				setTooltip(Tooltip.create(Component.translatable(selected
					? "chestradar.craft.tab_tip_selected" : "chestradar.craft.tab_tip")));
				primary(selected);
				resize();
			}
			reposition();
		}

		private void resize() {
			Minecraft client = Minecraft.getInstance();
			int text = client == null ? 0 : client.font.width(getMessage());
			setWidth(Math.max(MIN_WIDTH, text + TEXT_PADDING));
		}

		private void reposition() {
			int right = Math.min(owner.leftPos + owner.imageWidth, owner.width - 1);
			setX(Math.max(1, right - getWidth()));
			setY(Math.min(owner.topPos + owner.imageHeight + GAP, owner.height - HEIGHT - 1));
		}
	}
}
