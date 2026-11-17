package fr.imero.chestradar.client.craft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.inventory.AbstractCraftingMenu;

public final class RecipeBookSelection {
	private RecipeBookSelection() {
	}

	public static boolean has(Screen screen) {
		return selectedId(screen) != null;
	}

	public static RecipeDisplayEntry display(Screen screen) {
		RecipeDisplayId id = selectedId(screen);
		RecipeCollection results = selectedResults(screen);
		if (id == null || results == null) {
			return null;
		}
		for (RecipeDisplayEntry entry : results.getRecipes()) {
			if (id.equals(entry.id())) {
				return entry;
			}
		}
		return null;
	}

	public static RecipeCatalog.Entry entry(Minecraft client, Screen screen) {
		return RecipeCatalog.from(client, display(screen));
	}

	public static String pendingResult(Screen screen) {
		if (!(screen instanceof AbstractRecipeBookScreen<?> book)
			|| !(book.getMenu() instanceof AbstractCraftingMenu handler)) {
			return null;
		}
		ItemStack result = handler.getResultSlot().getItem();
		return result.isEmpty() ? null : result.getHoverName().getString();
	}

	private static RecipeDisplayId selectedId(Screen screen) {
		RecipeBookComponent<?> widget = widget(screen);
		return widget == null ? null : widget.lastRecipe;
	}

	private static RecipeCollection selectedResults(Screen screen) {
		RecipeBookComponent<?> widget = widget(screen);
		return widget == null ? null : widget.lastRecipeCollection;
	}

	private static RecipeBookComponent<?> widget(Screen screen) {
		return screen instanceof AbstractRecipeBookScreen<?> book ? book.recipeBookComponent : null;
	}
}
