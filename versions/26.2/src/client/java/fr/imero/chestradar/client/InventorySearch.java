package fr.imero.chestradar.client;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class InventorySearch {
	private static final Set<Screen> EQUIPPED = Collections.newSetFromMap(new WeakHashMap<>());

	private InventorySearch() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof AbstractContainerScreen<?> handled) || !EQUIPPED.add(screen)) {
				return;
			}
			ScreenKeyboardEvents.allowKeyPress(screen)
				.register((ignored, input) -> !locate(client, handled, input));
		});
	}

	private static boolean locate(Minecraft client, AbstractContainerScreen<?> screen, KeyEvent input) {
		if (ChestRadarKeybinds.SEARCH == null || !ChestRadarKeybinds.SEARCH.matches(input)) {
			return false;
		}
		if (screen.getFocused() instanceof EditBox) {
			return false;
		}
		ItemStack target = target(client, screen);
		if (target.isEmpty()) {
			return false;
		}
		screen.onClose();
		RadarActions.search(client, target);
		return true;
	}

	private static ItemStack target(Minecraft client, AbstractContainerScreen<?> screen) {
		ItemStack cursor = screen.getMenu().getCarried();
		if (!cursor.isEmpty()) {
			return cursor.copy();
		}
		Slot focused = screen.hoveredSlot;
		if (focused != null && focused.hasItem()) {
			return focused.getItem().copy();
		}
		return client == null || client.player == null
			? ItemStack.EMPTY : client.player.getMainHandItem().copy();
	}
}
