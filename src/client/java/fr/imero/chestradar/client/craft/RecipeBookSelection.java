package fr.imero.chestradar.client.craft;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.RecipeBookScreen;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.NetworkRecipeId;
import net.minecraft.recipe.RecipeDisplayEntry;
import net.minecraft.screen.AbstractCraftingScreenHandler;

public final class RecipeBookSelection {
	private RecipeBookSelection() {
	}

	public static boolean has(Screen screen) {
		return selectedId(screen) != null;
	}

	public static RecipeDisplayEntry display(Screen screen) {
		NetworkRecipeId id = selectedId(screen);
		RecipeResultCollection results = selectedResults(screen);
		if (id == null || results == null) {
			return null;
		}
		for (RecipeDisplayEntry entry : results.getAllRecipes()) {
			if (id.equals(entry.id())) {
				return entry;
			}
		}
		return null;
	}

	public static RecipeCatalog.Entry entry(MinecraftClient client, Screen screen) {
		return RecipeCatalog.from(client, display(screen));
	}

	public static String pendingResult(Screen screen) {
		if (!(screen instanceof RecipeBookScreen<?> book)
			|| !(book.getScreenHandler() instanceof AbstractCraftingScreenHandler handler)) {
			return null;
		}
		ItemStack result = handler.getOutputSlot().getStack();
		return result.isEmpty() ? null : result.getName().getString();
	}

	private static NetworkRecipeId selectedId(Screen screen) {
		RecipeBookWidget<?> widget = widget(screen);
		return widget == null ? null : widget.selectedRecipe;
	}

	private static RecipeResultCollection selectedResults(Screen screen) {
		RecipeBookWidget<?> widget = widget(screen);
		return widget == null ? null : widget.selectedRecipeResults;
	}

	private static RecipeBookWidget<?> widget(Screen screen) {
		return screen instanceof RecipeBookScreen<?> book ? book.recipeBook : null;
	}
}
