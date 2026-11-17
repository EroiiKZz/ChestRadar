package fr.imero.chestradar.client.capture;

import fr.imero.chestradar.ChestRadar;
import fr.imero.chestradar.client.RadarState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;

public final class CaptureDebug {
	private static final int MAX_COMPONENT_CHARS = 400;

	private CaptureDebug() {
	}

	public static boolean enabled() {
		try {
			return RadarState.config().debugCaptureDump;
		} catch (Exception e) {
			return false;
		}
	}

	public static void skipped(String reason, Object detail) {
		if (!enabled()) {
			return;
		}
		ChestRadar.LOGGER.info("[chestradar][debug] ecran non capture ({}) : {}", reason, detail);
	}

	public static void dump(String title, ScreenHandler handler, int slots, String kind) {
		if (!enabled()) {
			return;
		}
		try {
			ChestRadar.LOGGER.info("[chestradar][debug] ---- capture ----");
			ChestRadar.LOGGER.info("[chestradar][debug] titre GUI   : \"{}\"", title);
			ChestRadar.LOGGER.info("[chestradar][debug] handler     : {}", handler.getClass().getName());
			ChestRadar.LOGGER.info("[chestradar][debug] genre / slots: {} / {}", kind, slots);
			for (int i = 0; i < slots && i < handler.slots.size(); i++) {
				dumpSlot(i, handler.slots.get(i).getStack());
			}
			ChestRadar.LOGGER.info("[chestradar][debug] ---- fin ----");
		} catch (Exception e) {
			ChestRadar.LOGGER.warn("[chestradar][debug] vidage de diagnostic interrompu", e);
		}
	}

	private static void dumpSlot(int index, ItemStack stack) {
		if (stack.isEmpty()) {
			ChestRadar.LOGGER.info("[chestradar][debug]   slot {} : (vide)", index);
			return;
		}
		ChestRadar.LOGGER.info("[chestradar][debug]   slot {} : {} x{}  nom=\"{}\"", index,
			Registries.ITEM.getId(stack.getItem()), stack.getCount(), stack.getName().getString());
		var lore = stack.get(DataComponentTypes.LORE);
		if (lore != null) {
			int line = 0;
			for (Text text : lore.lines()) {
				ChestRadar.LOGGER.info("[chestradar][debug]       lore[{}] = \"{}\"", line++, text.getString());
			}
		}
		String components = String.valueOf(stack.getComponentChanges());
		if (components.length() > MAX_COMPONENT_CHARS) {
			components = components.substring(0, MAX_COMPONENT_CHARS) + "… (tronque)";
		}
		ChestRadar.LOGGER.info("[chestradar][debug]       composants = {}", components);
	}
}
