package fr.imero.chestradar.client;

import fr.imero.chestradar.ChestRadar;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public final class ChestRadarKeybinds {
	private static final KeyBinding.Category CATEGORY =
		KeyBinding.Category.create(Identifier.of(ChestRadar.MOD_ID, "main"));

	public static KeyBinding SEARCH;
	public static KeyBinding OPEN_RADAR;
	public static KeyBinding OPEN_CONFIG;
	public static KeyBinding TOGGLE_HIGHLIGHT;
	public static KeyBinding OPEN_CRAFT_PLAN;
	public static KeyBinding CANCEL_ROUTE;

	private ChestRadarKeybinds() {
	}

	public static void register() {
		SEARCH = bind("search", GLFW.GLFW_KEY_R);
		OPEN_RADAR = bind("open_radar", GLFW.GLFW_KEY_G);
		OPEN_CONFIG = bind("open_config", InputUtil.UNKNOWN_KEY.getCode());
		TOGGLE_HIGHLIGHT = bind("toggle_highlight", InputUtil.UNKNOWN_KEY.getCode());
		OPEN_CRAFT_PLAN = bind("open_craft_plan", InputUtil.UNKNOWN_KEY.getCode());
		CANCEL_ROUTE = bind("cancel_route", InputUtil.UNKNOWN_KEY.getCode());
	}

	private static KeyBinding bind(String name, int code) {
		return KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key." + ChestRadar.MOD_ID + "." + name, InputUtil.Type.KEYSYM, code, CATEGORY));
	}
}
