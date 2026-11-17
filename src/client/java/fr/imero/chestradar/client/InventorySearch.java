package fr.imero.chestradar.client;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class InventorySearch {
	private static final Set<Screen> EQUIPPED = Collections.newSetFromMap(new WeakHashMap<>());

	private InventorySearch() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof HandledScreen<?> handled) || !EQUIPPED.add(screen)) {
				return;
			}
			ScreenKeyboardEvents.allowKeyPress(screen)
				.register((ignored, input) -> !locate(client, handled, input));
		});
	}

	private static boolean locate(MinecraftClient client, HandledScreen<?> screen, KeyInput input) {
		if (ChestRadarKeybinds.SEARCH == null || !ChestRadarKeybinds.SEARCH.matchesKey(input)) {
			return false;
		}
		if (screen.getFocused() instanceof TextFieldWidget) {
			return false;
		}
		ItemStack target = target(client, screen);
		if (target.isEmpty()) {
			return false;
		}
		screen.close();
		RadarActions.search(client, target);
		return true;
	}

	private static ItemStack target(MinecraftClient client, HandledScreen<?> screen) {
		ItemStack cursor = screen.getScreenHandler().getCursorStack();
		if (!cursor.isEmpty()) {
			return cursor.copy();
		}
		Slot focused = screen.focusedSlot;
		if (focused != null && focused.hasStack()) {
			return focused.getStack().copy();
		}
		return client == null || client.player == null
			? ItemStack.EMPTY : client.player.getMainHandStack().copy();
	}
}
