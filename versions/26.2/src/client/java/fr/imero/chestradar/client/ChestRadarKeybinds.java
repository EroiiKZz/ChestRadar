package fr.imero.chestradar.client;

import fr.imero.chestradar.ChestRadar;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class ChestRadarKeybinds {
	private static final KeyMapping.Category CATEGORY =
		KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ChestRadar.MOD_ID, "main"));

	public static KeyMapping SEARCH;
	public static KeyMapping OPEN_RADAR;
	public static KeyMapping OPEN_CONFIG;
	public static KeyMapping TOGGLE_HIGHLIGHT;
	public static KeyMapping OPEN_CRAFT_PLAN;
	public static KeyMapping CANCEL_ROUTE;

	private ChestRadarKeybinds() {
	}

	public static void register() {
		SEARCH = bind("search", GLFW.GLFW_KEY_R);
		OPEN_RADAR = bind("open_radar", GLFW.GLFW_KEY_G);
		OPEN_CONFIG = bind("open_config", InputConstants.UNKNOWN.getValue());
		TOGGLE_HIGHLIGHT = bind("toggle_highlight", InputConstants.UNKNOWN.getValue());
		OPEN_CRAFT_PLAN = bind("open_craft_plan", InputConstants.UNKNOWN.getValue());
		CANCEL_ROUTE = bind("cancel_route", InputConstants.UNKNOWN.getValue());
	}

	private static KeyMapping bind(String name, int code) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key." + ChestRadar.MOD_ID + "." + name, InputConstants.Type.KEYSYM, code, CATEGORY));
	}
}
